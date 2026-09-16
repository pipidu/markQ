package com.markq.ui.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markq.core.MarkColor
import com.markq.core.MarkPlace
import com.markq.core.MarkTags
import com.markq.core.SyncErrors
import com.markq.data.MarkRepository
import com.markq.data.local.DeviceLocation
import com.markq.data.local.PlaceNameResolver
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
import kotlinx.coroutines.withTimeoutOrNull

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
    val includeLocation: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeName: String? = null,
    val locating: Boolean = false,
    val needLocationPermission: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val loaded: Boolean = false,
)

class EditorViewModel(
    private val repo: MarkRepository,
    private val places: PlaceNameResolver,
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
                    includeLocation = true,
                    latitude = row.entry.latitude,
                    longitude = row.entry.longitude,
                    placeName = row.entry.placeName,
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

    fun setIncludeLocation(context: Context, value: Boolean) {
        _state.update { it.copy(includeLocation = value, needLocationPermission = false) }
        if (value) prepareLocation(context)
    }

    fun prepareLocation(context: Context) {
        val s = _state.value
        if (!s.includeLocation) return
        if (MarkPlace.hasFix(s.latitude, s.longitude)) {
            if (s.placeName.isNullOrBlank()) resolvePlaceName(s.latitude!!, s.longitude!!)
            return
        }
        if (!DeviceLocation.hasPermission(context)) {
            _state.update { it.copy(needLocationPermission = true, locating = false) }
            return
        }
        captureLocation(context)
    }

    fun onLocationPermission(context: Context, granted: Boolean) {
        _state.update { it.copy(needLocationPermission = false) }
        if (granted && _state.value.includeLocation) {
            captureLocation(context)
        }
    }

    fun captureLocation(context: Context) {
        val s = _state.value
        if (!s.includeLocation || s.locating) return
        if (MarkPlace.hasFix(s.latitude, s.longitude)) return
        _state.update { it.copy(locating = true) }
        DeviceLocation.peek(context)?.let { peek ->
            _state.update { current ->
                if (!current.includeLocation) current else current.copy(
                    latitude = current.latitude ?: peek.latitude,
                    longitude = current.longitude ?: peek.longitude,
                )
            }
        }
        val peeked = _state.value
        if (MarkPlace.hasFix(peeked.latitude, peeked.longitude) && peeked.placeName.isNullOrBlank()) {
            resolvePlaceName(peeked.latitude!!, peeked.longitude!!)
        }
        viewModelScope.launch {
            val fix = runCatching { DeviceLocation.current(context) }.getOrNull()
            _state.update { current ->
                if (!current.includeLocation) {
                    current.copy(locating = false)
                } else {
                    current.copy(
                        locating = false,
                        latitude = fix?.latitude ?: current.latitude,
                        longitude = fix?.longitude ?: current.longitude,
                    )
                }
            }
            val lat = _state.value.latitude
            val lng = _state.value.longitude
            if (_state.value.includeLocation && MarkPlace.hasFix(lat, lng) && _state.value.placeName.isNullOrBlank()) {
                resolvePlaceName(lat!!, lng!!)
            }
        }
    }

    private fun resolvePlaceName(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            val name = runCatching { places.resolve(latitude, longitude) }.getOrNull()?.ifBlank { null }
            if (name == null) return@launch
            _state.update { current ->
                if (!current.includeLocation || current.placeName?.isNotBlank() == true) current
                else current.copy(placeName = name)
            }
        }
    }

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
                val lat = if (s.includeLocation) s.latitude else null
                val lng = if (s.includeLocation) s.longitude else null
                var place = if (s.includeLocation) {
                    _state.value.placeName?.trim()?.ifBlank { null } ?: s.placeName?.trim()?.ifBlank { null }
                } else {
                    null
                }
                if (s.includeLocation && MarkPlace.hasFix(lat, lng) && place.isNullOrBlank()) {
                    place = withTimeoutOrNull(5_000) { places.resolve(lat!!, lng!!) }?.trim()?.ifBlank { null }
                }
                if (id.isNullOrBlank()) {
                    repo.create(
                        text = s.text.trim(),
                        occurredAt = occurred,
                        color = s.color,
                        tags = s.tags,
                        attachments = s.attachments.map(::pending),
                        latitude = lat,
                        longitude = lng,
                        placeName = place,
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
                        latitude = lat,
                        longitude = lng,
                        placeName = place,
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
