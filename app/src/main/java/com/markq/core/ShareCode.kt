package com.markq.core

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SharePayload(
    val u: String,
    val l: String = "",
    val p: String = "",
    val d: String = "",
) {
    val url: String get() = u
    val username: String get() = l
    val password: String get() = p
    val remoteDir: String get() = d
}

object ShareCode {
    const val PREFIX = "MQ1_"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun encode(
        url: String,
        username: String = "",
        password: String = "",
        remoteDir: String = "",
    ): String {
        val payload = SharePayload(
            u = url.trim(),
            l = username,
            p = password,
            d = remoteDir.trim().trim('/'),
        )
        val deflated = deflate(json.encodeToString(SharePayload.serializer(), payload).toByteArray(Charsets.UTF_8))
        return PREFIX + base64UrlEncode(deflated)
    }

    fun decode(raw: String): SharePayload {
        val code = raw.trim()
        require(code.startsWith(PREFIX)) { "Not a MarkQ share code" }
        val bytes = base64UrlDecode(code.removePrefix(PREFIX))
        val payload = json.decodeFromString(SharePayload.serializer(), String(inflate(bytes), Charsets.UTF_8))
        require(payload.u.isNotBlank()) { "Share code is missing the WebDAV URL" }
        return payload
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(input)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(256)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            if (n > 0) out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun inflate(input: ByteArray): ByteArray {
        val inflater = Inflater(true)
        inflater.setInput(input)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(256)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n > 0) out.write(buf, 0, n) else if (inflater.needsInput()) break
        }
        inflater.end()
        return out.toByteArray()
    }

    private fun base64UrlEncode(bytes: ByteArray): String =
        String(Base64.getUrlEncoder().withoutPadding().encode(bytes), Charsets.US_ASCII)

    private fun base64UrlDecode(text: String): ByteArray =
        Base64.getUrlDecoder().decode(text)
}
