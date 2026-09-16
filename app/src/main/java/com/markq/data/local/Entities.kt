package com.markq.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.markq.core.MarkAttachment
import com.markq.core.MarkEntry
import java.time.Instant

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey val id: String,
    val occurredAt: Long,
    val createdAt: Long,
    val contentUpdatedAt: Long,
    val statusUpdatedAt: Long,
    val text: String,
    val createdBy: String,
    val updatedBy: String,
    val completed: Boolean,
    val completedBy: String?,
    val completedAt: Long?,
    val deleted: Boolean,
    val deletedBy: String?,
    val deletedAt: Long?,
    val color: String?,
    val tags: String = "",
    val dirty: Boolean,
    val remoteEtag: String?,
)

@Entity(
    tableName = "attachments",
    indices = [Index("entryId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val name: String,
    val mime: String,
    val kind: String,
    val size: Long,
    val sha256: String,
    val localPath: String?,
    val dirty: Boolean,
    val remoteEtag: String?,
)

@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
    @PrimaryKey val path: String,
    val etag: String?,
    val lastModified: String?,
)

data class EntryWithAttachments(
    @Embedded val entry: EntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val attachments: List<AttachmentEntity>,
) {
    fun toModel(): MarkEntry = MarkEntry(
        id = entry.id,
        occurredAt = Instant.ofEpochMilli(entry.occurredAt),
        createdAt = Instant.ofEpochMilli(entry.createdAt),
        contentUpdatedAt = Instant.ofEpochMilli(entry.contentUpdatedAt),
        statusUpdatedAt = Instant.ofEpochMilli(entry.statusUpdatedAt),
        text = entry.text,
        createdBy = entry.createdBy,
        updatedBy = entry.updatedBy,
        completed = entry.completed,
        completedBy = entry.completedBy,
        completedAt = entry.completedAt?.let(Instant::ofEpochMilli),
        deleted = entry.deleted,
        deletedBy = entry.deletedBy,
        deletedAt = entry.deletedAt?.let(Instant::ofEpochMilli),
        color = entry.color,
        tags = com.markq.core.MarkTags.decode(entry.tags),
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
}

fun MarkEntry.toEntity(dirty: Boolean, remoteEtag: String?): EntryEntity = EntryEntity(
    id = id,
    occurredAt = occurredAt.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    contentUpdatedAt = contentUpdatedAt.toEpochMilli(),
    statusUpdatedAt = statusUpdatedAt.toEpochMilli(),
    text = text,
    createdBy = createdBy,
    updatedBy = updatedBy,
    completed = completed,
    completedBy = completedBy,
    completedAt = completedAt?.toEpochMilli(),
    deleted = deleted,
    deletedBy = deletedBy,
    deletedAt = deletedAt?.toEpochMilli(),
    color = color,
    tags = com.markq.core.MarkTags.encode(tags),
    dirty = dirty,
    remoteEtag = remoteEtag,
)
