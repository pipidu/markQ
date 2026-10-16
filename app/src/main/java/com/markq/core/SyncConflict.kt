package com.markq.core

/**
 * WebDAV push conflict policy.
 *
 * A 412 means the other side already stored different bytes. Retrying the same
 * PUT with If-Match removed would overwrite those bytes, so that outcome is not
 * allowed while the remote resource still exists.
 */
object SyncConflict {
    const val MAX_MERGED_PUTS = 4

    enum class FileAction { UploadAttachments, DeleteRemoteFiles }

    /**
     * Remote files follow the entry that was actually written after merge.
     * [preMergeDeleted] is the local flag from before that merge. It must not
     * delete the files collection or skip uploads when the merged entry is alive.
     */
    fun filesFollowMergedEntry(preMergeDeleted: Boolean, mergedDeleted: Boolean): FileAction {
        if (preMergeDeleted == mergedDeleted && mergedDeleted) return FileAction.DeleteRemoteFiles
        return if (mergedDeleted) FileAction.DeleteRemoteFiles else FileAction.UploadAttachments
    }

    /** Dropping If-Match is only safe when the remote resource is already gone. */
    fun putWithoutMatchAllowed(remoteStillExists: Boolean): Boolean = !remoteStillExists

    sealed class Retry {
        data class Again(val ifMatch: String) : Retry()
        data object Unresolved : Retry()
    }

    fun retryPut(refreshedMatch: String?, conflictsSoFar: Int): Retry {
        if (conflictsSoFar >= MAX_MERGED_PUTS) return Retry.Unresolved
        val match = refreshedMatch?.trim().orEmpty()
        if (match.isEmpty()) return Retry.Unresolved
        return Retry.Again(match)
    }

    /**
     * If-Match for a follow-up PUT of an entry whose attachments just changed.
     * A blank result means the live resource has no validator, so the PUT must
     * omit If-Match. Throwing 412 here left the mark dirty forever on servers
     * that answer PUT with 204 and no ETag.
     */
    fun followUpIfMatch(stored: String?, live: String?): String? {
        val storedToken = stored?.trim()?.takeIf { it.isNotEmpty() }
        if (storedToken != null) return storedToken
        return live?.trim()?.takeIf { it.isNotEmpty() }
    }

    sealed class AttachmentConflict {
        /** Remote bytes match ours; keep the remote etag and do not PUT. */
        data class AdoptRemote(val etag: String?) : AttachmentConflict()

        /** Remote file is gone, so a PUT without If-Match creates it. */
        data object UploadAsNew : AttachmentConflict()

        /**
         * Remote bytes differ. Leave that URL alone and store our bytes under [forkId].
         */
        data class KeepRemoteAndForkLocal(val forkId: String) : AttachmentConflict()
    }

    fun resolveAttachment412(
        localSha256: String,
        remoteSha256: String?,
        remoteEtag: String?,
        remoteMissing: Boolean,
        forkId: String,
    ): AttachmentConflict {
        if (remoteMissing) return AttachmentConflict.UploadAsNew
        if (!remoteSha256.isNullOrBlank() && remoteSha256.equals(localSha256, ignoreCase = true)) {
            return AttachmentConflict.AdoptRemote(remoteEtag)
        }
        return AttachmentConflict.KeepRemoteAndForkLocal(forkId)
    }
}

/**
 * PUT loop shared by entry and template JSON. Attachment bytes do not go through
 * this with a null If-Match: [SyncConflict.resolveAttachment412] decides those.
 */
object ConditionalPut {
    class PreconditionFailed : Exception()
    class RemoteMissing : Exception()

    suspend fun run(
        initial: ByteArray,
        ifMatch: String?,
        put: suspend (ByteArray, String?) -> String?,
        onConflict: suspend () -> Pair<ByteArray, String?>,
    ): String? {
        var payload = initial
        var match = ifMatch
        var conflicts = 0
        while (true) {
            try {
                return put(payload, match)
            } catch (_: PreconditionFailed) {
                // Remote changed. Refresh below; never replay [payload] with If-Match removed.
            }
            val (retryBytes, retryMatch) = try {
                onConflict()
            } catch (_: RemoteMissing) {
                if (!SyncConflict.putWithoutMatchAllowed(remoteStillExists = false)) {
                    throw PreconditionFailed()
                }
                return put(payload, null)
            }
            when (val decision = SyncConflict.retryPut(retryMatch, conflicts)) {
                SyncConflict.Retry.Unresolved -> throw PreconditionFailed()
                is SyncConflict.Retry.Again -> {
                    if (!SyncConflict.putWithoutMatchAllowed(remoteStillExists = true) &&
                        decision.ifMatch.isBlank()
                    ) {
                        throw PreconditionFailed()
                    }
                    payload = retryBytes
                    match = decision.ifMatch
                    conflicts++
                }
            }
        }
    }
}
