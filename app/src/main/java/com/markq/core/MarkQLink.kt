package com.markq.core

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object MarkQLink {
    const val SCHEME = "markq"
    const val HOST_TEMPLATE = "template"

    fun templateUri(id: String): String {
        val clean = id.trim()
        require(clean.isNotEmpty()) { "template id" }
        return "$SCHEME://$HOST_TEMPLATE/${pathEncode(clean)}"
    }

    fun parseTemplateId(raw: String?): String? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = try {
            URI(text)
        } catch (_: Exception) {
            return null
        }
        if (!SCHEME.equals(uri.scheme, ignoreCase = true)) return null
        val host = uri.host
        val path = uri.path.orEmpty()
        val id = when {
            HOST_TEMPLATE.equals(host, ignoreCase = true) -> {
                path.trim('/').substringBefore('/')
            }
            host.isNullOrBlank() -> {
                val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
                if (segments.size >= 2 && HOST_TEMPLATE.equals(segments[0], ignoreCase = true)) {
                    segments[1]
                } else {
                    null
                }
            }
            else -> null
        }
        if (id.isNullOrBlank()) return null
        return pathDecode(id).takeIf { it.isNotBlank() }
    }

    private fun pathEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun pathDecode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
