package com.markq.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markq.core.MarkHomeList
import com.markq.core.MarkListFilter
import com.markq.core.MarkListQuery
import com.markq.core.MarkTags
import com.markq.data.MarkRepository
import com.markq.data.local.EntryWithAttachments
import com.markq.data.remote.SyncUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repo: MarkRepository,
) : ViewModel() {
    private val _listFilter = MutableStateFlow<MarkListFilter>(MarkListFilter.All)
    val listFilter: StateFlow<MarkListFilter> = _listFilter.asStateFlow()
    private val _keptVisible = MutableStateFlow<Set<String>>(emptySet())
    private val _justCompletedNonce = MutableStateFlow(0)
    val justCompletedNonce: StateFlow<Int> = _justCompletedNonce.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _searchOpen = MutableStateFlow(false)
    val searchOpen: StateFlow<Boolean> = _searchOpen.asStateFlow()
    private val _pulling = MutableStateFlow(false)
    val pulling: StateFlow<Boolean> = _pulling.asStateFlow()
    private val _saveHint = MutableStateFlow<String?>(null)
    val saveHint: StateFlow<String?> = _saveHint.asStateFlow()

    val availableTags: StateFlow<List<String>> = repo.incompleteTags.map { stored ->
        stored.flatMap { MarkTags.decode(it) }
            .distinctBy { it.lowercase() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<EntryWithAttachments>> = combine(
        _listFilter,
        _searchQuery,
        _keptVisible,
    ) { filter, query, kept ->
        Triple(filter, query, kept)
    }.flatMapLatest { (filter, query, kept) ->
        val source = when (val q = MarkListQuery.forFilter(filter, kept)) {
            MarkListQuery.Completed -> repo.observeCompletedEntries()
            MarkListQuery.Incomplete -> repo.observeIncompleteEntries()
            is MarkListQuery.IncompleteOrIds -> repo.observeIncompleteOrIds(q.ids)
        }
        source.map { rows ->
            rows.filter { row ->
                MarkHomeList.matches(
                    completed = row.entry.completed,
                    storedTags = row.entry.tags,
                    text = row.entry.text,
                    createdBy = row.entry.createdBy,
                    completedBy = row.entry.completedBy,
                    filter = filter,
                    stayVisible = row.entry.id in kept,
                    query = query,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasAnyMarks: StateFlow<Boolean> = repo.hasAnyMarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val hasCompleted: StateFlow<Boolean> = repo.hasCompleted
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
        _keptVisible.value = emptySet()
    }

    fun showCompleted() {
        setListFilter(MarkListFilter.Completed)
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
        _keptVisible.value = emptySet()
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
        viewModelScope.launch {
            val wasCompleted = repo.getEntry(id)?.entry?.completed == true
            val hidesCompleted = _listFilter.value !is MarkListFilter.Completed
            if (!wasCompleted && hidesCompleted) {
                _keptVisible.value = _keptVisible.value + id
                _justCompletedNonce.value = _justCompletedNonce.value + 1
            }
            repo.complete(id)
            if (wasCompleted) {
                _keptVisible.value = _keptVisible.value - id
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }
}
