package com.vishnu.kohliprotocol.data.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max

/**
 * Decodes an image, applies its EXIF orientation (camera photos are often stored sideways with
 * a rotation tag), scales it so the longest edge is at most `maxEdge`, and re-encodes as JPEG.
 * Pure in-memory work; callers decide where the bytes go.
 */
internal object ImageCompressor {

    /**
     * @param open opens a fresh stream of the source each call (it is read up to three times).
     * @param flattenAlpha if true, transparent images are drawn onto white; if false they are
     *   rejected (null) so the caller can keep the original instead of losing transparency.
     * @return JPEG bytes, or null if the image can't be decoded (or has alpha and [flattenAlpha]
     *   is false).
     */
    fun compress(open: () -> InputStream?, maxEdge: Int, quality: Int, flattenAlpha: Boolean): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = open() ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null

        // Power-of-two subsampling keeps memory low for huge photos; exact scaling happens below.
        var sample = 1
        while (longest / (sample * 2) >= maxEdge) sample *= 2
        val decodeStream = open() ?: return null
        var bitmap = decodeStream.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        if (bitmap.hasAlpha()) {
            if (!flattenAlpha) {
                bitmap.recycle()
                return null
            }
            bitmap = flattenOnWhite(bitmap)
        }

        val orientation = runCatching {
            open()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val output = orientAndScale(bitmap, orientation, maxEdge)
        return try {
            ByteArrayOutputStream().use { out ->
                output.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
        } finally {
            output.recycle()
        }
    }

    private fun orientAndScale(source: Bitmap, orientation: Int, maxEdge: Int): Bitmap {
        val matrix = Matrix()
        val scale = maxEdge.toFloat() / max(source.width, source.height)
        if (scale < 1f) matrix.postScale(scale, scale)
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        }
        if (matrix.isIdentity) return source
        val result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (result !== source) source.recycle()
        return result
    }

    private fun flattenOnWhite(source: Bitmap): Bitmap {
        val opaque = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.RGB_565)
        Canvas(opaque).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, 0f, 0f, null)
        }
        source.recycle()
        return opaque
    }
}
