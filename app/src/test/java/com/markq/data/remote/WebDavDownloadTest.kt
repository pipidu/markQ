package com.markq.data.remote

import java.io.File
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WebDavDownloadTest {
    @Test
    fun downloadToStreamsBodyToFile() {
        val payload = ByteArray(2 * 1024 * 1024) { index -> (index % 251).toByte() }
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("ETag", "\"att-1\"")
                .addHeader("Last-Modified", "Wed, 18 Nov 2026 00:00:00 GMT")
                .setBody(Buffer().write(payload)),
        )
        server.start()
        try {
            val client = WebDavClient(OkHttpClient())
            val dest = File.createTempFile("markq-att", ".bin")
            try {
                val got = client.downloadTo(config(server), server.url("/dav/MarkQ/files/e1/a1"), dest)
                assertEquals("att-1", got.etag)
                assertEquals("Wed, 18 Nov 2026 00:00:00 GMT", got.lastModified)
                assertEquals(payload.size.toLong(), dest.length())
                assertArrayEquals(payload, dest.readBytes())
            } finally {
                dest.delete()
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun downloadToPropagatesMissingAttachment() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()
        try {
            val client = WebDavClient(OkHttpClient())
            val dest = File.createTempFile("markq-att-missing", ".bin")
            try {
                client.downloadTo(config(server), server.url("/dav/MarkQ/files/e1/missing"), dest)
                fail("404 must surface as WebDavException")
            } catch (e: WebDavException) {
                assertEquals(404, e.code)
            } finally {
                dest.delete()
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun getStillReadsSmallJsonBodies() {
        val body = """{"id":"e1","text":"hello"}"""
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("ETag", "\"json-1\"")
                .setBody(body),
        )
        server.start()
        try {
            val client = WebDavClient(OkHttpClient())
            val got = client.get(config(server), server.url("/dav/MarkQ/entries/e1.json"))
            assertEquals("json-1", got.etag)
            assertEquals(body, got.bytes.toString(Charsets.UTF_8))
        } finally {
            server.shutdown()
        }
    }

    private fun config(server: MockWebServer) = WebDavConfig(
        baseUrl = server.url("/dav/MarkQ/").toString(),
        username = "markq",
        password = "not-a-real-password",
        davRoot = server.url("/dav/").toString(),
    )
}
