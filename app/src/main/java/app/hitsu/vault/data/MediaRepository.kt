package app.hitsu.vault.data

import android.net.Uri
import app.hitsu.vault.data.db.MediaDao
import app.hitsu.vault.data.db.MediaEntity
import app.hitsu.vault.data.db.toItem
import android.database.sqlite.SQLiteConstraintException
import app.hitsu.vault.data.media.ImportOutcome
import app.hitsu.vault.data.media.ImportSource
import app.hitsu.vault.data.media.ImportSources
import app.hitsu.vault.data.media.MediaExporter
import app.hitsu.vault.data.media.MediaImporter
import app.hitsu.vault.data.media.PlaybackCache
import app.hitsu.vault.data.media.SharedIntake
import app.hitsu.vault.data.media.asImportSource
import app.hitsu.vault.data.media.VaultFiles
import app.hitsu.vault.domain.MediaFilter
import app.hitsu.vault.domain.MediaItem
import app.hitsu.vault.domain.MediaType
import app.hitsu.vault.domain.VaultGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.IOException

data class ImportStatus(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val currentName: String? = null,
    val failedName: String? = null,
    val duplicates: Int = 0,
)

data class ExportStatus(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val failed: Int = 0,
)

class MediaRepository(
    private val dao: MediaDao,
    private val importer: MediaImporter,
    private val exporter: MediaExporter,
    private val files: VaultFiles,
    private val sources: ImportSources,
    private val playback: PlaybackCache,
    private val intake: SharedIntake,
    private val vault: VaultGateway,
    private val appScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val _importStatus = MutableStateFlow(ImportStatus())
    val importStatus: StateFlow<ImportStatus> = _importStatus.asStateFlow()

    private val _exportStatus = MutableStateFlow(ExportStatus())
    val exportStatus: StateFlow<ExportStatus> = _exportStatus.asStateFlow()

    private var importJob: Job? = null
    private var exportJob: Job? = null

    fun media(filter: MediaFilter): Flow<List<MediaItem>> {
        val rows = when (filter) {
            MediaFilter.All -> dao.observeAll()
            MediaFilter.Photos -> dao.observeByType(MediaType.Photo)
            MediaFilter.Videos -> dao.observeByType(MediaType.Video)
        }
        return rows.map { entities -> entities.map { it.toItem() } }
    }

    /**
     * Runs on the application scope so rotating or navigating away does not cancel an import. The
     * job is returned so callers can await it; the UI only watches [importStatus].
     */
    fun import(uris: List<Uri>): Job? {
        if (uris.isEmpty() || importJob?.isActive == true) return null
        return startImport(
            open = { withContext(ioDispatcher) { uris.map(sources::from) } },
        )
    }

    /** Spec §7.4: what other apps shared was copied aside before the PIN; now it can be sealed. */
    fun importShared(): Job? {
        if (importJob?.isActive == true) return null
        val staged = intake.take()
        if (staged.isEmpty()) return null
        return startImport(
            open = { staged.map { it.asImportSource() } },
            cleanUp = { intake.discard(staged) },
        )
    }

    private fun startImport(
        open: suspend () -> List<ImportSource>,
        cleanUp: () -> Unit = {},
    ): Job {
        val job = appScope.launch {
            val items = open()
            _importStatus.value = ImportStatus(running = true, total = items.size)
            var done = 0
            var duplicates = 0
            var failed: String? = null

            for (source in items) {
                _importStatus.value = ImportStatus(
                    running = true,
                    done = done,
                    total = items.size,
                    currentName = source.displayName,
                )
                val cipher = vault.cipher
                if (cipher == null) {
                    failed = source.displayName
                    break
                }
                try {
                    when (withContext(ioDispatcher) { importer.import(source, cipher) }) {
                        is ImportOutcome.Imported -> done++
                        is ImportOutcome.Duplicate -> duplicates++
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    failed = source.displayName
                }
            }

            _importStatus.value = ImportStatus(
                done = done,
                total = items.size,
                failedName = failed,
                duplicates = duplicates,
            )
            cleanUp()
        }
        importJob = job
        return job
    }

    val sharing: StateFlow<Boolean> get() = intake.staging

    fun stageShared(uris: List<Uri>) = intake.stage(uris)

    fun hasShared(): Boolean = intake.staged.value.isNotEmpty()

    fun wipeShared() = intake.wipe()

    fun canImport(): Boolean = vault.cipher != null

    /**
     * What the downloader produced: a post can hold several videos, and every one of them takes
     * the same pipeline as anything else before the copies are removed.
     */
    fun importDownloaded(files: List<File>): Job? {
        if (files.isEmpty() || importJob?.isActive == true) return null
        return startImport(
            open = { files.map { it.asImportSource() } },
            cleanUp = { files.forEach { it.delete() } },
        )
    }

    /** Keeps finished downloads until the vault opens, instead of losing them or leaving them bare. */
    fun holdForUnlock(files: List<File>) {
        files.forEach { intake.hold(it, it.name, mimeOf(it)) }
    }

    private fun File.asImportSource() = ImportSource(
        displayName = name,
        mime = mimeOf(this),
        sizeBytes = length(),
        open = { inputStream() },
        openDescriptor = { ParcelFileDescriptor.open(this, ParcelFileDescriptor.MODE_READ_ONLY) },
    )

    private fun mimeOf(file: File): String = when (file.extension.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    /**
     * Spec §7.8: writes a copy of each item back into the phone's gallery, one at a time so the
     * screen can say where it is. What leaves carries no location; [MediaExporter] explains at what
     * cost per format.
     */
    fun export(ids: List<String>): Job? {
        if (ids.isEmpty() || exportJob?.isActive == true) return null
        val job = appScope.launch {
            _exportStatus.value = ExportStatus(running = true, total = ids.size)
            var done = 0
            var failed = 0
            for (id in ids) {
                if (withContext(ioDispatcher) { exportOne(id) }) done++ else failed++
                _exportStatus.value =
                    ExportStatus(running = true, done = done, total = ids.size, failed = failed)
            }
            _exportStatus.value = ExportStatus(done = done, total = ids.size, failed = failed)
        }
        exportJob = job
        return job
    }

    private suspend fun exportOne(id: String): Boolean {
        val entity = dao.byId(id) ?: return false
        val cipher = vault.cipher ?: return false
        return try {
            when (entity.type) {
                MediaType.Photo -> {
                    val bytes = cipher.decrypt(files.file(entity.objectPath).readBytes())
                    try {
                        exporter.exportPhoto(entity, bytes)
                    } finally {
                        bytes.fill(0)
                    }
                }
                MediaType.Video -> exporter.exportVideo(entity) { staging ->
                    files.file(entity.objectPath).inputStream().use { source ->
                        staging.outputStream().use { sink -> cipher.decryptTo(source, sink) }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    fun clearExportStatus() {
        _exportStatus.value = ExportStatus()
    }

    /** Spec §7.7: object, thumbnail and row. Irreversible, and only ever after a confirmation. */
    fun delete(ids: List<String>): Job? {
        if (ids.isEmpty()) return null
        return appScope.launch {
            withContext(ioDispatcher) {
                ids.forEach { id ->
                    val entity = dao.byId(id) ?: return@forEach
                    files.file(entity.objectPath).delete()
                    files.file(entity.thumbPath).delete()
                    dao.delete(id)
                }
            }
        }
    }

    /** Bytes the vault occupies: encrypted objects plus their thumbnails. */
    suspend fun vaultSizeBytes(): Long = withContext(ioDispatcher) {
        listOf("objects", "thumbs").sumOf { dir ->
            files.file(dir).listFiles()?.sumOf { it.length() } ?: 0L
        }
    }

    /**
     * Rows imported before fingerprinting existed carry none, so they would never match a future
     * import. This fills them in from the stored bytes; running it twice is harmless.
     */
    fun ensureFingerprints(): Job = appScope.launch {
        val cipher = vault.cipher ?: return@launch
        withContext(ioDispatcher) {
            dao.withoutFingerprint().forEach { entity ->
                try {
                    val bytes = cipher.decrypt(files.file(entity.objectPath).readBytes())
                    try {
                        dao.setFingerprint(entity.id, cipher.fingerprint(bytes))
                    } catch (_: SQLiteConstraintException) {
                        // Another row already holds these bytes: this is a copy imported before
                        // deduplication existed. Leave it untouched; only the user deletes media.
                    }
                    bytes.fill(0)
                } catch (_: IOException) {
                    // A missing object cannot be fingerprinted; leave the row as it is.
                }
            }
        }
    }

    suspend fun thumbnail(id: String): ByteArray? = decrypted(id) { it.thumbPath }

    /**
     * Decrypts a video into the private playback cache so Media3 can read it, reporting progress.
     * Throws [app.hitsu.vault.data.media.InsufficientSpaceException] when there is no room.
     */
    suspend fun preparePlayback(id: String, onProgress: (Float) -> Unit): java.io.File? {
        val entity = withContext(ioDispatcher) { dao.byId(id) } ?: return null
        val cipher = vault.cipher ?: return null
        return playback.prepare(entity.objectPath, entity.sizeBytes, cipher, onProgress)
    }

    fun playbackSpace(sizeBytes: Long): Pair<Long, Long> =
        playback.requiredFreeBytes(sizeBytes) to playback.freeBytes()

    fun wipePlayback() = playback.wipe()

    /** The full photo, decrypted in memory for the viewer; it never lands on disk in the clear. */
    suspend fun original(id: String): ByteArray? = decrypted(id) { it.objectPath }

    suspend fun item(id: String): MediaItem? = withContext(ioDispatcher) { dao.byId(id)?.toItem() }

    private suspend fun decrypted(id: String, path: (MediaEntity) -> String): ByteArray? =
        withContext(ioDispatcher) {
            val entity = dao.byId(id) ?: return@withContext null
            val cipher = vault.cipher ?: return@withContext null
            try {
                cipher.decrypt(files.file(path(entity)).readBytes())
            } catch (_: IOException) {
                null
            }
        }
}
