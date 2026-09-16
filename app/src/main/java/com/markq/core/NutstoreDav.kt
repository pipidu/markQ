package com.markq.core

object NutstoreDav {
    const val DEFAULT_SERVER = "https://dav.jianguoyun.com/dav/"
    const val DEFAULT_DIR = "MarkQ"

    fun collectionUrl(server: String, remoteDir: String): String {
        val base = server.trim().trimEnd('/')
        val folder = remoteDir.trim().trim('/')
        if (base.isEmpty()) return ""
        return if (folder.isEmpty()) "$base/" else "$base/$folder/"
    }
}
