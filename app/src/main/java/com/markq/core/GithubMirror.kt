package com.markq.core

import java.net.URI

/**
 * GH Proxy URL rewrite for GitHub file downloads.
 *
 * Docs: prefix `https://gh.4o.pw/` + original HTTPS URL, e.g.
 * `https://gh.4o.pw/https://github.com/owner/repo/releases/download/tag/file`.
 * Allowed upstream hosts: github.com, *.github.com, githubusercontent.com,
 * *.githubusercontent.com.
 */
object GithubMirror {
    const val PREFIX = "https://gh.4o.pw/"

    fun rewrite(url: String, enabled: Boolean): String {
        if (!enabled) return url
        val source = url.trim()
        if (source.isEmpty()) return source
        if (source.startsWith(PREFIX, ignoreCase = true)) return source
        val httpsUrl = toHttps(source) ?: return url
        val host = hostOf(httpsUrl) ?: return url
        if (!isAllowedHost(host)) return url
        return PREFIX + httpsUrl
    }

    internal fun isAllowedHost(host: String): Boolean {
        val h = host.trim().lowercase().trimEnd('.')
        return h == "github.com" ||
            h.endsWith(".github.com") ||
            h == "githubusercontent.com" ||
            h.endsWith(".githubusercontent.com")
    }

    private fun toHttps(url: String): String? {
        return when {
            url.startsWith("https://", ignoreCase = true) -> "https://" + url.substring(8)
            url.startsWith("http://", ignoreCase = true) -> "https://" + url.substring(7)
            else -> null
        }
    }

    private fun hostOf(url: String): String? {
        return try {
            URI(url).host
        } catch (_: Exception) {
            null
        }
    }
}
