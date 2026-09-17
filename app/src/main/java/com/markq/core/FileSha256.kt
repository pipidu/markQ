package com.markq.core

import java.io.File
import java.security.MessageDigest

object FileSha256 {
    private val hex64 = Regex("[0-9a-fA-F]{64}")
    private val labeled = Regex(
        """(?i)sha-?256[^0-9a-fA-F]{0,40}([0-9a-fA-F]{64})""",
    )

    fun of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun of(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** First labeled SHA-256, otherwise the first standalone 64-char hex token. */
    fun parsePublished(text: String): String? {
        if (text.isBlank()) return null
        labeled.find(text)?.groupValues?.getOrNull(1)?.let { return it.lowercase() }
        return hex64.find(text)?.value?.lowercase()
    }

    fun matches(actual: String, expected: String): Boolean =
        actual.equals(expected.trim(), ignoreCase = true)
}
