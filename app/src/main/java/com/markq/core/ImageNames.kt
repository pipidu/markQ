package com.markq.core

object ImageNames {
    const val WEBP_MIME = "image/webp"
    const val WEBP_QUALITY = 60

    fun webpFileName(original: String): String {
        val trimmed = original.trim().ifBlank { "image" }
        val base = trimmed.substringBeforeLast('.', missingDelimiterValue = trimmed).ifBlank { "image" }
        return "$base.webp"
    }
}
