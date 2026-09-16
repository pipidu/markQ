package com.markq.ui.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markq.core.MarkColor
import com.markq.core.MarkTags
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
    val fromImagePicker: Boolean = false,
)

data class EditorUiState(
    val entryId: String? = null,
    val text: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now(),
    val color: String? = null,
    val tags: List<String> = emptyList(),
    val tagDraft: String = "",
    val createdBy: String? = null,
    val attachments: List<DraftAttachment> = emptyList(),
    val compressImages: Boolean = true,
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

    fun load(entryId: String?, templateId: String? = null) {
        if (_state.value.loaded) return
        if (!entryId.isNullOrBlank()) {
            viewModelScope.launch { loadEntry(entryId) }
            return
        }
        if (!templateId.isNullOrBlank()) {
            viewModelScope.launch { loadTemplate(templateId) }
            return
        }
        _state.update { it.copy(loaded = true) }
    }

    private suspend fun loadEntry(entryId: String) {
        val row = repo.getEntry(entryId)
        if (row == null) {
            _state.update { it.copy(loaded = true) }
            return
        }
            val zone = Instant.ofEpochMilli(row.entry.occurredAt).atZone(ZoneId.systemDefault())
            _state.update {
                it.copy(
                    entryId = entryId,
                    text = row.entry.text,
                    date = zone.toLocalDate(),
                    time = zone.toLocalTime(),
                    color = row.entry.color,
                    tags = MarkTags.decode(row.entry.tags),
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

    private suspend fun loadTemplate(templateId: String) {
        val row = repo.getTemplate(templateId)
        if (row == null) {
            _state.update { it.copy(loaded = true) }
            return
        }
        val model = row.toModel()
        _state.update {
            it.copy(
                text = model.text,
                color = model.color,
                tags = model.tags,
                loaded = true,
            )
        }
    }

    fun setText(value: String) = _state.update { it.copy(text = value) }
    fun setDate(value: LocalDate) = _state.update { it.copy(date = value) }
    fun setTime(value: LocalTime) = _state.update { it.copy(time = value) }
    fun setColor(value: String?) = _state.update { it.copy(color = MarkColor.normalize(value)) }
    fun setTagDraft(value: String) = _state.update { it.copy(tagDraft = value) }

    fun addTag() {
        val s = _state.value
        val next = MarkTags.normalize(s.tags + s.tagDraft)
        _state.update { it.copy(tags = next, tagDraft = "") }
    }

    fun removeTag(tag: String) {
        _state.update { current ->
            current.copy(tags = current.tags.filterNot { it.equals(tag, ignoreCase = true) })
        }
    }

    fun setCompressImages(value: Boolean) = _state.update { it.copy(compressImages = value) }

    fun showError(message: String) = _state.update { it.copy(error = message) }

    fun addCameraCapture(context: Context, file: File) {
        try {
            if (!file.exists() || file.length() <= 0L) return
            val pending = File(context.cacheDir, "pending").apply { mkdirs() }
            val dest = File(pending, UUID.randomUUID().toString())
            file.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            val extra = DraftAttachment(
                key = UUID.randomUUID().toString(),
                uri = Uri.fromFile(dest),
                name = file.name.ifBlank { "photo.jpg" },
                mime = "image/jpeg",
                fromImagePicker = true,
            )
            _state.update { it.copy(attachments = it.attachments + extra) }
        } finally {
            CaptureUris.deleteQuietly(file)
        }
    }

    fun addUris(context: Context, uris: List<Uri>, fromImagePicker: Boolean) {
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
                fromImagePicker = fromImagePicker,
            )
        }
        _state.update { it.copy(attachments = it.attachments + extras) }
    }

    fun removeAttachment(key: String) {
        _state.update { it.copy(attachments = it.attachments.filterNot { att -> att.key == key }) }
    }

    fun save() {
        var snapshot: EditorUiState? = null
        _state.update { current ->
            if (current.busy || current.saved) {
                current
            } else {
                snapshot = current
                current.copy(busy = true, error = null)
            }
        }
        val s = snapshot ?: return
        viewModelScope.launch {
            val occurred = s.date.atTime(s.time).atZone(ZoneId.systemDefault()).toInstant()
            runCatching {
                val compress = s.compressImages
                fun pending(att: DraftAttachment) = MarkRepository.PendingAttachment(
                    uri = att.uri,
                    name = att.name,
                    mime = att.mime,
                    compressImage = compress && att.fromImagePicker,
                )
                val keep = s.attachments.mapNotNull { it.existingId }
                val fresh = s.attachments.filter { it.existingId == null }.map(::pending)
                val id = s.entryId
                if (id.isNullOrBlank()) {
                    repo.create(
                        text = s.text.trim(),
                        occurredAt = occurred,
                        color = s.color,
                        tags = s.tags,
                        attachments = s.attachments.map(::pending),
                    )
                } else {
                    repo.update(
                        id = id,
                        text = s.text.trim(),
                        occurredAt = occurred,
                        color = s.color,
                        tags = s.tags,
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
