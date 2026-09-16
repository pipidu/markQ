package com.markq.data.local

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class AttachmentStore(context: Context) {
    private val root = File(context.filesDir, "attachments").apply { mkdirs() }
    private val blobs = File(root, "blobs").apply { mkdirs() }

    fun blob(sha256: String): File = File(blobs, sha256)

    fun locate(entryId: String, attachId: String, sha256: String?): File {
        if (!sha256.isNullOrBlank()) {
            val hashed = blob(sha256)
            if (hashed.exists()) return hashed
        }
        return File(File(root, entryId), attachId)
    }

    fun copyFromUri(context: Context, uri: Uri, entryId: String, attachId: String): StoredFile {
        val tmp = File(root, "tmp-${UUID.randomUUID()}")
        try {
            val path = uri.path
            if (uri.scheme == "file" && path != null) {
                File(path).copyTo(tmp, overwrite = true)
            } else {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to read attached file" }
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
            }
            return commitBlob(tmp)
        } finally {
            tmp.delete()
        }
    }

    fun writeBytes(entryId: String, attachId: String, bytes: ByteArray): StoredFile {
        val tmp = File(root, "tmp-${UUID.randomUUID()}")
        try {
            tmp.writeBytes(bytes)
            return commitBlob(tmp)
        } finally {
            tmp.delete()
        }
    }

    fun readBytes(entryId: String, attachId: String, sha256: String? = null): ByteArray? {
        val dest = locate(entryId, attachId, sha256)
        return if (dest.exists()) dest.readBytes() else null
    }

    fun prune(keepHashes: Set<String>, maxBytes: Long) {
        val keep = keepHashes.filter { it.isNotBlank() }.toSet()
        blobs.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in keep) file.delete()
        }
        root.listFiles()?.forEach { child ->
            if (child.isDirectory && child.name != "blobs") {
                child.listFiles()?.forEach { legacy ->
                    if (legacy.isFile) {
                        val hash = runCatching { sha256(legacy) }.getOrNull()
                        if (hash == null || hash !in keep) legacy.delete()
                    }
                }
                if (child.listFiles().isNullOrEmpty()) child.delete()
            }
        }
        var total = totalSize()
        if (total <= maxBytes) return
        val extras = blobs.listFiles()
            ?.filter { it.isFile && it.name !in keep }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
        for (file in extras) {
            if (total <= maxBytes) break
            total -= file.length()
            file.delete()
        }
    }

    fun totalSize(): Long {
        var sum = 0L
        root.walkTopDown().forEach { if (it.isFile) sum += it.length() }
        return sum
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

    private fun commitBlob(tmp: File): StoredFile {
        val hash = sha256(tmp)
        val dest = blob(hash)
        if (!dest.exists()) {
            dest.parentFile?.mkdirs()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
            }
        }
        return StoredFile(dest, hash, dest.length())
    }

    data class StoredFile(val file: File, val sha256: String, val size: Long)
}
