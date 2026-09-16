package com.markq.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markq.data.ApkInstaller
import com.markq.data.MarkRepository
import com.markq.data.UpdateChecker
import com.markq.data.UpdateInfo
import com.markq.data.local.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repo: MarkRepository,
    private val updates: UpdateChecker,
    private val app: Application,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update.asStateFlow()

    private val _updateMessage = MutableStateFlow<String?>(null)
    val updateMessage: StateFlow<String?> = _updateMessage.asStateFlow()

    private val _installing = MutableStateFlow(false)
    val installing: StateFlow<Boolean> = _installing.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { updates.check() }
                .onSuccess { _update.value = it }
                .onFailure { _updateMessage.value = it.message }
        }
    }

    fun dismissUpdate() {
        _update.value = null
    }

    fun installUpdate(info: UpdateInfo) {
        viewModelScope.launch {
            _installing.value = true
            runCatching {
                val apk = updates.downloadApk(app, info.apkUrl)
                ApkInstaller.install(app, apk)
            }.onFailure {
                _updateMessage.value = it.message ?: "Update failed"
            }
            _installing.value = false
        }
    }

    fun consumeUpdateMessage() {
        _updateMessage.value = null
    }
}
