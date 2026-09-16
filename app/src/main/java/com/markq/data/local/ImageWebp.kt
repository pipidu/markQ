package com.markq.data.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import com.markq.core.ImageNames
import java.io.File

object ImageWebp {
    fun compressFile(src: File, dest: File): Boolean {
        val bitmap = decodeOriented(src) ?: return false
        try {
            dest.parentFile?.mkdirs()
            dest.outputStream().use { out ->
                if (!bitmap.compress(webpFormat(), ImageNames.WEBP_QUALITY, out)) return false
            }
            return dest.exists() && dest.length() > 0L
        } catch (_: Exception) {
            dest.delete()
            return false
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun webpFormat(): Bitmap.CompressFormat {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
    }

    private fun decodeOriented(src: File): Bitmap? {
        val bitmap = BitmapFactory.decodeFile(src.absolutePath) ?: return null
        val rotation = runCatching {
            when (ExifInterface(src.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rotation == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap && !bitmap.isRecycled) bitmap.recycle()
        return rotated
    }
}
