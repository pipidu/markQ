package com.markq.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markq.R
import com.markq.core.MarkQLink
import com.markq.data.MarkRepository
import com.markq.data.UpdateManager
import com.markq.data.UpdateUiState
import com.markq.data.local.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainViewModel(
    private val repo: MarkRepository,
    private val updates: UpdateManager,
    private val app: Application,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    val update: StateFlow<UpdateUiState> = updates.state

    private val _openTemplateId = MutableStateFlow<String?>(null)
    val openTemplateId: StateFlow<String?> = _openTemplateId.asStateFlow()

    private val _openTemplateMode = MutableStateFlow(MarkQLink.OpenMode.Edit)
    val openTemplateMode: StateFlow<MarkQLink.OpenMode> = _openTemplateMode.asStateFlow()

    private val _linkError = MutableStateFlow<String?>(null)
    val linkError: StateFlow<String?> = _linkError.asStateFlow()

    private val mutex = Mutex()
    private var pendingTemplateId: String? = null
    private var pendingOpenMode = MarkQLink.OpenMode.Edit
    private var lastIntentIdentity: Int? = null

    init {
        updates.startOnLaunch()
        viewModelScope.launch {
            repo.settingsFlow.collect {
                if (it.isConfigured) resolvePending()
            }
        }
    }

    fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val identity = System.identityHashCode(intent)
        if (identity == lastIntentIdentity) return
        val data = intent.data?.toString() ?: return
        if (!data.trim().startsWith("${MarkQLink.SCHEME}:", ignoreCase = true)) return
        lastIntentIdentity = identity
        val id = MarkQLink.parseTemplateId(data)
        if (id.isNullOrBlank()) {
            _linkError.value = app.getString(R.string.error_template_link_invalid)
            return
        }
        pendingTemplateId = id
        pendingOpenMode = MarkQLink.parseOpenMode(data)
        viewModelScope.launch { resolvePending() }
    }

    fun onOpenedTemplateLink() {
        _openTemplateId.value = null
        _openTemplateMode.value = MarkQLink.OpenMode.Edit
    }

    fun consumeLinkError() {
        _linkError.value = null
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

    private suspend fun resolvePending() = mutex.withLock {
        val id = pendingTemplateId ?: return
        val cfg = repo.currentSettings()
        if (!cfg.isConfigured) return
        var row = repo.getTemplate(id)
        if (row == null || row.deleted) {
            repo.sync()
            row = repo.getTemplate(id)
        }
        if (row == null || row.deleted) {
            pendingTemplateId = null
            pendingOpenMode = MarkQLink.OpenMode.Edit
            _linkError.value = app.getString(R.string.error_template_link_missing)
            return
        }
        val mode = pendingOpenMode
        pendingTemplateId = null
        pendingOpenMode = MarkQLink.OpenMode.Edit
        _openTemplateMode.value = mode
        _openTemplateId.value = id
    }
}
