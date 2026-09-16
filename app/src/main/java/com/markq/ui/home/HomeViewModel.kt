package com.markq.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markq.core.MarkTags
import com.markq.data.MarkRepository
import com.markq.data.local.EntryWithAttachments
import com.markq.data.remote.SyncUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repo: MarkRepository,
) : ViewModel() {
    private val allEntries: StateFlow<List<EntryWithAttachments>> = repo.entries.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    private val _tagFilter = MutableStateFlow<String?>(null)
    val tagFilter: StateFlow<String?> = _tagFilter.asStateFlow()
    private val _pulling = MutableStateFlow(false)
    val pulling: StateFlow<Boolean> = _pulling.asStateFlow()

    val availableTags: StateFlow<List<String>> = allEntries.map { rows ->
        rows.flatMap { MarkTags.decode(it.entry.tags) }
            .distinctBy { it.lowercase() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val entries: StateFlow<List<EntryWithAttachments>> = combine(allEntries, _tagFilter) { rows, tag ->
        if (tag.isNullOrBlank()) rows
        else rows.filter { MarkTags.contains(it.entry.tags, tag) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasAnyMarks: StateFlow<Boolean> = allEntries.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val syncState: StateFlow<SyncUiState> = repo.syncState

    init {
        viewModelScope.launch { repo.sync() }
        viewModelScope.launch {
            combine(availableTags, _tagFilter) { tags, selected -> selected to tags }
                .collect { (selected, tags) ->
                    if (selected != null && tags.none { it.equals(selected, ignoreCase = true) }) {
                        _tagFilter.value = null
                    }
                }
        }
    }

    fun setTagFilter(tag: String?) {
        _tagFilter.value = tag
    }

    fun refresh(fromPull: Boolean = false) {
        viewModelScope.launch {
            if (fromPull) _pulling.value = true
            try {
                repo.sync()
            } finally {
                if (fromPull) _pulling.value = false
            }
        }
    }

    fun complete(id: String) {
        viewModelScope.launch { repo.complete(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }
}
