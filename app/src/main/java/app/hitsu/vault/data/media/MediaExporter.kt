package app.hitsu.vault.data.media

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import app.hitsu.vault.data.db.MediaEntity
import app.hitsu.vault.domain.MediaType
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Spec §7.8: puts a copy back in the phone's gallery. Export is the boundary where location has to
 * go: photos in a format ExifInterface can rewrite were already cleaned on the way in (§5.3), but
 * HEIC photos and videos were stored untouched, so their coordinates are still inside.
 *
 * Neither format can simply have its metadata rewritten, so each is rebuilt instead:
 *
 * - A HEIC is decoded and written out as JPEG, turned upright first, because its orientation lives
 *   in the metadata that is being left behind. The format changes and the image is re-encoded once
 *   at high quality; that is the price of it leaving without coordinates.
 * - An MP4 is remuxed: the tracks are copied across untouched, frame for frame, into a new
 *   container that was never told where it was recorded. No re-encoding, so no quality is lost.
 * - Other containers (the WebM and Matroska that the downloader produces) are written as they are:
 *   they come from the network, not from a camera, and carry no coordinates to remove.
 */
class MediaExporter(
    private val resolver: ContentResolver,
    private val stagingDir: File,
    private val sanitizer: ExifSanitizer,
) {

    /** A photo is small enough to rebuild in memory. @return true when the copy reached the gallery. */
    fun exportPhoto(entity: MediaEntity, bytes: ByteArray): Boolean {
        val plan = planFor(entity)
        return write(plan) { target ->
            if (plan.transcodeToJpeg) {
                writeJpeg(target, bytes)
            } else {
                writeBytes(target, sanitizer.stripLocation(bytes, entity.mime).bytes)
            }
        }
    }

    /**
     * A video is written out through disk rather than memory, since one can be gigabytes. The
     * plaintext copy is made by [decryptInto], used, and shredded before this returns.
     */
    fun exportVideo(entity: MediaEntity, decryptInto: (File) -> Unit): Boolean {
        val plan = planFor(entity)
        stagingDir.mkdirs()
        val staging = File.createTempFile("export", null, stagingDir)
        return try {
            decryptInto(staging)
            write(plan) { target ->
                if (plan.remux) remuxInto(target, staging) else copyInto(target, staging)
            }
        } finally {
            shred(staging)
        }
    }

    private fun write(plan: Plan, body: (Uri) -> Unit): Boolean {
        val target = insertPending(plan) ?: return false
        return try {
            body(target)
            publish(target)
            true
        } catch (_: IOException) {
            resolver.delete(target, null, null)
            false
        } catch (_: IllegalArgumentException) {
            // A container MediaMuxer will not take; the row is dropped rather than left half written.
            resolver.delete(target, null, null)
            false
        }
    }

    private class Plan(
        val displayName: String,
        val mime: String,
        val isVideo: Boolean,
        val transcodeToJpeg: Boolean,
        val remux: Boolean,
        val takenAt: Long?,
    )

    private fun planFor(entity: MediaEntity): Plan {
        val mime = exportMime(entity.mime)
        return Plan(
            displayName = exportName(entity.originalName, entity.id, mime),
            mime = mime,
            isVideo = entity.type == MediaType.Video,
            transcodeToJpeg = mime != entity.mime,
            remux = entity.mime in REMUXABLE_MIMES,
            takenAt = entity.takenAt,
        )
    }

    private fun insertPending(plan: Plan): Uri? {
        val collection = if (plan.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val directory = if (plan.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, plan.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, plan.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, directory + File.separator + ALBUM)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            plan.takenAt?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
        }
        return runCatching { resolver.insert(collection, values) }.getOrNull()
    }

    /** Until this runs the file is invisible to other apps, so a failed export leaves no husk. */
    private fun publish(target: Uri) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        resolver.update(target, values, null, null)
    }

    private fun writeBytes(target: Uri, bytes: ByteArray) {
        resolver.openOutputStream(target)?.use { it.write(bytes) } ?: throw IOException("no stream")
    }

    private fun writeJpeg(target: Uri, bytes: ByteArray) {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IOException("cannot decode")
        val upright = turnUpright(decoded, bytes)
        try {
            resolver.openOutputStream(target)?.use { out ->
                if (!upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    throw IOException("cannot encode")
                }
            } ?: throw IOException("no stream")
        } finally {
            if (upright !== decoded) upright.recycle()
            decoded.recycle()
        }
    }

    private fun turnUpright(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun copyInto(target: Uri, source: File) {
        val out = resolver.openOutputStream(target) ?: throw IOException("no stream")
        out.use { sink -> source.inputStream().use { it.copyTo(sink) } }
    }

    private fun remuxInto(target: Uri, source: File) {
        val extractor = MediaExtractor()
        extractor.setDataSource(source.absolutePath)
        val descriptor = resolver.openFileDescriptor(target, "w") ?: throw IOException("no descriptor")
        val muxer = descriptor.use {
            MediaMuxer(it.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }
        try {
            val trackMap = HashMap<Int, Int>()
            var maxInputSize = MIN_BUFFER_BYTES
            for (track in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(track)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    maxInputSize = maxOf(maxInputSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
                trackMap[track] = muxer.addTrack(format)
                extractor.selectTrack(track)
            }
            if (trackMap.isEmpty()) throw IOException("no tracks")

            // Deliberately no muxer.setLocation: the copy is the point where the coordinates stop.
            muxer.start()
            val buffer = ByteBuffer.allocate(maxInputSize)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val track = trackMap[extractor.sampleTrackIndex]
                if (track != null) {
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = extractor.sampleTime
                    // The extractor and the muxer name their flags differently; only one crosses.
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    muxer.writeSampleData(track, buffer, info)
                }
                extractor.advance()
            }
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            extractor.release()
        }
    }

    /** The staging copy is the only plaintext that touches disk; it does not outlive the export. */
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
        const val ALBUM = "Hitsu"
        const val JPEG_QUALITY = 95
        const val ZERO_CHUNK = 64 * 1024
        const val MIN_BUFFER_BYTES = 1 shl 20
        val REMUXABLE_MIMES = setOf("video/mp4", "video/quicktime")
    }
}

private const val NAME_PREFIX = "hitsu-"
private val HEIF_MIMES = setOf("image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence")

/** A HEIC cannot be rewritten without its location, so what leaves is a JPEG. */
fun exportMime(storedMime: String): String = if (storedMime in HEIF_MIMES) "image/jpeg" else storedMime

/**
 * The name the copy takes in the gallery. It keeps the name it had when it was imported, with the
 * extension of what is actually being written, and falls back to the item's own id for anything that
 * arrived without a name, such as a download.
 */
fun exportName(originalName: String?, id: String, mime: String): String {
    val base = originalName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
        ?: (NAME_PREFIX + id.take(ID_CHARS))
    return base + "." + extensionFor(mime)
}

private fun extensionFor(mime: String): String = when (mime) {
    "image/jpeg" -> "jpg"
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    "video/mp4" -> "mp4"
    "video/webm" -> "webm"
    "video/x-matroska" -> "mkv"
    "video/quicktime" -> "mov"
    else -> mime.substringAfterLast('/')
}

private const val ID_CHARS = 8
