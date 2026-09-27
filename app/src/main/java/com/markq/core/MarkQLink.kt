package com.markq.core

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object MarkQLink {
    const val SCHEME = "markq"
    const val HOST_TEMPLATE = "template"
    const val WEB_PATH_PREFIX = "/t/"
    const val DEFAULT_LINK_BASE = "https://markq-openx.4o.pw"

    enum class OpenMode {
        Edit,
        Camera,
        Save,
        ;

        fun query(): String = when (this) {
            Edit -> ""
            Camera -> "?camera=1"
            Save -> "?save=1"
        }
    }

    fun templateUri(id: String, mode: OpenMode = OpenMode.Edit): String {
        val clean = id.trim()
        require(clean.isNotEmpty()) { "template id" }
        return "$SCHEME://$HOST_TEMPLATE/${pathEncode(clean)}${mode.query()}"
    }

    /** Origin only: scheme + host + optional port. No path. */
    fun normalizeLinkBase(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = try {
            URI(withScheme)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.trim()?.trim('.') ?: return null
        if (host.isEmpty()) return null
        val port = if (uri.port > 0) ":${uri.port}" else ""
        return "$scheme://$host$port"
    }

    fun webTemplateUrl(base: String, id: String, mode: OpenMode = OpenMode.Edit): String? {
        val origin = if (base.isBlank()) {
            DEFAULT_LINK_BASE
        } else {
            normalizeLinkBase(base) ?: return null
        }
        val clean = id.trim()
        if (clean.isEmpty()) return null
        return "$origin$WEB_PATH_PREFIX${pathEncode(clean)}${mode.query()}"
    }

    fun parseOpenCamera(raw: String?): Boolean = queryFlag(raw, "camera")

    fun parseOpenSave(raw: String?): Boolean = queryFlag(raw, "save")

    fun parseOpenMode(raw: String?): OpenMode {
        if (parseOpenCamera(raw)) return OpenMode.Camera
        if (parseOpenSave(raw)) return OpenMode.Save
        return OpenMode.Edit
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

    private fun queryFlag(raw: String?, name: String): Boolean {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val query = try {
            URI(text).query
        } catch (_: Exception) {
            null
        } ?: text.substringAfter('?', "").substringBefore('#')
        if (query.isBlank()) return false
        return query.split("&").any { part ->
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "1")
            key.equals(name, ignoreCase = true) &&
                (value.isEmpty() || value == "1" || value.equals("true", ignoreCase = true))
        }
    }

    private fun pathEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun pathDecode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
