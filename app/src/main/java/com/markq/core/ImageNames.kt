package com.markq.core

object ImageNames {
    const val WEBP_MIME = "image/webp"
    const val WEBP_QUALITY = 60
    /** Longest edge, in pixels, used when downsampling before WebP compression. */
    const val MAX_EDGE = 2048

    /**
     * Power-of-two [android.graphics.BitmapFactory.Options.inSampleSize].
     * Already-small images stay at 1 so orientation and bytes are unchanged.
     */
    fun inSampleSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Int {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / sample > maxEdge) {
            sample *= 2
        }
        return sample
    }

    fun webpFileName(original: String): String {
        val trimmed = original.trim().ifBlank { "image" }
        val base = trimmed.substringBeforeLast('.', missingDelimiterValue = trimmed).ifBlank { "image" }
        return "$base.webp"
    }
}
