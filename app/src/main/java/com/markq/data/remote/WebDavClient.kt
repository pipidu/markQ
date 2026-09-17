package com.markq.data.remote

import java.io.ByteArrayInputStream
import java.io.IOException
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
)

class WebDavException(message: String, val code: Int? = null) : IOException(message)

class WebDavClient(
    private val http: OkHttpClient,
) {
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

    fun propfind(config: WebDavConfig, url: HttpUrl, depth: Int = 1): List<DavResource> {
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
                .url(ensureCollectionUrl(url))
                .method("PROPFIND", body)
                .header("Depth", depth.toString())
                .header("Content-Type", "application/xml; charset=utf-8"),
        )
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful && response.code != 207) {
                throw WebDavException("PROPFIND ${url.encodedPath} failed (${response.code})", response.code)
            }
            return parseMultiStatus(text, url)
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

    fun put(
        config: WebDavConfig,
        url: HttpUrl,
        bytes: ByteArray,
        contentType: String,
        ifMatch: String?,
    ): String? {
        val builder = Request.Builder()
            .url(url)
            .put(bytes.toRequestBody(contentType.toMediaType()))
            .header("Content-Type", contentType)
        if (!ifMatch.isNullOrBlank()) {
            builder.header("If-Match", quotedEtag(ifMatch))
        }
        val request = authorized(config, builder)
        http.newCall(request).execute().use { response ->
            if (response.code == 412) {
                throw WebDavException("Precondition failed", 412)
            }
            if (!response.isSuccessful && response.code != 201) {
                throw WebDavException("PUT ${url.encodedPath} failed (${response.code})", response.code)
            }
            return normalizeEtag(response.header("ETag"))
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

    private fun authorized(config: WebDavConfig, builder: Request.Builder): Request {
        builder.header("User-Agent", "MarkQ")
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
