package com.markq.data.remote

import com.markq.core.ConditionalPut
import com.markq.core.EntryMerge
import com.markq.core.FileSha256
import com.markq.core.MarkAttachment
import com.markq.core.MarkEntry
import com.markq.core.MarkTemplate
import com.markq.core.SyncConflict
import com.markq.core.SyncErrors
import com.markq.core.TemplateMerge
import com.markq.core.TombstoneGc
import com.markq.data.local.AttachmentDao
import com.markq.data.local.AttachmentEntity
import com.markq.data.local.AttachmentStore
import com.markq.data.local.CursorDao
import com.markq.data.local.EntryDao
import com.markq.data.local.SettingsStore
import com.markq.data.local.SyncCursorEntity
import com.markq.data.local.TemplateDao
import com.markq.data.local.toEntity
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl

data class SyncUiState(
    val running: Boolean = false,
    val error: String? = null,
    val lastSuccessEpochMs: Long = 0L,
    val pendingCount: Int = 0,
)

class SyncEngine(
    private val dav: WebDavClient,
    private val entries: EntryDao,
    private val attachments: AttachmentDao,
    private val templates: TemplateDao,
    private val cursors: CursorDao,
    private val files: AttachmentStore,
    private val settings: SettingsStore,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    suspend fun testConnection(config: WebDavConfig) = withContext(Dispatchers.IO) {
        dav.ensureLayout(config)
        dav.probe(config)
    }

    suspend fun sync(): Result<Unit> = mutex.withLock {
        val cfg = settings.current()
        if (!cfg.isConfigured) {
            refreshPending()
            return Result.success(Unit)
        }
        val config = cfg.toWebDavConfig()
        _state.value = _state.value.copy(running = true, error = null, lastSuccessEpochMs = cfg.lastSyncEpochMs)
        try {
            withContext(Dispatchers.IO) {
                dav.ensureLayout(config)
                pull(config)
                pullTemplates(config)
                push(config)
                pushTemplates(config)
                gcTombstones(config)
            }
            val now = System.currentTimeMillis()
            settings.setLastSync(now)
            val pending = pendingCount()
            _state.value = SyncUiState(
                running = false,
                error = null,
                lastSuccessEpochMs = now,
                pendingCount = pending,
            )
            Result.success(Unit)
        } catch (e: Exception) {
            val silent = (e is WebDavException && e.code == 412) || SyncErrors.isSilent(e)
            _state.value = _state.value.copy(
                running = false,
                error = if (silent) null else (e.message ?: e.javaClass.simpleName),
                pendingCount = pendingCount(),
            )
            if (silent) Result.success(Unit) else Result.failure(e)
        }
    }

    private suspend fun pull(config: WebDavConfig) {
        val listing = dav.propfind(config, dav.entriesUrl(config), depth = 1)
        for (resource in listing) {
            if (resource.isCollection) continue
            val name = resource.url.pathSegments.lastOrNull().orEmpty()
            if (!name.endsWith(".json")) continue
            val path = WebDavClient.cursorKey(resource.url)
            val cursor = cursors.get(path)
            if (!WebDavClient.needsDownload(cursor?.etag, cursor?.lastModified, resource)) {
                continue
            }
            val got = dav.get(config, resource.url)
            val remote = json.decodeFromString(RemoteEntry.serializer(), got.bytes.toString(Charsets.UTF_8)).toModel()
            mergeRemoteEntry(remote, got.etag ?: resource.etag)
            cursors.upsert(SyncCursorEntity(path, got.etag ?: resource.etag, got.lastModified ?: resource.lastModified))
            if (!remote.deleted) {
                pullAttachments(config, remote)
            }
        }
    }

    private suspend fun pullAttachments(config: WebDavConfig, remote: MarkEntry) {
        dav.ensurePath(config, WebDavClient.join(config.baseUrl, "files", remote.id))
        for (att in remote.attachments) {
            val local = files.locate(remote.id, att.id, att.sha256)
            val existing = attachments.forEntry(remote.id).firstOrNull { it.id == att.id }
            val hashOk = local.exists() && (existing?.sha256 == att.sha256 || files.blob(att.sha256).exists()) && att.sha256.isNotBlank()
            if (hashOk) {
                if (existing == null || existing.localPath != local.absolutePath || existing.sha256 != att.sha256) {
                    attachments.upsert(
                        AttachmentEntity(
                            id = att.id,
                            entryId = remote.id,
                            name = att.name,
                            mime = att.mime,
                            kind = att.kind,
                            size = att.size,
                            sha256 = att.sha256,
                            localPath = local.absolutePath,
                            dirty = false,
                            remoteEtag = existing?.remoteEtag,
                        ),
                    )
                }
                continue
            }
            val url = dav.attachmentUrl(config, remote.id, att.id)
            val got = try {
                dav.get(config, url)
            } catch (e: WebDavException) {
                if (e.code == 404) continue else throw e
            }
            val stored = files.writeBytes(remote.id, att.id, got.bytes)
            attachments.upsert(
                AttachmentEntity(
                    id = att.id,
                    entryId = remote.id,
                    name = att.name,
                    mime = att.mime,
                    kind = att.kind,
                    size = stored.size,
                    sha256 = stored.sha256,
                    localPath = stored.file.absolutePath,
                    dirty = false,
                    remoteEtag = got.etag,
                ),
            )
        }
    }

    private suspend fun mergeRemoteEntry(remote: MarkEntry, etag: String?) {
        val localRow = entries.get(remote.id)
        val merged = if (localRow == null) {
            remote
        } else {
            EntryMerge.merge(localRow.toModel(), remote)
        }
        val localDirty = localRow?.entry?.dirty == true
        val stillDirty = localDirty && merged != remote
        entries.upsert(merged.toEntity(dirty = stillDirty, remoteEtag = etag))
        val keep = merged.attachments.map { it.id }
        if (keep.isEmpty()) {
            attachments.deleteForEntry(merged.id)
        } else {
            attachments.deleteMissing(merged.id, keep)
            merged.attachments.forEach { att ->
                val existing = attachments.forEntry(merged.id).firstOrNull { it.id == att.id }
                attachments.upsert(
                    AttachmentEntity(
                        id = att.id,
                        entryId = merged.id,
                        name = att.name,
                        mime = att.mime,
                        kind = att.kind,
                        size = att.size,
                        sha256 = att.sha256,
                        localPath = existing?.localPath
                            ?: files.locate(merged.id, att.id, att.sha256).takeIf { it.exists() }?.absolutePath,
                        dirty = existing?.dirty == true,
                        remoteEtag = existing?.remoteEtag,
                    ),
                )
            }
        }
    }

    private suspend fun push(config: WebDavConfig) {
        for (row in entries.getDirty()) {
            try {
                val model = row.toModel()
                var merged = model
                val url = dav.entryUrl(config, model.id)
                val body = json.encodeToString(RemoteEntry.serializer(), RemoteEntry.from(model))
                    .toByteArray(Charsets.UTF_8)
                val etag = putWithRetry(config, url, body, "application/json; charset=utf-8", row.entry.remoteEtag) {
                    val got = dav.get(config, url)
                    val remote = json.decodeFromString(RemoteEntry.serializer(), got.bytes.toString(Charsets.UTF_8)).toModel()
                    val localNow = entries.get(model.id)?.toModel() ?: model
                    merged = EntryMerge.merge(localNow, remote)
                    entries.upsert(merged.toEntity(dirty = true, remoteEtag = got.etag))
                    json.encodeToString(RemoteEntry.serializer(), RemoteEntry.from(merged)).toByteArray(Charsets.UTF_8) to got.etag
                }
                entries.upsert(merged.toEntity(dirty = true, remoteEtag = etag))
                var cursorEtag = etag
                when (SyncConflict.filesFollowMergedEntry(model.deleted, merged.deleted)) {
                    SyncConflict.FileAction.DeleteRemoteFiles -> {
                        runCatching { dav.deleteCollection(config, dav.filesEntryUrl(config, model.id)) }
                    }
                    SyncConflict.FileAction.UploadAttachments -> {
                        val changed = pushAttachments(config, model.id)
                        if (changed) {
                            val latestRow = entries.get(model.id) ?: continue
                            val latest = latestRow.toModel()
                            merged = latest
                            val latestBody = json.encodeToString(RemoteEntry.serializer(), RemoteEntry.from(latest))
                                .toByteArray(Charsets.UTF_8)
                            val match = latestRow.entry.remoteEtag?.takeIf { it.isNotBlank() }
                                ?: dav.get(config, url).etag?.takeIf { it.isNotBlank() }
                                ?: throw WebDavException("Precondition failed", 412)
                            cursorEtag = putWithRetry(
                                config,
                                url,
                                latestBody,
                                "application/json; charset=utf-8",
                                match,
                            ) {
                                val got = dav.get(config, url)
                                val remote = json.decodeFromString(RemoteEntry.serializer(), got.bytes.toString(Charsets.UTF_8)).toModel()
                                val localNow = entries.get(model.id)?.toModel() ?: latest
                                merged = EntryMerge.merge(localNow, remote)
                                entries.upsert(merged.toEntity(dirty = true, remoteEtag = got.etag))
                                json.encodeToString(RemoteEntry.serializer(), RemoteEntry.from(merged))
                                    .toByteArray(Charsets.UTF_8) to got.etag
                            }
                        }
                    }
                }
                val path = WebDavClient.cursorKey(url)
                entries.markPushed(model.id, dirty = false, etag = cursorEtag)
                cursors.upsert(SyncCursorEntity(path, cursorEtag, Instant.now().toString()))
            } catch (e: WebDavException) {
                if (e.code == 412) continue else throw e
            }
        }
    }

    /**
     * Uploads dirty blobs for an entry the merge kept alive.
     * Returns true when a file conflict added a new attachment and the entry JSON must be pushed again.
     */
    private suspend fun pushAttachments(config: WebDavConfig, entryId: String): Boolean {
        val current = entries.get(entryId) ?: return false
        if (current.entry.deleted) return false
        dav.ensurePath(config, WebDavClient.join(config.baseUrl, "files", entryId))
        val liveIds = current.toModel().attachments.map { it.id }.toSet()
        var changed = false
        for (att in attachments.forEntry(entryId).filter { it.dirty && it.id in liveIds }) {
            val bytes = files.readBytes(entryId, att.id, att.sha256) ?: continue
            val url = dav.attachmentUrl(config, entryId, att.id)
            val contentType = att.mime.ifBlank { "application/octet-stream" }
            try {
                val etag = dav.put(config, url, bytes, contentType, att.remoteEtag)
                attachments.markPushed(att.id, etag)
            } catch (e: WebDavException) {
                if (e.code != 412) throw e
                if (resolveAttachmentConflict(config, entryId, att, bytes, url, contentType)) {
                    changed = true
                }
            }
        }
        return changed
    }

    /**
     * A 412 on an attachment must not upload our bytes onto that same URL.
     * Same content adopts the remote etag. Different content keeps the remote
     * bytes and stores ours under a new attachment id.
     */
    private suspend fun resolveAttachmentConflict(
        config: WebDavConfig,
        entryId: String,
        att: com.markq.data.local.AttachmentEntity,
        localBytes: ByteArray,
        url: HttpUrl,
        contentType: String,
    ): Boolean {
        val got = try {
            dav.get(config, url)
        } catch (e: WebDavException) {
            if (e.code != 404) throw e
            val outcome = SyncConflict.resolveAttachment412(
                localSha256 = att.sha256,
                remoteSha256 = null,
                remoteEtag = null,
                remoteMissing = true,
                forkId = "",
            )
            if (outcome !is SyncConflict.AttachmentConflict.UploadAsNew) {
                throw WebDavException("Precondition failed", 412)
            }
            val etag = dav.put(config, url, localBytes, contentType, ifMatch = null)
            attachments.markPushed(att.id, etag)
            return false
        }
        val remoteSha = FileSha256.of(got.bytes)
        val forkId = UUID.randomUUID().toString()
        when (
            val outcome = SyncConflict.resolveAttachment412(
                localSha256 = att.sha256,
                remoteSha256 = remoteSha,
                remoteEtag = got.etag,
                remoteMissing = false,
                forkId = forkId,
            )
        ) {
            is SyncConflict.AttachmentConflict.AdoptRemote -> {
                attachments.markPushed(att.id, outcome.etag ?: got.etag)
                return false
            }
            SyncConflict.AttachmentConflict.UploadAsNew -> {
                throw WebDavException("Precondition failed", 412)
            }
            is SyncConflict.AttachmentConflict.KeepRemoteAndForkLocal -> {
                val storedRemote = files.writeBytes(entryId, att.id, got.bytes)
                attachments.upsert(
                    att.copy(
                        size = storedRemote.size,
                        sha256 = storedRemote.sha256,
                        localPath = storedRemote.file.absolutePath,
                        dirty = false,
                        remoteEtag = got.etag,
                    ),
                )
                val storedLocal = files.writeBytes(entryId, outcome.forkId, localBytes)
                val fork = com.markq.data.local.AttachmentEntity(
                    id = outcome.forkId,
                    entryId = entryId,
                    name = att.name,
                    mime = att.mime,
                    kind = att.kind,
                    size = storedLocal.size,
                    sha256 = storedLocal.sha256,
                    localPath = storedLocal.file.absolutePath,
                    dirty = true,
                    remoteEtag = null,
                )
                attachments.upsert(fork)
                val row = entries.get(entryId) ?: return true
                val model = row.toModel()
                val updatedExisting = model.attachments.map { item ->
                    if (item.id == att.id) {
                        item.copy(size = storedRemote.size, sha256 = storedRemote.sha256)
                    } else {
                        item
                    }
                }
                val forked = MarkAttachment(
                    id = outcome.forkId,
                    name = att.name,
                    mime = att.mime,
                    kind = att.kind,
                    size = storedLocal.size,
                    sha256 = storedLocal.sha256,
                )
                val stamp = maxOf(Instant.now(), model.contentUpdatedAt.plusMillis(1))
                entries.upsert(
                    model.copy(
                        attachments = updatedExisting + forked,
                        contentUpdatedAt = stamp,
                    ).toEntity(dirty = true, remoteEtag = row.entry.remoteEtag),
                )
                val forkUrl = dav.attachmentUrl(config, entryId, outcome.forkId)
                try {
                    val forkEtag = dav.put(config, forkUrl, localBytes, contentType, ifMatch = null)
                    attachments.markPushed(outcome.forkId, forkEtag)
                } catch (e: WebDavException) {
                    if (e.code == 412) throw WebDavException("Precondition failed", 412)
                    throw e
                }
                return true
            }
        }
    }

    private suspend fun putWithRetry(
        config: WebDavConfig,
        url: HttpUrl,
        bytes: ByteArray,
        contentType: String,
        ifMatch: String?,
        onConflict: suspend () -> Pair<ByteArray, String?>,
    ): String? {
        try {
            return ConditionalPut.run(
                initial = bytes,
                ifMatch = ifMatch,
                put = { payload, match ->
                    try {
                        dav.put(config, url, payload, contentType, match)
                    } catch (e: WebDavException) {
                        if (e.code == 412) throw ConditionalPut.PreconditionFailed() else throw e
                    }
                },
                onConflict = {
                    try {
                        onConflict()
                    } catch (e: WebDavException) {
                        if (e.code == 404) throw ConditionalPut.RemoteMissing() else throw e
                    }
                },
            )
        } catch (_: ConditionalPut.PreconditionFailed) {
            throw WebDavException("Precondition failed", 412)
        }
    }

    suspend fun deleteRemoteFiles(entryId: String) {
        val cfg = settings.current()
        if (!cfg.isConfigured) return
        val config = cfg.toWebDavConfig()
        withContext(Dispatchers.IO) {
            runCatching { dav.deleteCollection(config, dav.filesEntryUrl(config, entryId)) }
        }
    }

    suspend fun refreshPending() {
        _state.value = _state.value.copy(pendingCount = pendingCount())
    }

    private suspend fun pendingCount(): Int =
        entries.getDirty().size + templates.getDirty().size

    private suspend fun gcTombstones(config: WebDavConfig) {
        val now = System.currentTimeMillis()
        for (row in entries.getDeleted()) {
            if (row.entry.dirty) continue
            if (!TombstoneGc.isExpired(row.entry.deleted, row.entry.deletedAt, now)) continue
            val jsonUrl = dav.entryUrl(config, row.entry.id)
            if (!tryDelete(config, jsonUrl)) continue
            runCatching { dav.deleteCollection(config, dav.filesEntryUrl(config, row.entry.id)) }
            cursors.delete(WebDavClient.cursorKey(jsonUrl))
            val keep = attachments.hashesExceptEntry(row.entry.id).filter { it.isNotBlank() }.toSet()
            files.deleteEntryFiles(row.entry.id, row.attachments.map { it.sha256 }, keep)
            attachments.deleteForEntry(row.entry.id)
            entries.hardDelete(row.entry.id)
        }
        for (row in templates.getDeleted()) {
            if (row.dirty) continue
            if (!TombstoneGc.isExpired(row.deleted, row.deletedAt, now)) continue
            val jsonUrl = dav.templateUrl(config, row.id)
            if (!tryDelete(config, jsonUrl)) continue
            cursors.delete(WebDavClient.cursorKey(jsonUrl))
            templates.hardDelete(row.id)
        }
    }

    private fun tryDelete(config: WebDavConfig, url: HttpUrl): Boolean {
        return try {
            dav.delete(config, url)
            true
        } catch (e: WebDavException) {
            e.code == 404
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun pullTemplates(config: WebDavConfig) {
        val listing = try {
            dav.propfind(config, dav.templatesUrl(config), depth = 1)
        } catch (e: WebDavException) {
            if (e.code == 404) return else throw e
        }
        for (resource in listing) {
            if (resource.isCollection) continue
            val name = resource.url.pathSegments.lastOrNull().orEmpty()
            if (!name.endsWith(".json")) continue
            val path = WebDavClient.cursorKey(resource.url)
            val cursor = cursors.get(path)
            if (!WebDavClient.needsDownload(cursor?.etag, cursor?.lastModified, resource)) {
                continue
            }
            val got = dav.get(config, resource.url)
            val remote = json.decodeFromString(RemoteTemplate.serializer(), got.bytes.toString(Charsets.UTF_8)).toModel()
            mergeRemoteTemplate(remote, got.etag ?: resource.etag)
            cursors.upsert(SyncCursorEntity(path, got.etag ?: resource.etag, got.lastModified ?: resource.lastModified))
        }
    }

    private suspend fun mergeRemoteTemplate(remote: MarkTemplate, etag: String?) {
        val localRow = templates.get(remote.id)
        val merged = if (localRow == null) {
            remote
        } else {
            TemplateMerge.merge(localRow.toModel(), remote)
        }
        val localDirty = localRow?.dirty == true
        val stillDirty = localDirty && merged != remote
        templates.upsert(merged.toEntity(dirty = stillDirty, remoteEtag = etag))
    }

    private suspend fun pushTemplates(config: WebDavConfig) {
        dav.ensurePath(config, dav.templatesUrl(config))
        for (row in templates.getDirty()) {
            try {
                val model = row.toModel()
                val url = dav.templateUrl(config, model.id)
                val body = json.encodeToString(RemoteTemplate.serializer(), RemoteTemplate.from(model))
                    .toByteArray(Charsets.UTF_8)
                val etag = putWithRetry(config, url, body, "application/json; charset=utf-8", row.remoteEtag) {
                    val got = dav.get(config, url)
                    val remote = json.decodeFromString(RemoteTemplate.serializer(), got.bytes.toString(Charsets.UTF_8)).toModel()
                    val merged = TemplateMerge.merge(model, remote)
                    templates.upsert(merged.toEntity(dirty = true, remoteEtag = got.etag))
                    json.encodeToString(RemoteTemplate.serializer(), RemoteTemplate.from(merged)).toByteArray(Charsets.UTF_8) to got.etag
                }
                val path = WebDavClient.cursorKey(url)
                templates.markPushed(model.id, dirty = false, etag = etag)
                cursors.upsert(SyncCursorEntity(path, etag, Instant.now().toString()))
            } catch (e: WebDavException) {
                if (e.code == 412) continue else throw e
            }
        }
    }
}
