package com.markq.core

object MarkSearch {
    fun matches(
        query: String,
        text: String,
        tags: List<String>,
        vararg authors: String?,
    ): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val haystack = buildString {
            append(text)
            tags.forEach { tag ->
                append('\u0000')
                append(tag)
            }
            authors.forEach { author ->
                if (!author.isNullOrBlank()) {
                    append('\u0000')
                    append(author)
                }
            }
        }
        return q.split(Regex("\\s+")).filter { it.isNotEmpty() }.all { token ->
            haystack.contains(token, ignoreCase = true)
        }
    }
}
