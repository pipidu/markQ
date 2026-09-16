package com.markq.core

object MarkTags {
    private const val SEP = '\u001F'

    fun normalize(raw: Iterable<String>): List<String> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<String>()
        for (item in raw) {
            val t = item.trim().replace(Regex("\\s+"), " ")
            if (t.isEmpty()) continue
            if (!seen.add(t.lowercase())) continue
            out.add(t)
        }
        return out
    }

    fun encode(tags: List<String>): String = normalize(tags).joinToString(SEP.toString())

    fun decode(stored: String?): List<String> {
        if (stored.isNullOrEmpty()) return emptyList()
        return normalize(stored.split(SEP, '\n'))
    }

    fun contains(stored: String?, tag: String): Boolean {
        val want = tag.trim().lowercase()
        if (want.isEmpty()) return false
        return decode(stored).any { it.lowercase() == want }
    }
}
