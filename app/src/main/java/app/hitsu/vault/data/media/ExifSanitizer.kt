package app.hitsu.vault.data.media

import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Spec §5.3.3: location is dropped before the bytes are encrypted, not just left out of the index.
 * ExifInterface can only rewrite JPEG, PNG and WebP; for anything else (HEIC, for instance) the
 * bytes are stored untouched and the caller is told, because silently keeping GPS would be worse
 * than saying so.
 *
 * Those untouched originals keep their location inside the encrypted object, where only Hitsu can
 * read it. Export is therefore the boundary that has to strip it: nothing may leave the vault with
 * coordinates still attached.
 */
class ExifSanitizer(private val stagingDir: File) {

    class Result(val bytes: ByteArray, val locationRemoved: Boolean)

    fun stripLocation(bytes: ByteArray, mime: String): Result {
        if (!ExifInterface.isSupportedMimeType(mime)) return Result(bytes, locationRemoved = false)

        stagingDir.mkdirs()
        val staging = File.createTempFile("import", null, stagingDir)
        return try {
            staging.writeBytes(bytes)
            val exif = ExifInterface(staging)
            var hadLocation = false
            GPS_TAGS.forEach { tag ->
                if (exif.getAttribute(tag) != null) hadLocation = true
                exif.setAttribute(tag, null)
            }
            exif.saveAttributes()
            Result(staging.readBytes(), locationRemoved = hadLocation)
        } catch (_: IOException) {
            Result(bytes, locationRemoved = false)
        } finally {
            shred(staging)
        }
    }

    /** Overwrite before unlinking: the staging copy is the only plaintext that ever hits disk. */
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
        const val ZERO_CHUNK = 64 * 1024
        val GPS_TAGS = listOf(
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_DEST_BEARING,
            ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_DEST_DISTANCE,
            ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
            ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LONGITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_DIFFERENTIAL,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_MAP_DATUM,
            ExifInterface.TAG_GPS_MEASURE_MODE,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_SATELLITES,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_STATUS,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_VERSION_ID,
        )
    }
}
