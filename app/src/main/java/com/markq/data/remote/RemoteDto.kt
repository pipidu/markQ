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
