package com.markq.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markq.core.MarkListFilter
import com.markq.core.MarkListVisibility
import com.markq.core.MarkSearch
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
    private val _listFilter = MutableStateFlow<MarkListFilter>(MarkListFilter.All)
    val listFilter: StateFlow<MarkListFilter> = _listFilter.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _searchOpen = MutableStateFlow(false)
    val searchOpen: StateFlow<Boolean> = _searchOpen.asStateFlow()
    private val _pulling = MutableStateFlow(false)
    val pulling: StateFlow<Boolean> = _pulling.asStateFlow()
    private val _saveHint = MutableStateFlow<String?>(null)
    val saveHint: StateFlow<String?> = _saveHint.asStateFlow()

    val availableTags: StateFlow<List<String>> = allEntries.map { rows ->
        rows.filter { !it.entry.completed }
            .flatMap { MarkTags.decode(it.entry.tags) }
            .distinctBy { it.lowercase() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val entries: StateFlow<List<EntryWithAttachments>> = combine(
        allEntries,
        _listFilter,
        _searchQuery,
    ) { rows, filter, query ->
        rows.filter { row ->
            MarkListVisibility.include(
                completed = row.entry.completed,
                tags = MarkTags.decode(row.entry.tags),
                filter = filter,
            ) && MarkSearch.matches(
                query,
                row.entry.text,
                MarkTags.decode(row.entry.tags),
                row.entry.createdBy,
                row.entry.completedBy,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasAnyMarks: StateFlow<Boolean> = allEntries.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val hasCompleted: StateFlow<Boolean> = allEntries.map { rows -> rows.any { it.entry.completed } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val syncState: StateFlow<SyncUiState> = repo.syncState
    val pendingCount: StateFlow<Int> = repo.pendingUploadCount.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        0,
    )
    val lastSyncEpochMs: StateFlow<Long> = repo.settingsFlow.map { it.lastSyncEpochMs }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        0L,
    )

    init {
        viewModelScope.launch { repo.sync() }
        viewModelScope.launch {
            repo.saveHints.collect { _saveHint.value = it }
        }
        viewModelScope.launch {
            combine(availableTags, _listFilter) { tags, selected -> selected to tags }
                .collect { (selected, tags) ->
                    if (selected is MarkListFilter.Tag &&
                        tags.none { it.equals(selected.name, ignoreCase = true) }
                    ) {
                        _listFilter.value = MarkListFilter.All
                    }
                }
        }
    }

    fun setListFilter(filter: MarkListFilter) {
        _listFilter.value = filter
    }

    fun setSearchQuery(value: String) {
        _searchQuery.value = value
    }

    fun openSearch() {
        _searchOpen.value = true
    }

    fun closeSearch() {
        _searchOpen.value = false
        _searchQuery.value = ""
    }

    fun consumeSaveHint() {
        _saveHint.value = null
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
