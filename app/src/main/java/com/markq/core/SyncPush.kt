package com.markq.core

/**
 * Local-ack policy after a successful WebDAV PUT.
 *
 * The bytes that landed on the server are a snapshot. A later local edit of the
 * same row must keep [dirty] set, or the new text would sit locally, look synced,
 * and never upload. The new remote ETag is still recorded so the next push can
 * send a matching If-Match.
 */
object SyncPush {
    fun shouldClearDirty(
        uploadedContentUpdatedAt: Long,
        uploadedStatusUpdatedAt: Long,
        currentContentUpdatedAt: Long,
        currentStatusUpdatedAt: Long,
    ): Boolean {
        return uploadedContentUpdatedAt == currentContentUpdatedAt &&
            uploadedStatusUpdatedAt == currentStatusUpdatedAt
    }

    fun shouldEnsureLayout(readyFor: String?, collectionUrl: String): Boolean {
        val ready = readyFor?.trim().orEmpty()
        return ready.isEmpty() || ready != collectionUrl
    }

    fun shouldEnsureFilesPath(hasAttachments: Boolean): Boolean = hasAttachments
}

/** Persist a set of WebDAV hosts that require an unquoted If-Match. */
object WebDavHostPrefs {
    fun encode(hosts: Set<String>): String {
        return hosts.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.sorted().joinToString("\n")
    }

    fun decode(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split('\n', ',', ';')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
