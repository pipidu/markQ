package com.markq.core

import java.time.Instant

data class MarkTemplate(
    val id: String,
    val name: String,
    val text: String,
    val color: String?,
    val tags: List<String> = emptyList(),
    val createdAt: Instant,
    val contentUpdatedAt: Instant,
    val statusUpdatedAt: Instant,
    val createdBy: String,
    val updatedBy: String,
    val deleted: Boolean = false,
    val deletedBy: String? = null,
    val deletedAt: Instant? = null,
) {
    fun tieBreakKey(): String =
        listOf(
            updatedBy,
            name,
            text,
            color.orEmpty(),
            MarkTags.encode(tags),
            deleted.toString(),
            deletedBy.orEmpty(),
        ).joinToString("|")
}

object TemplateMerge {
    fun merge(local: MarkTemplate, remote: MarkTemplate): MarkTemplate {
        require(local.id == remote.id) { "Cannot merge different templates" }
        val content = pick(local, remote) { it.contentUpdatedAt }
        val status = pick(local, remote) { it.statusUpdatedAt }
        return MarkTemplate(
            id = local.id,
            name = content.name,
            text = content.text,
            color = content.color,
            tags = content.tags,
            createdAt = if (local.createdAt <= remote.createdAt) local.createdAt else remote.createdAt,
            contentUpdatedAt = content.contentUpdatedAt,
            statusUpdatedAt = status.statusUpdatedAt,
            createdBy = if (local.createdAt <= remote.createdAt) local.createdBy else remote.createdBy,
            updatedBy = if (content.contentUpdatedAt >= status.statusUpdatedAt) {
                content.updatedBy
            } else {
                status.updatedBy
            },
            deleted = status.deleted,
            deletedBy = status.deletedBy,
            deletedAt = status.deletedAt,
        )
    }

    private fun pick(
        local: MarkTemplate,
        remote: MarkTemplate,
        time: (MarkTemplate) -> Instant,
    ): MarkTemplate {
        val lt = time(local)
        val rt = time(remote)
        return when {
            lt > rt -> local
            rt > lt -> remote
            local.tieBreakKey() >= remote.tieBreakKey() -> local
            else -> remote
        }
    }
}
