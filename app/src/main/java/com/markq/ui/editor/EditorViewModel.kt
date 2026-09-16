package com.markq.ui.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markq.data.MarkRepository
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DraftAttachment(
    val uri: Uri,
    val name: String,
    val mime: String,
)

data class EditorUiState(
    val text: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now(),
    val attachments: List<DraftAttachment> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class EditorViewModel(
    private val repo: MarkRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    fun setText(value: String) = _state.update { it.copy(text = value) }
    fun setDate(value: LocalDate) = _state.update { it.copy(date = value) }
    fun setTime(value: LocalTime) = _state.update { it.copy(time = value) }

    fun addUris(context: Context, uris: List<Uri>) {
        val pending = File(context.cacheDir, "pending").apply { mkdirs() }
        val extras = uris.map { uri ->
            val name = queryName(context, uri)
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val dest = File(pending, java.util.UUID.randomUUID().toString())
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to read attached file" }
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            DraftAttachment(
                uri = Uri.fromFile(dest),
                name = name,
                mime = mime,
            )
        }
        _state.update { it.copy(attachments = it.attachments + extras) }
    }

    fun removeAttachment(uri: Uri) {
        _state.update { it.copy(attachments = it.attachments.filterNot { att -> att.uri == uri }) }
    }

    fun save() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val occurred = s.date.atTime(s.time).atZone(ZoneId.systemDefault()).toInstant()
            runCatching {
                repo.create(
                    text = s.text.trim(),
                    occurredAt = occurred,
                    attachments = s.attachments.map {
                        MarkRepository.PendingAttachment(it.uri, it.name, it.mime)
                    },
                )
            }.onSuccess {
                _state.update { it.copy(busy = false, saved = true) }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        busy = false,
                        error = if (com.markq.core.SyncErrors.isSilent(err)) null else err.message,
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
