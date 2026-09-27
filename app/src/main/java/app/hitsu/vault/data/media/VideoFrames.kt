package app.hitsu.vault.data.media

import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor

class VideoDetails(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val takenAt: Long?,
    val thumbnail: ByteArray,
)

/**
 * Reads what the index needs from a video and grabs its poster frame (spec §5.3.5: the frame at one
 * second, or the closest keyframe). Works off a file descriptor because a video is far too large to
 * hold in memory the way photos are.
 */
class VideoFrames(private val thumbnails: ThumbnailFactory) {

    fun read(descriptor: ParcelFileDescriptor): VideoDetails? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(descriptor.fileDescriptor)
            val rotation = retriever.extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val rawWidth = retriever.extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val rawHeight = retriever.extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (rawWidth <= 0 || rawHeight <= 0) return null

            val frame = retriever.getFrameAtTime(
                POSTER_FRAME_US,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            ) ?: retriever.frameAtTime ?: return null

            val thumbnail = thumbnails.encodeThumbnail(frame)
            frame.recycle()

            val upright = rotation == 90 || rotation == 270
            VideoDetails(
                width = if (upright) rawHeight else rawWidth,
                height = if (upright) rawWidth else rawHeight,
                durationMs = retriever.extract(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                takenAt = null,
                thumbnail = thumbnail,
            )
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun MediaMetadataRetriever.extract(key: Int): String? = extractMetadata(key)

    private companion object {
        const val POSTER_FRAME_US = 1_000_000L
    }
}
