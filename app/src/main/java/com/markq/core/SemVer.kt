package com.markq.core

object SemVer {
    fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)
        for (i in 0 until 3) {
            val c = pa[i].compareTo(pb[i])
            if (c != 0) return c
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean =
        compare(strip(candidate), strip(current)) > 0

    private fun strip(raw: String): String = raw.trim().removePrefix("v").removePrefix("V")

    private fun parse(raw: String): IntArray {
        val parts = strip(raw).split('.', '-', '+')
        return intArrayOf(
            parts.getOrNull(0)?.toIntOrNull() ?: 0,
            parts.getOrNull(1)?.toIntOrNull() ?: 0,
            parts.getOrNull(2)?.toIntOrNull() ?: 0,
        )
    }
}
