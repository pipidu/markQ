package com.markq.ui.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markq.core.MarkColor
import com.markq.core.SyncErrors
import com.markq.data.MarkRepository
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DraftAttachment(
    val key: String,
    val uri: Uri,
    val name: String,
    val mime: String,
    val existingId: String? = null,
)

data class EditorUiState(
    val entryId: String? = null,
    val text: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now(),
    val color: String? = null,
    val createdBy: String? = null,
    val attachments: List<DraftAttachment> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val loaded: Boolean = false,
)

class EditorViewModel(
    private val repo: MarkRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    fun load(entryId: String?) {
        if (_state.value.loaded) return
        if (entryId.isNullOrBlank()) {
            _state.update { it.copy(loaded = true) }
            return
        }
        viewModelScope.launch {
            val row = repo.getEntry(entryId)
            if (row == null) {
                _state.update { it.copy(loaded = true) }
                return@launch
            }
            val zone = Instant.ofEpochMilli(row.entry.occurredAt).atZone(ZoneId.systemDefault())
            _state.update {
                it.copy(
                    entryId = entryId,
                    text = row.entry.text,
                    date = zone.toLocalDate(),
                    time = zone.toLocalTime(),
                    color = row.entry.color,
                    createdBy = row.entry.createdBy,
                    attachments = row.attachments.map { att ->
                        DraftAttachment(
                            key = att.id,
                            uri = att.localPath?.let { path -> Uri.fromFile(File(path)) } ?: Uri.EMPTY,
                            name = att.name,
                            mime = att.mime,
                            existingId = att.id,
                        )
                    },
                    loaded = true,
                )
            }
        }
    }

    fun setText(value: String) = _state.update { it.copy(text = value) }
    fun setDate(value: LocalDate) = _state.update { it.copy(date = value) }
    fun setTime(value: LocalTime) = _state.update { it.copy(time = value) }
    fun setColor(value: String?) = _state.update { it.copy(color = MarkColor.normalize(value)) }

    fun addUris(context: Context, uris: List<Uri>) {
        val pending = File(context.cacheDir, "pending").apply { mkdirs() }
        val extras = uris.map { uri ->
            val name = queryName(context, uri)
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val dest = File(pending, UUID.randomUUID().toString())
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to read attached file" }
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            DraftAttachment(
                key = UUID.randomUUID().toString(),
                uri = Uri.fromFile(dest),
                name = name,
                mime = mime,
            )
        }
        _state.update { it.copy(attachments = it.attachments + extras) }
    }

    fun removeAttachment(key: String) {
        _state.update { it.copy(attachments = it.attachments.filterNot { att -> att.key == key }) }
    }

    fun save() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val occurred = s.date.atTime(s.time).atZone(ZoneId.systemDefault()).toInstant()
            runCatching {
                val keep = s.attachments.mapNotNull { it.existingId }
                val fresh = s.attachments.filter { it.existingId == null }.map {
                    MarkRepository.PendingAttachment(it.uri, it.name, it.mime)
                }
                val id = s.entryId
                if (id.isNullOrBlank()) {
                    repo.create(
                        text = s.text.trim(),
                        occurredAt = occurred,
                        color = s.color,
                        attachments = s.attachments.map {
                            MarkRepository.PendingAttachment(it.uri, it.name, it.mime)
                        },
                    )
                } else {
                    repo.update(
                        id = id,
                        text = s.text.trim(),
                        occurredAt = occurred,
                        color = s.color,
                        keepAttachmentIds = keep,
                        newAttachments = fresh,
                    )
                }
            }.onSuccess {
                _state.update { it.copy(busy = false, saved = true) }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        busy = false,
                        error = if (SyncErrors.isSilent(err)) null else err.message,
                    )
                }
            }
        }
    }

    private fun queryName(context: Context, uri: Uri): String {
        val fallback = uri.lastPathSegment ?: "file"
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else fallback
            } ?: fallback
    }
}
