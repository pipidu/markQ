package com.markq.data

import android.content.Context
import android.net.Uri
import com.markq.R
import com.markq.core.MarkAttachment
import com.markq.core.MarkEntry
import com.markq.core.MarkTemplate
import com.markq.data.local.AppSettings
import com.markq.data.local.AttachmentEntity
import com.markq.data.local.AttachmentStore
import com.markq.data.local.CacheJanitor
import com.markq.data.local.EntryWithAttachments
import com.markq.data.local.MarkDatabase
import com.markq.data.local.SettingsStore
import com.markq.data.local.TemplateEntity
import com.markq.data.local.toEntity
import com.markq.data.remote.SyncEngine
import com.markq.data.remote.SyncUiState
import com.markq.data.remote.WebDavConfig
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine

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
    val templates: Flow<List<TemplateEntity>> = db.templates().observeActive()
    val pendingUploadCount: Flow<Int> = combine(
        db.entries().observeDirtyCount(),
        db.templates().observeDirtyCount(),
    ) { entriesDirty, templatesDirty -> entriesDirty + templatesDirty }

    private val _saveHints = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val saveHints: SharedFlow<String> = _saveHints.asSharedFlow()

    suspend fun currentSettings(): AppSettings = settings.current()

    fun observeEntry(id: String): Flow<EntryWithAttachments?> = db.entries().observeById(id)

    suspend fun getEntry(id: String): EntryWithAttachments? = db.entries().get(id)

    suspend fun getTemplate(id: String): TemplateEntity? = db.templates().get(id)

    suspend fun saveServer(
        nickname: String,
        url: String,
        username: String,
        password: String,
        remoteDir: String,
    ) {
        requireNickname(nickname)
        val config = WebDavConfig(
            baseUrl = com.markq.core.NutstoreDav.collectionUrl(url, remoteDir),
            username = username,
            password = password,
            davRoot = url,
        )
        sync.testConnection(config)
        settings.saveServer(nickname, url, username, password, remoteDir)
        sync.sync()
    }

    suspend fun saveNickname(nickname: String) {
        requireNickname(nickname)
        settings.saveNickname(nickname)
    }

    suspend fun saveTheme(
        barColor: String? = null,
        backgroundColor: String? = null,
        fabColor: String? = null,
    ) {
        settings.saveTheme(
            barColor = barColor?.let { com.markq.core.MarkColor.normalize(it) },
            backgroundColor = backgroundColor?.let { com.markq.core.MarkColor.normalize(it) },
            fabColor = fabColor?.let { com.markq.core.MarkColor.normalize(it) },
        )
    }

    suspend fun setBackgroundSync(enabled: Boolean) {
        settings.setBackgroundSync(enabled)
    }

    suspend fun exportBackup(): File = BackupExport.write(appContext, db, files)

    suspend fun sync() = sync.sync()

    suspend fun create(
        text: String,
        occurredAt: Instant,
        color: String?,
        tags: List<String> = emptyList(),
        attachments: List<PendingAttachment>,
        latitude: Double? = null,
        longitude: Double? = null,
        placeName: String? = null,
    ) {
        val cfg = settings.current()
        requireNickname(cfg.nickname)
        val now = Instant.now()
        val id = UUID.randomUUID().toString()
        val stored = storeNewAttachments(id, attachments)
        val hasPlace = com.markq.core.MarkPlace.hasFix(latitude, longitude)
        val entry = MarkEntry(
            id = id,
            occurredAt = occurredAt,
            createdAt = now,
            contentUpdatedAt = now,
            statusUpdatedAt = now,
            text = text,
            createdBy = cfg.nickname,
            updatedBy = cfg.nickname,
            color = com.markq.core.MarkColor.normalize(color),
            tags = com.markq.core.MarkTags.normalize(tags),
            latitude = if (hasPlace) latitude else null,
            longitude = if (hasPlace) longitude else null,
            placeName = if (hasPlace) placeName?.trim()?.ifBlank { null } else null,
            attachments = stored.map {
                MarkAttachment(it.id, it.name, it.mime, it.kind, it.size, it.sha256)
            },
        )
        db.entries().upsert(entry.toEntity(dirty = true, remoteEtag = null))
        stored.forEach { db.attachments().upsert(it) }
        persistThenSync(id)
    }

    suspend fun update(
        id: String,
        text: String,
        occurredAt: Instant,
        color: String?,
        tags: List<String> = emptyList(),
        keepAttachmentIds: List<String>,
        newAttachments: List<PendingAttachment>,
        latitude: Double? = null,
        longitude: Double? = null,
        placeName: String? = null,
    ) {
        val row = db.entries().get(id) ?: return
        val cfg = settings.current()
        requireNickname(cfg.nickname)
        val now = Instant.now()
        val kept = row.attachments.filter { it.id in keepAttachmentIds }
        val added = storeNewAttachments(id, newAttachments)
        val all = kept + added
        val hasPlace = com.markq.core.MarkPlace.hasFix(latitude, longitude)
        val model = row.toModel().copy(
            text = text,
            occurredAt = occurredAt,
            color = com.markq.core.MarkColor.normalize(color),
            tags = com.markq.core.MarkTags.normalize(tags),
            latitude = if (hasPlace) latitude else null,
            longitude = if (hasPlace) longitude else null,
            placeName = if (hasPlace) placeName?.trim()?.ifBlank { null } else null,
            contentUpdatedAt = now,
            updatedBy = cfg.nickname,
            attachments = all.map {
                MarkAttachment(it.id, it.name, it.mime, it.kind, it.size, it.sha256)
            },
        )
        db.entries().upsert(model.toEntity(dirty = true, remoteEtag = row.entry.remoteEtag))
        if (all.isEmpty()) {
            db.attachments().deleteForEntry(id)
        } else {
            db.attachments().deleteMissing(id, all.map { it.id })
            all.forEach { db.attachments().upsert(it) }
        }
        persistThenSync(id)
    }

    suspend fun complete(id: String) {
        val row = db.entries().get(id) ?: return
        val cfg = settings.current()
        requireNickname(cfg.nickname)
        val now = Instant.now()
        val model = if (row.entry.completed) {
            row.toModel().copy(
                completed = false,
                completedBy = null,
                completedAt = null,
                statusUpdatedAt = now,
                updatedBy = cfg.nickname,
            )
        } else {
            row.toModel().copy(
                completed = true,
                completedBy = cfg.nickname,
                completedAt = now,
                statusUpdatedAt = now,
                updatedBy = cfg.nickname,
            )
        }
        db.entries().upsert(model.toEntity(dirty = true, remoteEtag = row.entry.remoteEtag))
        sync.sync()
    }

    suspend fun delete(id: String) {
        val row = db.entries().get(id) ?: return
        val cfg = settings.current()
        requireNickname(cfg.nickname)
        val now = Instant.now()
        val model = row.toModel().copy(
            deleted = true,
            deletedBy = cfg.nickname,
            deletedAt = now,
            statusUpdatedAt = now,
            updatedBy = cfg.nickname,
        )
        db.entries().upsert(model.toEntity(dirty = true, remoteEtag = row.entry.remoteEtag))
        unlinkLocalAttachments(row)
        runCatching { sync.deleteRemoteFiles(id) }
        persistThenSync(id, hintIfPending = false)
    }

    suspend fun createTemplate(
        name: String,
        text: String,
        color: String?,
        tags: List<String>,
    ) {
        val cfg = settings.current()
        requireNickname(cfg.nickname)
        val now = Instant.now()
        val model = MarkTemplate(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            text = text.trim(),
            color = com.markq.core.MarkColor.normalize(color),
            tags = com.markq.core.MarkTags.normalize(tags),
            createdAt = now,
            contentUpdatedAt = now,
            statusUpdatedAt = now,
            createdBy = cfg.nickname,
            updatedBy = cfg.nickname,
        )
        db.templates().upsert(model.toEntity(dirty = true, remoteEtag = null))
        sync.sync()
    }

    suspend fun updateTemplate(
        id: String,
        name: String,
        text: String,
        color: String?,
        tags: List<String>,
    ) {
        val row = db.templates().get(id) ?: return
        val cfg = settings.current()
        requireNickname(cfg.nickname)
        val now = Instant.now()
        val model = row.toModel().copy(
            name = name.trim(),
            text = text.trim(),
            color = com.markq.core.MarkColor.normalize(color),
            tags = com.markq.core.MarkTags.normalize(tags),
            contentUpdatedAt = now,
            updatedBy = cfg.nickname,
        )
        db.templates().upsert(model.toEntity(dirty = true, remoteEtag = row.remoteEtag))
        sync.sync()
    }

    suspend fun deleteTemplate(id: String) {
        val row = db.templates().get(id) ?: return
        val cfg = settings.current()
        requireNickname(cfg.nickname)
        val now = Instant.now()
        val model = row.toModel().copy(
            deleted = true,
            deletedBy = cfg.nickname,
            deletedAt = now,
            statusUpdatedAt = now,
            updatedBy = cfg.nickname,
        )
        db.templates().upsert(model.toEntity(dirty = true, remoteEtag = row.remoteEtag))
        sync.sync()
    }

    data class PendingAttachment(
        val uri: Uri,
        val name: String,
        val mime: String,
        val compressImage: Boolean = false,
    )

    private fun storeNewAttachments(entryId: String, attachments: List<PendingAttachment>): List<AttachmentEntity> {
        return attachments.map { pending ->
            val attachId = UUID.randomUUID().toString()
            val wantCompress = pending.compressImage
            val copied = files.copyFromUri(appContext, pending.uri, entryId, attachId, compressImage = wantCompress)
            val mime = if (copied.usedWebp) {
                com.markq.core.ImageNames.WEBP_MIME
            } else {
                pending.mime.ifBlank { "application/octet-stream" }
            }
            val name = if (copied.usedWebp) {
                com.markq.core.ImageNames.webpFileName(pending.name)
            } else {
                pending.name
            }
            val kind = if (mime.startsWith("image/") || pending.compressImage) "image" else "file"
            AttachmentEntity(
                id = attachId,
                entryId = entryId,
                name = name,
                mime = mime,
                kind = kind,
                size = copied.size,
                sha256 = copied.sha256,
                localPath = copied.file.absolutePath,
                dirty = true,
                remoteEtag = null,
            )
        }
    }

    suspend fun pruneCache(keepUpdateApk: Boolean = false) {
        val hashes = db.attachments().allHashes().filter { it.isNotBlank() }.toSet()
        CacheJanitor.prune(appContext, files, hashes, keepUpdateApk)
    }

    private suspend fun persistThenSync(entryId: String, hintIfPending: Boolean = true) {
        pruneCache()
        sync.refreshPending()
        sync.sync()
        if (hintIfPending && db.entries().get(entryId)?.entry?.dirty == true) {
            _saveHints.tryEmit(appContext.getString(R.string.saved_locally_pending_upload))
        }
    }

    private suspend fun unlinkLocalAttachments(row: EntryWithAttachments) {
        val keep = db.attachments().hashesExceptEntry(row.entry.id).filter { it.isNotBlank() }.toSet()
        files.deleteEntryFiles(row.entry.id, row.attachments.map { it.sha256 }, keep)
        row.attachments.forEach { att ->
            db.attachments().upsert(att.copy(localPath = null, dirty = false))
        }
    }

    private fun requireNickname(nickname: String) {
        require(nickname.trim().isNotEmpty()) {
            appContext.getString(R.string.error_nickname_required)
        }
    }
}
