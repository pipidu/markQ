package com.markq.data.local

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

class AttachmentStore(context: Context) {
    private val root = File(context.filesDir, "attachments").apply { mkdirs() }

    fun file(entryId: String, attachId: String): File {
        val dir = File(root, entryId).apply { mkdirs() }
        return File(dir, attachId)
    }

    fun copyFromUri(context: Context, uri: Uri, entryId: String, attachId: String): StoredFile {
        val dest = file(entryId, attachId)
        val path = uri.path
        if (uri.scheme == "file" && path != null) {
            File(path).copyTo(dest, overwrite = true)
        } else {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to read attached file" }
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return StoredFile(dest, sha256(dest), dest.length())
    }

    fun writeBytes(entryId: String, attachId: String, bytes: ByteArray): StoredFile {
        val dest = file(entryId, attachId)
        dest.writeBytes(bytes)
        return StoredFile(dest, sha256(dest), dest.length())
    }

    fun readBytes(entryId: String, attachId: String): ByteArray? {
        val dest = file(entryId, attachId)
        return if (dest.exists()) dest.readBytes() else null
    }

    fun sha256(file: File): String {
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

    data class StoredFile(val file: File, val sha256: String, val size: Long)
}
