package com.markq.data.remote

import com.markq.core.EntryMerge
import com.markq.core.MarkEntry
import com.markq.core.SyncErrors
import com.markq.data.local.AttachmentDao
import com.markq.data.local.AttachmentEntity
import com.markq.data.local.AttachmentStore
import com.markq.data.local.CursorDao
import com.markq.data.local.EntryDao
import com.markq.data.local.SettingsStore
import com.markq.data.local.SyncCursorEntity
import com.markq.data.local.toEntity
import java.time.Instant
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
)

class SyncEngine(
    private val dav: WebDavClient,
    private val entries: EntryDao,
    private val attachments: AttachmentDao,
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
        if (!cfg.isConfigured) return Result.success(Unit)
        val config = cfg.toWebDavConfig()
        _state.value = _state.value.copy(running = true, error = null)
        try {
            withContext(Dispatchers.IO) {
                dav.ensureLayout(config)
                pull(config)
                push(config)
            }
            val now = System.currentTimeMillis()
            settings.setLastSync(now)
            _state.value = SyncUiState(running = false, error = null, lastSuccessEpochMs = now)
            Result.success(Unit)
        } catch (e: Exception) {
            val silent = (e is WebDavException && e.code == 412) || SyncErrors.isSilent(e)
            _state.value = _state.value.copy(
                running = false,
                error = if (silent) null else (e.message ?: e.javaClass.simpleName),
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
            pullAttachments(config, remote)
        }
    }

    private suspend fun pullAttachments(config: WebDavConfig, remote: MarkEntry) {
        dav.ensurePath(config, WebDavClient.join(config.baseUrl, "files", remote.id))
        for (att in remote.attachments) {
            val local = files.file(remote.id, att.id)
            val existing = attachments.forEntry(remote.id).firstOrNull { it.id == att.id }
            val hashOk = local.exists() && existing?.sha256 == att.sha256 && att.sha256.isNotBlank()
            if (hashOk) continue
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
                        localPath = existing?.localPath ?: files.file(merged.id, att.id).takeIf { it.exists() }?.absolutePath,
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
                pushAttachments(config, row)
                val url = dav.entryUrl(config, model.id)
                val body = json.encodeToString(RemoteEntry.serializer(), RemoteEntry.from(model))
                    .toByteArray(Charsets.UTF_8)
                val etag = putWithRetry(config, url, body, "application/json; charset=utf-8", row.entry.remoteEtag) {
                    val got = dav.get(config, url)
                    val remote = json.decodeFromString(RemoteEntry.serializer(), got.bytes.toString(Charsets.UTF_8)).toModel()
                    val merged = EntryMerge.merge(model, remote)
                    entries.upsert(merged.toEntity(dirty = true, remoteEtag = got.etag))
                    json.encodeToString(RemoteEntry.serializer(), RemoteEntry.from(merged)).toByteArray(Charsets.UTF_8) to got.etag
                }
                val path = WebDavClient.cursorKey(url)
                entries.markPushed(model.id, dirty = false, etag = etag)
                cursors.upsert(SyncCursorEntity(path, etag, Instant.now().toString()))
            } catch (e: WebDavException) {
                if (e.code == 412) continue else throw e
            }
        }
    }

    private suspend fun pushAttachments(config: WebDavConfig, row: com.markq.data.local.EntryWithAttachments) {
        dav.ensurePath(config, WebDavClient.join(config.baseUrl, "files", row.entry.id))
        for (att in row.attachments.filter { it.dirty }) {
            try {
                val bytes = files.readBytes(row.entry.id, att.id) ?: continue
                val url = dav.attachmentUrl(config, row.entry.id, att.id)
                val etag = putWithRetry(config, url, bytes, att.mime.ifBlank { "application/octet-stream" }, att.remoteEtag) {
                    bytes to null
                }
                attachments.markPushed(att.id, etag)
            } catch (e: WebDavException) {
                if (e.code == 412) continue else throw e
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
            return dav.put(config, url, bytes, contentType, ifMatch)
        } catch (e: WebDavException) {
            if (e.code != 412) throw e
        }
        val (retryBytes, retryMatch) = try {
            onConflict()
        } catch (e: WebDavException) {
            if (e.code == 404) bytes to null else throw e
        }
        try {
            return dav.put(config, url, retryBytes, contentType, retryMatch)
        } catch (e: WebDavException) {
            if (e.code != 412) throw e
        }
        return dav.put(config, url, retryBytes, contentType, ifMatch = null)
    }
}
