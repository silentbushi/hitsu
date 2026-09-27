package app.hitsu.vault.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

class DecodedImage(val width: Int, val height: Int, val thumbnail: ByteArray)

/** Spec §5.3.5: thumbnails are downscaled to 480 px on the long side before being encrypted. */
class ThumbnailFactory {

    fun decode(bytes: ByteArray, orientation: Int): DecodedImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val rotated = decoded.applying(rotationFor(orientation))
        val scaled = rotated.scaledToLongSide()

        val thumbnail = compress(scaled)

        val swapsAxes = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
        val width = if (swapsAxes) bounds.outHeight else bounds.outWidth
        val height = if (swapsAxes) bounds.outWidth else bounds.outHeight

        if (scaled !== rotated) scaled.recycle()
        if (rotated !== decoded) rotated.recycle()
        decoded.recycle()

        return DecodedImage(width, height, thumbnail)
    }

    /** A video frame arrives already decoded, so it only needs the same scaling and encoding. */
    fun encodeThumbnail(frame: Bitmap): ByteArray {
        val scaled = frame.scaledToLongSide()
        val bytes = compress(scaled)
        if (scaled !== frame) scaled.recycle()
        return bytes
    }

    private fun compress(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        out.toByteArray()
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= LONG_SIDE) sample *= 2
        return sample
    }

    private fun rotationFor(orientation: Int): Matrix? = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { postRotate(90f) }
        ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { postRotate(180f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { postRotate(270f) }
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { postScale(1f, -1f) }
        ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply { postRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply { postRotate(270f); postScale(-1f, 1f) }
        else -> null
    }

    private fun Bitmap.applying(matrix: Matrix?): Bitmap =
        if (matrix == null) this else Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)

    private fun Bitmap.scaledToLongSide(): Bitmap {
        val longSide = maxOf(width, height)
        if (longSide <= LONG_SIDE) return this
        val factor = LONG_SIDE.toFloat() / longSide
        return scale((width * factor).toInt(), (height * factor).toInt())
    }

    private companion object {
        const val LONG_SIDE = 480
        const val QUALITY = 85
    }
}
