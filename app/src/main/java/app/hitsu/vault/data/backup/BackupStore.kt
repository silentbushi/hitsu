package app.hitsu.vault.data.backup

import app.hitsu.vault.data.db.MediaDao
import app.hitsu.vault.data.media.VaultFiles
import app.hitsu.vault.domain.Clock
import app.hitsu.vault.domain.MediaType
import app.hitsu.vault.domain.VaultGateway
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import javax.crypto.AEADBadTagException

@Serializable
data class BackupItem(
    val id: String,
    val type: MediaType,
    val mime: String,
    val originalName: String? = null,
    val takenAt: Long? = null,
    val importedAt: Long = 0L,
)

@Serializable
data class BackupManifest(
    val version: Int = VERSION,
    val createdAt: Long,
    val items: List<BackupItem>,
) {
    companion object {
        const val VERSION = 1
    }
}

/** The passphrase does not open this file. Nothing was read, and nothing was written. */
class BackupPassphraseException : Exception("Wrong passphrase")

/**
 * Spec §7.10: the whole vault in one encrypted file, and back again.
 *
 * Two keys are in play and they never meet: the vault's own DEK, which unseals what is stored, and a
 * key derived from a passphrase the user types, which seals what leaves. In between, the plaintext
 * exists only as the bytes passing from one cipher to the other — a chunk at a time, never on disk
 * and never whole in memory. Restoring reverses it and hands each file to the ordinary import path,
 * so what comes back is sealed with *this* phone's DEK, which is what lets a backup open on a phone
 * that never knew the vault it came from.
 */
class BackupStore(
    private val dao: MediaDao,
    private val files: VaultFiles,
    private val vault: VaultGateway,
    private val clock: Clock,
    private val stagingDir: File,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** @return how many items were written. */
    suspend fun create(
        sink: OutputStream,
        passphrase: CharArray,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Int = withContext(ioDispatcher) {
        val cipher = vault.cipher ?: throw IOException("The vault is closed")
        val rows = dao.all()
        val manifest = BackupManifest(
            createdAt = clock.now(),
            items = rows.map {
                BackupItem(
                    id = it.id,
                    type = it.type,
                    mime = it.mime,
                    originalName = it.originalName,
                    takenAt = it.takenAt,
                    importedAt = it.importedAt,
                )
            },
        )

        val header = BackupCrypto.writeHeader(sink)
        val backup = BackupCrypto.cipherFor(passphrase, header)
        backup.encryptingSink(sink).use { sealed ->
            BackupArchive.Writer(sealed).use { archive ->
                archive.writeBytes(
                    BackupArchive.MANIFEST,
                    json.encodeToString(manifest).toByteArray(),
                )
                rows.forEachIndexed { index, row ->
                    onProgress(index, rows.size)
                    archive.write(BackupArchive.mediaEntry(row.id)) { entry ->
                        files.file(row.objectPath).inputStream().use { stored ->
                            cipher.decryptTo(stored, entry)
                        }
                    }
                }
            }
        }
        onProgress(rows.size, rows.size)
        rows.size
    }

    /**
     * Unpacks the backup one entry at a time, handing each to [importEntry] as a plaintext file that
     * is shredded as soon as it returns. One file exists in the clear at a time and no longer, which
     * is the same deal the importer already makes with anything shared into the app.
     */
    suspend fun restore(
        source: InputStream,
        passphrase: CharArray,
        onProgress: (done: Int, total: Int) -> Unit,
        importEntry: suspend (File, BackupItem) -> Unit,
    ) {
        val header = withContext(ioDispatcher) { BackupCrypto.readHeader(source) }
        val backup = withContext(ioDispatcher) { BackupCrypto.cipherFor(passphrase, header) }
        run {
            val plain = backup.decryptingSource(source)
            val reader = try {
                BackupArchive.Reader(plain)
            } catch (_: AEADBadTagException) {
                throw BackupPassphraseException()
            }

            val first = reader.next()
            if (first?.name != BackupArchive.MANIFEST) {
                throw BackupFormatException("The backup does not start with its manifest")
            }
            val manifest = json.decodeFromString<BackupManifest>(reader.readBytes().decodeToString())
            if (manifest.version > BackupManifest.VERSION) {
                throw BackupFormatException("The backup was made by a newer version of Hitsu")
            }
            val byId = manifest.items.associateBy { it.id }

            var done = 0
            while (true) {
                val entry = reader.next() ?: break
                val id = entry.name.removePrefix(MEDIA_PREFIX)
                val item = byId[id]
                if (entry.name == id || item == null) {
                    // Something this version does not know about: skip its body and carry on.
                    withContext(ioDispatcher) { reader.read(Discard) }
                    continue
                }
                val staging = withContext(ioDispatcher) {
                    stagingDir.mkdirs()
                    File.createTempFile("restore", null, stagingDir).also { file ->
                        file.outputStream().use { reader.read(it) }
                    }
                }
                try {
                    importEntry(staging, item)
                } finally {
                    withContext(ioDispatcher) { shred(staging) }
                }
                done++
                onProgress(done, manifest.items.size)
            }
        }
    }

    /** Where the body of an entry nobody asked for goes. */
    private object Discard : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    /** The unpacked copy is the only plaintext on disk, and it does not outlive its import. */
    private fun shred(file: File) {
        try {
            if (file.exists()) {
                RandomAccessFile(file, "rws").use { raf ->
                    val zeros = ByteArray(ZERO_CHUNK)
                    var written = 0L
                    while (written < raf.length()) {
                        val step = minOf(ZERO_CHUNK.toLong(), raf.length() - written).toInt()
                        raf.write(zeros, 0, step)
                        written += step
                    }
                }
            }
        } catch (_: IOException) {
            // Best effort; the file is deleted either way.
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val MEDIA_PREFIX = "media/"
        const val ZERO_CHUNK = 64 * 1024
    }
}
