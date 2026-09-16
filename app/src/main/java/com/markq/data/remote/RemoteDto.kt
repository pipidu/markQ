package com.markq.data.remote

import com.markq.core.IsoTime
import com.markq.core.MarkAttachment
import com.markq.core.MarkEntry
import kotlinx.serialization.Serializable

@Serializable
data class RemoteAttachment(
    val id: String,
    val name: String,
    val mime: String,
    val kind: String,
    val size: Long = 0,
    val sha256: String = "",
)

@Serializable
data class RemoteEntry(
    val id: String,
    val schemaVersion: Int = 1,
    val occurredAt: String,
    val createdAt: String,
    val contentUpdatedAt: String,
    val statusUpdatedAt: String,
    val text: String,
    val createdBy: String,
    val updatedBy: String,
    val completed: Boolean = false,
    val completedBy: String? = null,
    val completedAt: String? = null,
    val deleted: Boolean = false,
    val deletedBy: String? = null,
    val deletedAt: String? = null,
    val color: String? = null,
    val tags: List<String> = emptyList(),
    val attachments: List<RemoteAttachment> = emptyList(),
) {
    fun toModel(): MarkEntry = MarkEntry(
        id = id,
        occurredAt = IsoTime.parseRequired(occurredAt),
        createdAt = IsoTime.parseRequired(createdAt),
        contentUpdatedAt = IsoTime.parseRequired(contentUpdatedAt),
        statusUpdatedAt = IsoTime.parseRequired(statusUpdatedAt),
        text = text,
        createdBy = createdBy,
        updatedBy = updatedBy,
        completed = completed,
        completedBy = completedBy,
        completedAt = IsoTime.parse(completedAt),
        deleted = deleted,
        deletedBy = deletedBy,
        deletedAt = IsoTime.parse(deletedAt),
        color = color,
        tags = com.markq.core.MarkTags.normalize(tags),
        attachments = attachments.map {
            MarkAttachment(
                id = it.id,
                name = it.name,
                mime = it.mime,
                kind = it.kind,
                size = it.size,
                sha256 = it.sha256,
            )
        },
    )

    companion object {
        fun from(entry: MarkEntry): RemoteEntry = RemoteEntry(
            id = entry.id,
            occurredAt = IsoTime.format(entry.occurredAt),
            createdAt = IsoTime.format(entry.createdAt),
            contentUpdatedAt = IsoTime.format(entry.contentUpdatedAt),
            statusUpdatedAt = IsoTime.format(entry.statusUpdatedAt),
            text = entry.text,
            createdBy = entry.createdBy,
            updatedBy = entry.updatedBy,
            completed = entry.completed,
            completedBy = entry.completedBy,
            completedAt = entry.completedAt?.let(IsoTime::format),
            deleted = entry.deleted,
            deletedBy = entry.deletedBy,
            deletedAt = entry.deletedAt?.let(IsoTime::format),
            color = entry.color,
            tags = com.markq.core.MarkTags.normalize(entry.tags),
            attachments = entry.attachments.map {
                RemoteAttachment(
                    id = it.id,
                    name = it.name,
                    mime = it.mime,
                    kind = it.kind,
                    size = it.size,
                    sha256 = it.sha256,
                )
            },
        )
    }
}

@Serializable
data class RemoteTemplate(
    val id: String,
    val schemaVersion: Int = 1,
    val name: String = "",
    val text: String = "",
    val color: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: String,
    val contentUpdatedAt: String,
    val statusUpdatedAt: String,
    val createdBy: String,
    val updatedBy: String,
    val deleted: Boolean = false,
    val deletedBy: String? = null,
    val deletedAt: String? = null,
) {
    fun toModel(): com.markq.core.MarkTemplate = com.markq.core.MarkTemplate(
        id = id,
        name = name,
        text = text,
        color = color,
        tags = com.markq.core.MarkTags.normalize(tags),
        createdAt = IsoTime.parseRequired(createdAt),
        contentUpdatedAt = IsoTime.parseRequired(contentUpdatedAt),
        statusUpdatedAt = IsoTime.parseRequired(statusUpdatedAt),
        createdBy = createdBy,
        updatedBy = updatedBy,
        deleted = deleted,
        deletedBy = deletedBy,
        deletedAt = IsoTime.parse(deletedAt),
    )

    companion object {
        fun from(template: com.markq.core.MarkTemplate): RemoteTemplate = RemoteTemplate(
            id = template.id,
            name = template.name,
            text = template.text,
            color = template.color,
            tags = com.markq.core.MarkTags.normalize(template.tags),
            createdAt = IsoTime.format(template.createdAt),
            contentUpdatedAt = IsoTime.format(template.contentUpdatedAt),
            statusUpdatedAt = IsoTime.format(template.statusUpdatedAt),
            createdBy = template.createdBy,
            updatedBy = template.updatedBy,
            deleted = template.deleted,
            deletedBy = template.deletedBy,
            deletedAt = template.deletedAt?.let(IsoTime::format),
        )
    }
}
