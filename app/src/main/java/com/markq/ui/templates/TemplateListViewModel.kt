package com.markq.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markq.data.MarkRepository
import com.markq.data.local.TemplateEntity
import com.markq.data.remote.SyncUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplateListViewModel(
    private val repo: MarkRepository,
) : ViewModel() {
    val templates: StateFlow<List<TemplateEntity>> = repo.templates.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val syncState: StateFlow<SyncUiState> = repo.syncState

    fun delete(id: String) {
        viewModelScope.launch { repo.deleteTemplate(id) }
    }
}
