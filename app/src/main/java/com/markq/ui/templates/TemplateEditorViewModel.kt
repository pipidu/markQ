package com.markq.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markq.core.MarkColor
import com.markq.core.MarkTags
import com.markq.core.SyncErrors
import com.markq.data.MarkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TemplateEditorUiState(
    val templateId: String? = null,
    val name: String = "",
    val text: String = "",
    val color: String? = null,
    val tags: List<String> = emptyList(),
    val tagDraft: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val loaded: Boolean = false,
)

class TemplateEditorViewModel(
    private val repo: MarkRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TemplateEditorUiState())
    val state: StateFlow<TemplateEditorUiState> = _state.asStateFlow()

    fun load(templateId: String?) {
        if (_state.value.loaded) return
        if (templateId.isNullOrBlank()) {
            _state.update { it.copy(loaded = true) }
            return
        }
        viewModelScope.launch {
            val row = repo.getTemplate(templateId)
            if (row == null) {
                _state.update { it.copy(loaded = true) }
                return@launch
            }
            val model = row.toModel()
            _state.update {
                it.copy(
                    templateId = templateId,
                    name = model.name,
                    text = model.text,
                    color = model.color,
                    tags = model.tags,
                    loaded = true,
                )
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setText(value: String) = _state.update { it.copy(text = value) }
    fun setColor(value: String?) = _state.update { it.copy(color = MarkColor.normalize(value)) }
    fun setTagDraft(value: String) = _state.update { it.copy(tagDraft = value) }

    fun addTag() {
        val s = _state.value
        _state.update { it.copy(tags = MarkTags.normalize(s.tags + s.tagDraft), tagDraft = "") }
    }

    fun removeTag(tag: String) {
        _state.update { current ->
            current.copy(tags = current.tags.filterNot { it.equals(tag, ignoreCase = true) })
        }
    }

    fun save() {
        var snapshot: TemplateEditorUiState? = null
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
            runCatching {
                val id = s.templateId
                if (id.isNullOrBlank()) {
                    repo.createTemplate(
                        name = s.name,
                        text = s.text,
                        color = s.color,
                        tags = s.tags,
                    )
                } else {
                    repo.updateTemplate(
                        id = id,
                        name = s.name,
                        text = s.text,
                        color = s.color,
                        tags = s.tags,
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
}
