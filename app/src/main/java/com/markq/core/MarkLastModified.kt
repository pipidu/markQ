package com.markq.core

import java.time.Instant

/**
 * 「最后修改时间」 for a mark.
 *
 * Content edits advance [contentUpdatedAt]. Complete, uncomplete, and delete advance
 * [statusUpdatedAt]. Both change the mark, and `updatedBy` already follows the newer
 * clock, so the time shown here is that same later instant. A delete hides the row,
 * so its status time is not displayed. No extra field is stored.
 */
object MarkLastModified {
    fun at(contentUpdatedAt: Instant, statusUpdatedAt: Instant): Instant =
        if (contentUpdatedAt >= statusUpdatedAt) contentUpdatedAt else statusUpdatedAt

    fun epochMs(contentUpdatedAtMs: Long, statusUpdatedAtMs: Long): Long =
        maxOf(contentUpdatedAtMs, statusUpdatedAtMs)
}
