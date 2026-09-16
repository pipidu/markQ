package com.markq.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markq.BuildConfig
import com.markq.core.ShareCode
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsForm(
    val nickname: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val shareCode: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val update: UpdateInfo? = null,
    val checkingUpdate: Boolean = false,
)

class SettingsViewModel(
    private val repo: MarkRepository,
    private val updates: UpdateChecker,
    private val app: Application,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    private val _form = MutableStateFlow(SettingsForm())
    val form: StateFlow<SettingsForm> = _form.asStateFlow()
    private var hydrated = false

    init {
        viewModelScope.launch {
            repo.settingsFlow.collect { cfg ->
                if (!hydrated) {
                    hydrated = true
                    _form.update {
                        it.copy(
                            nickname = cfg.nickname,
                            url = cfg.webdavUrl,
                            username = cfg.username,
                            password = cfg.password,
                            shareCode = ShareCode.encode(cfg.webdavUrl, cfg.username, cfg.password),
                        )
                    }
                }
            }
        }
    }

    fun setNickname(value: String) = _form.update { it.copy(nickname = value) }
    fun setUrl(value: String) = _form.update { it.copy(url = value, shareCode = ShareCode.encode(value, it.username, it.password)) }
    fun setUsername(value: String) = _form.update { it.copy(username = value, shareCode = ShareCode.encode(it.url, value, it.password)) }
    fun setPassword(value: String) = _form.update { it.copy(password = value, shareCode = ShareCode.encode(it.url, it.username, value)) }

    fun save() {
        val f = _form.value
        if (f.nickname.isBlank() || f.url.isBlank()) {
            _form.update { it.copy(message = "Nickname and WebDAV URL are required") }
            return
        }
        viewModelScope.launch {
            _form.update { it.copy(busy = true, message = null) }
            runCatching {
                repo.saveServer(f.nickname, f.url, f.username, f.password)
            }.onSuccess {
                _form.update {
                    it.copy(
                        busy = false,
                        message = "Saved",
                        shareCode = ShareCode.encode(f.url, f.username, f.password),
                    )
                }
            }.onFailure { err ->
                _form.update { it.copy(busy = false, message = err.message) }
            }
        }
    }

    fun checkUpdate() {
        viewModelScope.launch {
            _form.update { it.copy(checkingUpdate = true, message = null) }
            runCatching { updates.check() }
                .onSuccess { info ->
                    _form.update {
                        it.copy(
                            checkingUpdate = false,
                            update = info,
                            message = if (info == null) "Already on ${BuildConfig.VERSION_NAME}" else null,
                        )
                    }
                }
                .onFailure { err ->
                    _form.update { it.copy(checkingUpdate = false, message = err.message) }
                }
        }
    }

    fun installUpdate(info: UpdateInfo) {
        viewModelScope.launch {
            _form.update { it.copy(busy = true, message = "Downloading update…") }
            runCatching {
                val apk = updates.downloadApk(app, info.apkUrl)
                ApkInstaller.install(app, apk)
            }.onFailure { err ->
                _form.update { it.copy(busy = false, message = err.message) }
            }.onSuccess {
                _form.update { it.copy(busy = false, message = "Install prompt opened") }
            }
        }
    }
}
