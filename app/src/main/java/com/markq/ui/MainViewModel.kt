package com.markq.ui

import androidx.lifecycle.ViewModel
import com.markq.data.MarkRepository
import com.markq.data.UpdateManager
import com.markq.data.UpdateUiState
import com.markq.data.local.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

class MainViewModel(
    repo: MarkRepository,
    private val updates: UpdateManager,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    val update: StateFlow<UpdateUiState> = updates.state

    init {
        updates.startOnLaunch()
    }

    fun dismissUpdate() {
        updates.dismiss()
    }

    fun installUpdate(context: android.content.Context) {
        updates.install(context)
    }

    fun consumeUpdateMessage() {
        updates.consumeError()
    }
}
