package com.markq.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markq.data.MarkRepository
import com.markq.data.local.EntryWithAttachments
import com.markq.data.remote.SyncUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repo: MarkRepository,
) : ViewModel() {
    val entries: StateFlow<List<EntryWithAttachments>> = repo.entries.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val syncState: StateFlow<SyncUiState> = repo.syncState

    init {
        viewModelScope.launch { repo.sync() }
    }

    fun refresh() {
        viewModelScope.launch { repo.sync() }
    }

    fun complete(id: String) {
        viewModelScope.launch { repo.complete(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }
}
