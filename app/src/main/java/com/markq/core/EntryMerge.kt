package com.markq.core

import java.time.Instant

data class MarkAttachment(
    val id: String,
    val name: String,
    val mime: String,
    val kind: String,
    val size: Long,
    val sha256: String,
)

data class MarkEntry(
    val id: String,
    val occurredAt: Instant,
    val createdAt: Instant,
    val contentUpdatedAt: Instant,
    val statusUpdatedAt: Instant,
    val text: String,
    val createdBy: String,
    val updatedBy: String,
    val completed: Boolean = false,
    val completedBy: String? = null,
    val completedAt: Instant? = null,
    val deleted: Boolean = false,
    val deletedBy: String? = null,
    val deletedAt: Instant? = null,
    val attachments: List<MarkAttachment> = emptyList(),
) {
    fun tieBreakKey(): String =
        listOf(
            updatedBy,
            text,
            occurredAt.toString(),
            completed.toString(),
            deleted.toString(),
            completedBy.orEmpty(),
            deletedBy.orEmpty(),
            attachments.joinToString { it.id + it.sha256 },
        ).joinToString("|")
}

object EntryMerge {
    fun merge(local: MarkEntry, remote: MarkEntry): MarkEntry {
        require(local.id == remote.id) { "Cannot merge different entries" }
        val content = pick(local, remote) { it.contentUpdatedAt }
        val status = pick(local, remote) { it.statusUpdatedAt }
        return MarkEntry(
            id = local.id,
            occurredAt = content.occurredAt,
            createdAt = min(local.createdAt, remote.createdAt),
            contentUpdatedAt = content.contentUpdatedAt,
            statusUpdatedAt = status.statusUpdatedAt,
            text = content.text,
            createdBy = olderAuthor(local, remote),
            updatedBy = newestAuthor(content, status),
            completed = status.completed,
            completedBy = status.completedBy,
            completedAt = status.completedAt,
            deleted = status.deleted,
            deletedBy = status.deletedBy,
            deletedAt = status.deletedAt,
            attachments = content.attachments,
        )
    }

    private fun pick(
        local: MarkEntry,
        remote: MarkEntry,
        time: (MarkEntry) -> Instant,
    ): MarkEntry {
        val lt = time(local)
        val rt = time(remote)
        return when {
            lt > rt -> local
            rt > lt -> remote
            local.tieBreakKey() >= remote.tieBreakKey() -> local
            else -> remote
        }
    }

    private fun min(a: Instant, b: Instant): Instant = if (a <= b) a else b

    private fun olderAuthor(local: MarkEntry, remote: MarkEntry): String =
        if (local.createdAt <= remote.createdAt) local.createdBy else remote.createdBy

    private fun newestAuthor(content: MarkEntry, status: MarkEntry): String =
        if (content.contentUpdatedAt >= status.statusUpdatedAt) content.updatedBy else status.updatedBy
}
