package com.markq.data.remote

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class WebDavConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
    /** User-entered WebDAV server URL (e.g. https://dav.jianguoyun.com/dav/). Never MKCOL this path. */
    val davRoot: String,
)

data class DavResource(
    val href: String,
    val url: HttpUrl,
    val isCollection: Boolean,
    val etag: String?,
    val lastModified: String?,
    /** ETag / getetag text as the server sent it, before quote normalization. */
    val rawEtag: String? = null,
)

class WebDavException(message: String, val code: Int? = null) : IOException(message)

class WebDavClient(
    private val http: OkHttpClient,
) {
    /**
     * Hosts whose live ETag is an unquoted token (Nutstore). Remembered so the
     * next PUT sends that spelling first instead of eating a false 412.
     */
    private val unquotedIfMatchHosts = ConcurrentHashMap.newKeySet<String>()
    fun entriesUrl(config: WebDavConfig): HttpUrl = join(config.baseUrl, "entries")

    fun filesUrl(config: WebDavConfig): HttpUrl = join(config.baseUrl, "files")

    fun templatesUrl(config: WebDavConfig): HttpUrl = join(config.baseUrl, "templates")

    fun entryUrl(config: WebDavConfig, id: String): HttpUrl =
        join(config.baseUrl, "entries", "$id.json")

    fun templateUrl(config: WebDavConfig, id: String): HttpUrl =
        join(config.baseUrl, "templates", "$id.json")

    fun attachmentUrl(config: WebDavConfig, entryId: String, attachId: String): HttpUrl =
        join(config.baseUrl, "files", entryId, attachId)

    fun filesEntryUrl(config: WebDavConfig, entryId: String): HttpUrl =
        join(config.baseUrl, "files", entryId)

    fun probe(config: WebDavConfig) {
        propfind(config, join(config.baseUrl), depth = 0)
    }

    fun ensureLayout(config: WebDavConfig) {
        ensurePath(config, join(config.baseUrl))
        ensurePath(config, entriesUrl(config))
        ensurePath(config, filesUrl(config))
        ensurePath(config, templatesUrl(config))
    }

    fun ensurePath(config: WebDavConfig, target: HttpUrl) {
        for (url in collectionsToMkcol(config.davRoot, target)) {
            mkcolIfNeeded(config, url)
        }
    }

    fun mkcolIfNeeded(config: WebDavConfig, url: HttpUrl) {
        if (shouldSkipMkcol(config.davRoot, url)) return
        val request = authorized(config, Request.Builder().url(url).method("MKCOL", ByteArray(0).toRequestBody(null)))
        http.newCall(request).execute().use { response ->
            when (response.code) {
                201, 403, 405, 301, 302, 307, 308, 409 -> Unit
                in 200..299 -> Unit
                else -> throw WebDavException("MKCOL ${url.encodedPath} failed (${response.code})", response.code)
            }
        }
    }

    fun propfind(
        config: WebDavConfig,
        url: HttpUrl,
        depth: Int = 1,
        directory: Boolean = true,
    ): List<DavResource> {
        val target = if (directory) ensureCollectionUrl(url) else url
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:getetag/>
                <d:getlastmodified/>
                <d:resourcetype/>
              </d:prop>
            </d:propfind>
        """.trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())
        val request = authorized(
            config,
            Request.Builder()
                .url(target)
                .method("PROPFIND", body)
                .header("Depth", depth.toString())
                .header("Content-Type", "application/xml; charset=utf-8"),
        )
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful && response.code != 207) {
                throw WebDavException("PROPFIND ${url.encodedPath} failed (${response.code})", response.code)
            }
            return parseMultiStatus(text, target)
        }
    }

    data class GetResult(val bytes: ByteArray, val etag: String?, val lastModified: String?)

    fun get(config: WebDavConfig, url: HttpUrl): GetResult {
        val request = authorized(config, Request.Builder().url(url).get())
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw WebDavException("GET ${url.encodedPath} failed (${response.code})", response.code)
            }
            val bytes = response.body?.bytes() ?: ByteArray(0)
            return GetResult(
                bytes = bytes,
                etag = normalizeEtag(response.header("ETag")),
                lastModified = response.header("Last-Modified"),
            )
        }
    }

    /**
     * Conditional PUT.
     *
     * Nutstore answers GET/PROPFIND with an unquoted validator (`Etag: 8sKB…`) and
     * compares `If-Match` to that exact text. v1.0.28 always sent the quoted form
     * (`If-Match: "8sKB…"`), so every update of an existing file was a false 412
     * and [com.markq.core.ConditionalPut] left the entry dirty. A quoted 412 is
     * retried once with the live spelling when the opaque tag is unchanged.
     * A different live tag still fails so the caller can merge instead of clobbering.
     */
    fun put(
        config: WebDavConfig,
        url: HttpUrl,
        bytes: ByteArray,
        contentType: String,
        ifMatch: String?,
    ): String? {
        val normalized = normalizeEtag(ifMatch)
        if (normalized == null) {
            return executePut(config, url, bytes, contentType, ifMatchHeader = null)
        }
        val quoted = quotedEtag(normalized)
        val unquoted = normalized.takeIf { isOpaqueToken(it) }
        val first = if (unquoted != null && url.host in unquotedIfMatchHosts) unquoted else quoted
        try {
            return executePut(config, url, bytes, contentType, first)
        } catch (e: WebDavException) {
            if (e.code != 412) throw e
        }
        val alternate = alternateIfMatch(first, discoverRawEtag(config, url))
        if (alternate == null) {
            throw WebDavException("Precondition failed", 412)
        }
        val etag = executePut(config, url, bytes, contentType, alternate)
        if (isUnquotedHeader(alternate)) {
            unquotedIfMatchHosts.add(url.host)
        } else {
            unquotedIfMatchHosts.remove(url.host)
        }
        return etag
    }

    /**
     * Live validator text, or null when the resource is missing or publishes none.
     * Network and auth failures propagate; a missing tag does not.
     */
    fun rawEtag(config: WebDavConfig, url: HttpUrl): String? {
        return when (val lookup = lookupEtag(config, url)) {
            is EtagLookup.Found -> lookup.raw?.trim()?.ifBlank { null }
            EtagLookup.Missing -> null
        }
    }

    fun delete(config: WebDavConfig, url: HttpUrl) {
        val request = authorized(config, Request.Builder().url(url).delete())
        http.newCall(request).execute().use { response ->
            when (response.code) {
                200, 202, 204, 404 -> Unit
                else -> throw WebDavException("DELETE ${url.encodedPath} failed (${response.code})", response.code)
            }
        }
    }

    fun deleteCollection(config: WebDavConfig, url: HttpUrl) {
        val listing = try {
            propfind(config, url, depth = 1)
        } catch (e: WebDavException) {
            if (e.code == 404) return else throw e
        }
        val selfPath = ensureCollectionUrl(url).encodedPath.trimEnd('/')
        for (resource in listing) {
            val path = resource.url.encodedPath.trimEnd('/')
            if (path.isEmpty() || path == selfPath) continue
            if (resource.isCollection) {
                deleteCollection(config, resource.url)
            } else {
                delete(config, resource.url)
            }
        }
        delete(config, url)
    }

    private fun executePut(
        config: WebDavConfig,
        url: HttpUrl,
        bytes: ByteArray,
        contentType: String,
        ifMatchHeader: String?,
    ): String? {
        val builder = Request.Builder()
            .url(url)
            .put(bytes.toRequestBody(contentType.toMediaType()))
            .header("Content-Type", contentType)
        if (!ifMatchHeader.isNullOrBlank()) {
            builder.header("If-Match", ifMatchHeader)
        }
        val request = authorized(config, builder)
        val fromResponse = http.newCall(request).execute().use { response ->
            if (response.code == 412) {
                throw WebDavException("Precondition failed", 412)
            }
            if (!response.isSuccessful && response.code != 201 && response.code != 204) {
                throw WebDavException("PUT ${url.encodedPath} failed (${response.code})", response.code)
            }
            normalizeEtag(response.header("ETag"))
        }
        if (fromResponse != null) return fromResponse
        // Nutstore PUT is 204 with no ETag. Read the new validator so the next
        // update can send If-Match instead of wiping the stored tag.
        return normalizeEtag(discoverRawEtag(config, url))
    }

    private fun discoverRawEtag(config: WebDavConfig, url: HttpUrl): String? {
        return try {
            when (val lookup = lookupEtag(config, url)) {
                is EtagLookup.Found -> lookup.raw
                EtagLookup.Missing -> null
            }
        } catch (_: IOException) {
            null
        }
    }

    private sealed class EtagLookup {
        data class Found(val raw: String?) : EtagLookup()
        data object Missing : EtagLookup()
    }

    private fun lookupEtag(config: WebDavConfig, url: HttpUrl): EtagLookup {
        // HEAD 404 is not proof the file is gone: some WebDAV servers reject HEAD.
        // Fall through to PROPFIND unless HEAD already returned a validator.
        val head = try {
            readHead(config, url)
        } catch (e: WebDavException) {
            if (e.code == 401 || e.code == 403) throw e
            null
        }
        if (head is EtagLookup.Found && !head.raw.isNullOrBlank()) return head
        return try {
            val listing = propfind(config, url, depth = 0, directory = false)
            val self = listing.firstOrNull {
                it.url.encodedPath.trimEnd('/') == url.encodedPath.trimEnd('/')
            } ?: listing.firstOrNull { !it.isCollection }
            val raw = (self?.rawEtag ?: self?.etag)?.trim()?.ifBlank { null }
            if (raw == null && head is EtagLookup.Missing) EtagLookup.Missing else EtagLookup.Found(raw)
        } catch (e: WebDavException) {
            when {
                e.code == 401 || e.code == 403 -> throw e
                e.code == 404 || head is EtagLookup.Missing -> EtagLookup.Missing
                head is EtagLookup.Found -> head
                else -> throw e
            }
        }
    }

    private fun readHead(config: WebDavConfig, url: HttpUrl): EtagLookup {
        val request = authorized(config, Request.Builder().url(url).head())
        http.newCall(request).execute().use { response ->
            return when (response.code) {
                404 -> EtagLookup.Missing
                405, 501 -> EtagLookup.Found(null)
                in 200..299 -> EtagLookup.Found(response.header("ETag")?.trim()?.ifBlank { null })
                else -> throw WebDavException("HEAD ${url.encodedPath} failed (${response.code})", response.code)
            }
        }
    }

    private fun authorized(config: WebDavConfig, builder: Request.Builder): Request {
        builder.header("User-Agent", "MarkQ")
        // Identity keeps the strong validator. Gzip would rewrite ETag to a weak
        // or "-gzip" tag that If-Match can never satisfy.
        builder.header("Accept-Encoding", "identity")
        if (config.username.isNotBlank() || config.password.isNotEmpty()) {
            builder.header("Authorization", Credentials.basic(config.username, config.password))
        }
        return builder.build()
    }

    companion object {
        fun join(base: String, vararg parts: String): HttpUrl {
            val root = base.trim().trimEnd('/').toHttpUrl()
            val builder = root.newBuilder()
            parts.forEach { part ->
                part.trim('/').split('/').filter { it.isNotEmpty() }.forEach { segment ->
                    builder.addPathSegment(segment)
                }
            }
            return builder.build()
        }

        fun ensureCollectionUrl(url: HttpUrl): HttpUrl {
            val path = url.encodedPath
            return if (path.endsWith("/")) url else url.newBuilder().encodedPath("$path/").build()
        }

        fun normalizeEtag(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            return raw.trim().removePrefix("W/").trim().trim('"')
        }

        fun quotedEtag(etag: String): String {
            val n = normalizeEtag(etag) ?: etag
            return "\"$n\""
        }

        private val codingSuffix = Regex("-(?:gzip|br|deflate)$", RegexOption.IGNORE_CASE)
        fun isOpaqueToken(value: String): Boolean {
            if (value.isEmpty()) return false
            return value.none { it.isWhitespace() || it == ',' || it == '"' || it == '\\' }
        }

        fun isUnquotedHeader(header: String): Boolean {
            val stripped = header.trim().removePrefix("W/").removePrefix("w/").trim()
            return stripped.isNotEmpty() && !stripped.startsWith("\"")
        }

        /**
         * Second `If-Match` value after [attemptedHeader] got 412.
         * Returns null when [liveRaw] is a different validator (real conflict)
         * or when the spelling we already sent is the one the server published.
         */
        fun alternateIfMatch(attemptedHeader: String, liveRaw: String?): String? {
            val live = liveRaw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val liveOpaque = normalizeEtag(live) ?: return null
            val attemptedOpaque = normalizeEtag(attemptedHeader) ?: return null
            val liveBare = liveOpaque.replace(codingSuffix, "")
            val attemptedBare = attemptedOpaque.replace(codingSuffix, "")
            if (liveBare != attemptedBare) return null
            val liveUnquoted = isUnquotedHeader(live)
            val published = liveOpaque.takeIf { isOpaqueToken(it) }
            if (liveUnquoted && published != null && attemptedHeader != published) {
                return published
            }
            val bareQuoted = "\"$liveBare\""
            val codingMismatch = liveOpaque != liveBare || attemptedOpaque != attemptedBare
            if (bareQuoted != attemptedHeader && (codingMismatch || !liveUnquoted)) {
                return bareQuoted
            }
            return null
        }

        fun parseMultiStatus(xml: String, requestUrl: HttpUrl): List<DavResource> {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), Charsets.UTF_8.name())
            val results = mutableListOf<DavResource>()
            var href: String? = null
            var etag: String? = null
            var lastModified: String? = null
            var isCollection = false
            var inResponse = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.lowercase()
                        when (name) {
                            "response" -> {
                                inResponse = true
                                href = null
                                etag = null
                                lastModified = null
                                isCollection = false
                            }
                            "href" -> if (inResponse) href = parser.nextText()
                            "getetag" -> if (inResponse) etag = parser.nextText()
                            "getlastmodified" -> if (inResponse) lastModified = parser.nextText()
                            "collection" -> if (inResponse) isCollection = true
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("response", ignoreCase = true) && inResponse) {
                            val rawHref = href?.trim().orEmpty()
                            if (rawHref.isNotEmpty()) {
                                val resolved = resolveHref(requestUrl, rawHref)
                                results += DavResource(
                                    href = rawHref,
                                    url = resolved,
                                    isCollection = isCollection,
                                    etag = normalizeEtag(etag),
                                    lastModified = lastModified?.trim()?.ifBlank { null },
                                    rawEtag = etag?.trim()?.ifBlank { null },
                                )
                            }
                            inResponse = false
                        }
                    }
                }
                event = parser.next()
            }
            return results
        }

        fun resolveHref(base: HttpUrl, href: String): HttpUrl {
            val decoded = href.trim()
            return when {
                decoded.startsWith("http://") || decoded.startsWith("https://") -> decoded.toHttpUrl()
                decoded.startsWith("/") -> {
                    base.newBuilder().encodedPath(decoded).query(null).fragment(null).build()
                }
                else -> base.resolve(decoded) ?: join(base.toString(), decoded)
            }
        }

        fun needsDownload(localEtag: String?, localModified: String?, remote: DavResource): Boolean {
            val remoteEtag = remote.etag
            if (!remoteEtag.isNullOrBlank() && remoteEtag == localEtag) return false
            if (remoteEtag.isNullOrBlank() && !remote.lastModified.isNullOrBlank() && remote.lastModified == localModified) {
                return false
            }
            return true
        }

        fun cursorKey(url: HttpUrl): String = url.encodedPath

        fun davRootUrl(davRoot: String): HttpUrl {
            val trimmed = davRoot.trim().ifBlank { error("davRoot") }
            val withSlash = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
            return withSlash.toHttpUrl()
        }

        /**
         * Collections to MKCOL for [target], skipping the DAV root (e.g. `/dav`) and `/`.
         * For Nutstore `https://dav.jianguoyun.com/dav/` + save dir `MarkQ`, this is only `…/dav/MarkQ`.
         */
        fun collectionsToMkcol(davRoot: String, target: HttpUrl): List<HttpUrl> {
            val root = davRootUrl(davRoot)
            val rootSegs = root.pathSegments.filter { it.isNotEmpty() }
            val targetSegs = target.pathSegments.filter { it.isNotEmpty() }
            val extra = if (
                targetSegs.size >= rootSegs.size &&
                targetSegs.take(rootSegs.size).map { it.lowercase() } == rootSegs.map { it.lowercase() }
            ) {
                targetSegs.drop(rootSegs.size)
            } else {
                targetSegs
            }
            if (extra.isEmpty()) return emptyList()
            val start = if (
                targetSegs.size >= rootSegs.size &&
                targetSegs.take(rootSegs.size).map { it.lowercase() } == rootSegs.map { it.lowercase() }
            ) {
                root.newBuilder().query(null).fragment(null).build()
            } else {
                target.newBuilder().encodedPath("/").query(null).fragment(null).build()
            }
            var current = start
            return extra.map { segment ->
                current = current.newBuilder().addPathSegment(segment).build()
                current
            }
        }

        /** Never MKCOL `/`, the DAV root (`/dav`), or any prefix of the DAV root. */
        fun shouldSkipMkcol(davRoot: String, url: HttpUrl): Boolean {
            val urlSegs = url.pathSegments.filter { it.isNotEmpty() }
            if (urlSegs.isEmpty()) return true
            val rootSegs = davRootUrl(davRoot).pathSegments.filter { it.isNotEmpty() }
            if (urlSegs.size > rootSegs.size) return false
            return urlSegs.map { it.lowercase() } == rootSegs.take(urlSegs.size).map { it.lowercase() }
        }
    }
}
