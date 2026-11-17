package com.markq.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Local WebDAV stand-in for the upload path. Nutstore mode copies the live
 * behaviour documented against dav.jianguoyun.com: `Etag` is an unquoted token,
 * `If-Match` must be that exact token, and PUT answers 204 with no ETag.
 */
class WebDavPutTest {
    @Test
    fun newFileUploadsWithoutIfMatch() {
        val stub = DavStub(etag = "unused", body = ByteArray(0))
        withServer(stub) { server, client ->
            val body = """{"text":"new"}""".toByteArray()
            client.put(config(server), entryUrl(server), body, JSON, ifMatch = null)
            assertArrayEquals(body, stub.body)
            assertTrue(stub.ifMatches.all { it == null })
            assertEquals(1, stub.methods.count { it == "PUT" })
        }
    }

    @Test
    fun nutstoreUpdateRetriesUnquotedIfMatchAndKeepsLaterPutsOnThatSpelling() {
        val original = "8sKBsnMc5tH71U67xjQTCQ"
        val stub = DavStub(etag = original, body = """{"text":"old"}""".toByteArray())
        withServer(stub) { server, client ->
            val updated = """{"text":"updated"}""".toByteArray()
            val etag = client.put(config(server), entryUrl(server), updated, JSON, original)
            assertArrayEquals(updated, stub.body)
            assertEquals(listOf("\"$original\"", original), stub.ifMatches)
            assertEquals(stub.etag, etag)

            stub.ifMatches.clear()
            val again = """{"text":"again"}""".toByteArray()
            client.put(config(server), entryUrl(server), again, JSON, etag)
            assertArrayEquals(again, stub.body)
            assertEquals(listOf(etag), stub.ifMatches)
        }
    }

    @Test
    fun differentLiveEtagDoesNotOverwrite() {
        val stub = DavStub(etag = "remote-token", body = "remote-bytes".toByteArray())
        withServer(stub) { server, client ->
            try {
                client.put(
                    config(server),
                    entryUrl(server),
                    "local-bytes".toByteArray(),
                    JSON,
                    "8sKBsnMc5tH71U67xjQTCQ",
                )
                fail("stale If-Match must stay a 412")
            } catch (e: WebDavException) {
                assertEquals(412, e.code)
            }
            assertArrayEquals("remote-bytes".toByteArray(), stub.body)
            assertEquals(listOf("\"8sKBsnMc5tH71U67xjQTCQ\""), stub.ifMatches)
        }
    }

    @Test
    fun quotedEtagServerAcceptsTheFirstPut() {
        val stub = DavStub(etag = "abc", body = "old".toByteArray(), acceptQuoted = true)
        withServer(stub) { server, client ->
            val updated = "new".toByteArray()
            client.put(config(server), entryUrl(server), updated, JSON, "abc")
            assertArrayEquals(updated, stub.body)
            assertEquals(listOf("\"abc\""), stub.ifMatches)
        }
    }

    @Test
    fun deferredEtagDiscoverySkipsPerFileHead() {
        val stub = DavStub(etag = "token", body = "old".toByteArray(), acceptQuoted = true)
        withServer(stub) { server, client ->
            val etag = client.put(
                config(server),
                entryUrl(server),
                "new".toByteArray(),
                JSON,
                "token",
                discoverEtag = false,
            )
            assertEquals(null, etag)
            assertEquals(listOf("PUT"), stub.methods)
            assertEquals(0, stub.methods.count { it == "HEAD" || it == "PROPFIND" })
        }
    }

    @Test
    fun rememberedUnquotedHostSkipsTheFalse412OnColdStart() {
        val original = "8sKBsnMc5tH71U67xjQTCQ"
        val stub = DavStub(etag = original, body = """{"text":"old"}""".toByteArray())
        withServer(stub) { server, client ->
            client.loadUnquotedIfMatchHosts(setOf(server.url("/").host))
            val updated = """{"text":"updated"}""".toByteArray()
            client.put(config(server), entryUrl(server), updated, JSON, original)
            assertArrayEquals(updated, stub.body)
            assertEquals(listOf(original), stub.ifMatches)
            assertEquals(1, stub.methods.count { it == "PUT" })
        }
    }

    @Test
    fun aRoundOfPutsWithoutDiscoverDoesNotProbeEachFile() {
        val stub = MultiFileStub()
        withServer(stub) { server, client ->
            val cfg = config(server)
            val urls = (1..3).map { server.url("/dav/MarkQ/entries/$it.json") }
            val etags = urls.mapIndexed { index, url ->
                client.put(cfg, url, "body$index".toByteArray(), JSON, ifMatch = null, discoverEtag = false)
            }
            assertTrue(etags.all { it == null })
            assertEquals(3, stub.methods.count { it == "PUT" })
            assertEquals(0, stub.methods.count { it == "HEAD" })
            assertEquals(0, stub.methods.count { it == "PROPFIND" })
            assertEquals(0, stub.methods.count { it == "MKCOL" })
        }
    }

    @Test
    fun etagsFromListingIndexPathsWithoutExtraRoundTrips() {
        val found = WebDavClient.etagsFromListing(
            listOf(
                DavResource(
                    href = "/dav/MarkQ/entries/1.json",
                    url = "https://dav.example/dav/MarkQ/entries/1.json".toHttpUrl(),
                    isCollection = false,
                    etag = "e1",
                    lastModified = null,
                    rawEtag = "e1",
                ),
                DavResource(
                    href = "/dav/MarkQ/entries/2.json",
                    url = "https://dav.example/dav/MarkQ/entries/2.json".toHttpUrl(),
                    isCollection = false,
                    etag = "e2",
                    lastModified = null,
                ),
                DavResource(
                    href = "/dav/MarkQ/entries/",
                    url = "https://dav.example/dav/MarkQ/entries/".toHttpUrl(),
                    isCollection = true,
                    etag = "dir",
                    lastModified = null,
                ),
            ),
        )
        assertEquals("e1", found["/dav/MarkQ/entries/1.json"])
        assertEquals("e2", found["/dav/MarkQ/entries/2.json"])
        assertEquals("dir", found["/dav/MarkQ/entries/"])
        assertEquals("dir", found["/dav/MarkQ/entries"])
    }

    private fun withServer(stub: Dispatcher, block: (MockWebServer, WebDavClient) -> Unit) {
        val server = MockWebServer()
        server.dispatcher = stub
        server.start()
        try {
            block(server, WebDavClient(OkHttpClient()))
        } finally {
            server.shutdown()
        }
    }

    private class MultiFileStub : Dispatcher() {
        val methods = mutableListOf<String>()
        private val etags = mutableMapOf<String, String>()

        override fun dispatch(request: RecordedRequest): MockResponse {
            methods += request.method.orEmpty()
            val path = request.path?.substringBefore('?') ?: "/"
            return when (request.method) {
                "PUT" -> {
                    val name = path.substringAfterLast('/').substringBefore(".json")
                    etags[path] = "e$name"
                    MockResponse().setResponseCode(204)
                }
                "PROPFIND" -> {
                    val items = etags.entries.joinToString("") { (href, tag) ->
                        """
                          <d:response>
                            <d:href>$href</d:href>
                            <d:propstat>
                              <d:prop><d:getetag>$tag</d:getetag></d:prop>
                              <d:status>HTTP/1.1 200 OK</d:status>
                            </d:propstat>
                          </d:response>
                        """
                    }
                    val xml = """
                        <?xml version="1.0" encoding="utf-8"?>
                        <d:multistatus xmlns:d="DAV:">
                          $items
                        </d:multistatus>
                    """.trimIndent()
                    MockResponse()
                        .setResponseCode(207)
                        .addHeader("Content-Type", "application/xml; charset=utf-8")
                        .setBody(xml)
                }
                "HEAD", "MKCOL" -> MockResponse().setResponseCode(405)
                else -> MockResponse().setResponseCode(405)
            }
        }
    }

    private fun config(server: MockWebServer) = WebDavConfig(
        baseUrl = server.url("/dav/MarkQ/").toString(),
        username = "markq",
        password = "not-a-real-password",
        davRoot = server.url("/dav/").toString(),
    )

    private fun entryUrl(server: MockWebServer): HttpUrl = server.url("/dav/MarkQ/entries/1.json")

    private class DavStub(
        var etag: String,
        var body: ByteArray,
        private val headStatus: Int = 200,
        private val acceptQuoted: Boolean = false,
    ) : Dispatcher() {
        val ifMatches = mutableListOf<String?>()
        val methods = mutableListOf<String>()

        override fun dispatch(request: RecordedRequest): MockResponse {
            methods += request.method.orEmpty()
            return when (request.method) {
                "PUT" -> {
                    val match = request.getHeader("If-Match")
                    ifMatches += match
                    val current = etag
                    val matches = match == null || match == current || (acceptQuoted && match == "\"$current\"")
                    if (!matches) {
                        MockResponse().setResponseCode(412)
                    } else {
                        body = request.body.readByteArray()
                        etag = "v${current.length}${body.size}"
                        MockResponse().setResponseCode(204)
                    }
                }
                "HEAD" -> {
                    if (headStatus != 200) {
                        MockResponse().setResponseCode(headStatus)
                    } else {
                        MockResponse().setResponseCode(200).addHeader("Etag", etag)
                    }
                }
                "PROPFIND" -> {
                    val path = request.path?.substringBefore('?') ?: "/"
                    val xml = """
                        <?xml version="1.0" encoding="utf-8"?>
                        <d:multistatus xmlns:d="DAV:">
                          <d:response>
                            <d:href>$path</d:href>
                            <d:propstat>
                              <d:prop><d:getetag>$etag</d:getetag></d:prop>
                              <d:status>HTTP/1.1 200 OK</d:status>
                            </d:propstat>
                          </d:response>
                        </d:multistatus>
                    """.trimIndent()
                    MockResponse()
                        .setResponseCode(207)
                        .addHeader("Content-Type", "application/xml; charset=utf-8")
                        .setBody(xml)
                }
                else -> MockResponse().setResponseCode(405)
            }
        }
    }

    private companion object {
        const val JSON = "application/json; charset=utf-8"
    }
}
