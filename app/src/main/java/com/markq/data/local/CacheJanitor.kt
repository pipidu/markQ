package com.markq.data.local

import android.content.Context
import java.io.File

object CacheJanitor {
    const val MAX_ATTACH_BYTES = 256L * 1024 * 1024
    const val MAX_PENDING_BYTES = 48L * 1024 * 1024
    const val MAX_PENDING_AGE_MS = 24L * 60 * 60 * 1000
    const val MAX_IMAGE_CACHE_BYTES = 48L * 1024 * 1024

    fun prune(
        context: Context,
        files: AttachmentStore,
        keepHashes: Set<String>,
        keepUpdateApk: Boolean,
    ) {
        files.prune(keepHashes, MAX_ATTACH_BYTES)
        pruneDir(File(context.cacheDir, "pending"), MAX_PENDING_BYTES, MAX_PENDING_AGE_MS)
        pruneDir(File(context.cacheDir, "image_cache"), MAX_IMAGE_CACHE_BYTES, MAX_PENDING_AGE_MS * 14)
        if (!keepUpdateApk) {
            deleteUpdateApks(context)
        }
    }

    fun deleteUpdateApks(context: Context) {
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("markq-update") && file.name.endsWith(".apk")) {
                file.delete()
            }
        }
    }

    private fun pruneDir(dir: File, maxBytes: Long, maxAgeMs: Long) {
        if (!dir.exists()) return
        val now = System.currentTimeMillis()
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        files.forEach { file ->
            if (now - file.lastModified() > maxAgeMs) file.delete()
        }
        var total = files.filter { it.exists() }.sumOf { it.length() }
        if (total <= maxBytes) return
        files.filter { it.exists() }.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= maxBytes) return
            total -= file.length()
            file.delete()
        }
    }
}
