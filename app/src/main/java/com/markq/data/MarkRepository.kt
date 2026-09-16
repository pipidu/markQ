package com.markq.data

import android.content.Context
import android.net.Uri
import com.markq.core.MarkAttachment
import com.markq.core.MarkEntry
import com.markq.data.local.AppSettings
import com.markq.data.local.AttachmentEntity
import com.markq.data.local.AttachmentStore
import com.markq.data.local.EntryWithAttachments
import com.markq.data.local.MarkDatabase
import com.markq.data.local.SettingsStore
import com.markq.data.local.toEntity
import com.markq.data.remote.SyncEngine
import com.markq.data.remote.SyncUiState
import com.markq.data.remote.WebDavConfig
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class MarkRepository(
    private val db: MarkDatabase,
    private val settings: SettingsStore,
    private val files: AttachmentStore,
    private val sync: SyncEngine,
    private val appContext: Context,
) {
    val settingsFlow: Flow<AppSettings> = settings.settings
    val syncState: StateFlow<SyncUiState> = sync.state
    val entries: Flow<List<EntryWithAttachments>> = db.entries().observeActive()

    suspend fun currentSettings(): AppSettings = settings.current()

    fun observeEntry(id: String): Flow<EntryWithAttachments?> = db.entries().observeById(id)

    suspend fun getEntry(id: String): EntryWithAttachments? = db.entries().get(id)

    suspend fun saveServer(nickname: String, url: String, username: String, password: String) {
        sync.testConnection(WebDavConfig(url.trim(), username, password))
        settings.saveServer(nickname, url, username, password)
        sync.sync()
    }

    suspend fun saveNickname(nickname: String) {
        settings.saveNickname(nickname)
    }

    suspend fun sync() = sync.sync()

    suspend fun create(
        text: String,
        occurredAt: Instant,
        attachments: List<PendingAttachment>,
    ) {
        val cfg = settings.current()
        val now = Instant.now()
        val id = UUID.randomUUID().toString()
        val stored = attachments.map { pending ->
            val attachId = UUID.randomUUID().toString()
            val copied = files.copyFromUri(appContext, pending.uri, id, attachId)
            val kind = if (pending.mime.startsWith("image/")) "image" else "file"
            AttachmentEntity(
                id = attachId,
                entryId = id,
                name = pending.name,
                mime = pending.mime.ifBlank { "application/octet-stream" },
                kind = kind,
                size = copied.size,
                sha256 = copied.sha256,
                localPath = copied.file.absolutePath,
                dirty = true,
                remoteEtag = null,
            )
        }
        val entry = MarkEntry(
            id = id,
            occurredAt = occurredAt,
            createdAt = now,
            contentUpdatedAt = now,
            statusUpdatedAt = now,
            text = text,
            createdBy = cfg.nickname,
            updatedBy = cfg.nickname,
            attachments = stored.map {
                MarkAttachment(it.id, it.name, it.mime, it.kind, it.size, it.sha256)
            },
        )
        db.entries().upsert(entry.toEntity(dirty = true, remoteEtag = null))
        stored.forEach { db.attachments().upsert(it) }
        sync.sync()
    }

    suspend fun complete(id: String) {
        val row = db.entries().get(id) ?: return
        if (row.entry.completed) return
        val cfg = settings.current()
        val now = Instant.now()
        val model = row.toModel().copy(
            completed = true,
            completedBy = cfg.nickname,
            completedAt = now,
            statusUpdatedAt = now,
            updatedBy = cfg.nickname,
        )
        db.entries().upsert(model.toEntity(dirty = true, remoteEtag = row.entry.remoteEtag))
        sync.sync()
    }

    suspend fun delete(id: String) {
        val row = db.entries().get(id) ?: return
        val cfg = settings.current()
        val now = Instant.now()
        val model = row.toModel().copy(
            deleted = true,
            deletedBy = cfg.nickname,
            deletedAt = now,
            statusUpdatedAt = now,
            updatedBy = cfg.nickname,
        )
        db.entries().upsert(model.toEntity(dirty = true, remoteEtag = row.entry.remoteEtag))
        sync.sync()
    }

    data class PendingAttachment(
        val uri: Uri,
        val name: String,
        val mime: String,
    )
}
