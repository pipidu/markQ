package com.markq.core

object MarkColor {
    val PRESETS = listOf(
        "#F6C945",
        "#F97066",
        "#34C759",
        "#5B8DEF",
        "#C084FC",
        "#FB923C",
        "#22D3EE",
        "#F472B6",
    )

    fun parseArgb(hex: String?): Long? {
        val h = hex?.trim()?.removePrefix("#").orEmpty()
        if (h.length != 6) return null
        val rgb = h.toLongOrNull(16) ?: return null
        return 0xFF000000L or rgb
    }

    fun normalize(hex: String?): String? {
        val argb = parseArgb(hex) ?: return null
        return "#%06X".format((argb and 0xFFFFFFL).toInt())
    }
}

object ByteFormat {
    fun speed(bytesPerSecond: Long): String {
        val n = bytesPerSecond.coerceAtLeast(0L)
        return when {
            n >= 1_000_000L -> String.format(java.util.Locale.US, "%.1f MB/s", n / 1_000_000.0)
            n >= 1_000L -> String.format(java.util.Locale.US, "%.0f KB/s", n / 1_000.0)
            else -> "$n B/s"
        }
    }
}
