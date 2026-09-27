package app.hitsu.vault.data.media

import android.database.sqlite.SQLiteConstraintException
import androidx.exifinterface.media.ExifInterface
import app.hitsu.vault.crypto.VaultCipher
import app.hitsu.vault.data.db.MediaDao
import app.hitsu.vault.data.db.MediaEntity
import app.hitsu.vault.domain.Clock
import app.hitsu.vault.domain.MediaType
import java.io.ByteArrayInputStream
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class UnsupportedMediaException(message: String) : Exception(message)

sealed interface ImportOutcome {
    data class Imported(val entity: MediaEntity) : ImportOutcome

    /** The very same bytes are already in the vault, so nothing was written. */
    data class Duplicate(val existingId: String) : ImportOutcome
}

class MediaImporter(
    private val files: VaultFiles,
    private val dao: MediaDao,
    private val thumbnails: ThumbnailFactory,
    private val videos: VideoFrames,
    private val exif: ExifSanitizer,
    private val clock: Clock,
) {

    suspend fun import(source: ImportSource, cipher: VaultCipher): ImportOutcome = when {
        source.isVideo -> importVideo(source, cipher)
        source.mime.startsWith("image/") -> importPhoto(source, cipher)
        else -> throw UnsupportedMediaException(source.mime)
    }

    /**
     * Reads the photo, drops its location, encrypts both it and its thumbnail, and only then writes
     * the index row: a crash mid-import leaves orphan files, never a row pointing at bytes that are
     * not there.
     */
    private suspend fun importPhoto(source: ImportSource, cipher: VaultCipher): ImportOutcome {
        val raw = source.open().use { it.readBytes() }
        val sanitized = exif.stripLocation(raw, source.mime).bytes
        if (raw !== sanitized) raw.fill(0)

        /*
         * The fingerprint identifies the bytes that end up stored, not the ones that came in. Only
         * those can be recomputed later from the vault itself, which is what lets rows written
         * before deduplication existed be filled in. As a side effect two copies of one photo that
         * differ only in the location tags we strip are recognised as the same file.
         */
        val fingerprint = cipher.fingerprint(sanitized)
        dao.findByFingerprint(fingerprint)?.let { existingId ->
            sanitized.fill(0)
            return ImportOutcome.Duplicate(existingId)
        }

        val metadata = ExifInterface(ByteArrayInputStream(sanitized))
        val orientation = metadata.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val decoded = thumbnails.decode(sanitized, orientation)
            ?: throw UnsupportedMediaException(source.mime)

        val id = UUID.randomUUID().toString()
        val paths = prepare(id)
        try {
            files.file(paths.objectPath).writeBytes(cipher.encrypt(sanitized))
            files.file(paths.thumbPath).writeBytes(cipher.encrypt(decoded.thumbnail))
        } catch (e: IOException) {
            discard(paths)
            throw e
        } finally {
            sanitized.fill(0)
            decoded.thumbnail.fill(0)
        }

        return insert(
            MediaEntity(
                id = id,
                type = MediaType.Photo,
                mime = source.mime,
                objectPath = paths.objectPath,
                thumbPath = paths.thumbPath,
                width = decoded.width,
                height = decoded.height,
                durationMs = null,
                sizeBytes = source.sizeBytes,
                takenAt = metadata.takenAtMillis(),
                importedAt = clock.now(),
                originalName = source.displayName,
                contentFingerprint = fingerprint,
            ),
            paths,
        )
    }

    /**
     * A video never fits in memory, so it is streamed straight into the vault and its fingerprint
     * falls out of the same pass. That means the duplicate check can only happen once the bytes are
     * already written, and a copy is deleted again right after.
     */
    private suspend fun importVideo(source: ImportSource, cipher: VaultCipher): ImportOutcome {
        val openDescriptor = source.openDescriptor
            ?: throw UnsupportedMediaException(source.mime)
        val details = openDescriptor()?.use { videos.read(it) }
            ?: throw UnsupportedMediaException(source.mime)

        val id = UUID.randomUUID().toString()
        val paths = prepare(id)
        val fingerprint = try {
            val stored = source.open().use { input ->
                files.file(paths.objectPath).outputStream().use { output ->
                    cipher.encryptTo(input, output)
                }
            }
            files.file(paths.thumbPath).writeBytes(cipher.encrypt(details.thumbnail))
            stored
        } catch (e: IOException) {
            discard(paths)
            throw e
        } finally {
            details.thumbnail.fill(0)
        }

        dao.findByFingerprint(fingerprint)?.let { existingId ->
            discard(paths)
            return ImportOutcome.Duplicate(existingId)
        }

        return insert(
            MediaEntity(
                id = id,
                type = MediaType.Video,
                mime = source.mime,
                objectPath = paths.objectPath,
                thumbPath = paths.thumbPath,
                width = details.width,
                height = details.height,
                durationMs = details.durationMs,
                sizeBytes = source.sizeBytes,
                takenAt = details.takenAt,
                importedAt = clock.now(),
                originalName = source.displayName,
                contentFingerprint = fingerprint,
            ),
            paths,
        )
    }

    private class VaultPaths(val objectPath: String, val thumbPath: String)

    private fun prepare(id: String): VaultPaths {
        files.prepareDirectories()
        return VaultPaths(files.objectPath(id), files.thumbPath(id))
    }

    private fun discard(paths: VaultPaths) {
        files.file(paths.objectPath).delete()
        files.file(paths.thumbPath).delete()
    }

    private suspend fun insert(entity: MediaEntity, paths: VaultPaths): ImportOutcome = try {
        dao.insert(entity)
        ImportOutcome.Imported(entity)
    } catch (_: SQLiteConstraintException) {
        // Lost a race against another import of the same bytes: keep the row that won.
        discard(paths)
        ImportOutcome.Duplicate(dao.findByFingerprint(entity.contentFingerprint.orEmpty()).orEmpty())
    }

    private fun ExifInterface.takenAtMillis(): Long? {
        val stamp = getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        return try {
            SimpleDateFormat(EXIF_DATE_PATTERN, Locale.US).parse(stamp)?.time
        } catch (_: ParseException) {
            null
        }
    }

    private companion object {
        const val EXIF_DATE_PATTERN = "yyyy:MM:dd HH:mm:ss"
    }
}
