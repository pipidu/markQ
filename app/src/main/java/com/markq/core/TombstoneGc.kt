package com.markq.core

object TombstoneGc {
    const val MAX_AGE_MS: Long = 30L * 24 * 60 * 60 * 1000

    fun isExpired(deleted: Boolean, deletedAtEpochMs: Long?, nowEpochMs: Long): Boolean {
        if (!deleted) return false
        val at = deletedAtEpochMs ?: return false
        return nowEpochMs - at >= MAX_AGE_MS
    }
}
