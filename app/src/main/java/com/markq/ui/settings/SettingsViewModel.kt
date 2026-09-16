package com.markq.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markq.R
import com.markq.core.NutstoreDav
import com.markq.core.ShareCode
import com.markq.data.MarkRepository
import com.markq.data.UpdateInfo
import com.markq.data.UpdateManager
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
    val url: String = NutstoreDav.DEFAULT_SERVER,
    val remoteDir: String = NutstoreDav.DEFAULT_DIR,
    val username: String = "",
    val password: String = "",
    val shareCode: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val update: UpdateInfo? = null,
    val checkingUpdate: Boolean = false,
    val downloading: Boolean = false,
    val downloadPercent: Int = 0,
    val downloadIndeterminate: Boolean = false,
    val nicknameError: Boolean = false,
)

class SettingsViewModel(
    private val repo: MarkRepository,
    private val updates: UpdateManager,
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
                    val url = cfg.webdavUrl.ifBlank { NutstoreDav.DEFAULT_SERVER }
                    val dir = if (cfg.webdavUrl.isBlank()) {
                        cfg.remoteDir.ifBlank { NutstoreDav.DEFAULT_DIR }
                    } else {
                        cfg.remoteDir
                    }
                    _form.update {
                        it.copy(
                            nickname = cfg.nickname,
                            url = url,
                            remoteDir = dir,
                            username = cfg.username,
                            password = cfg.password,
                            shareCode = ShareCode.encode(url, cfg.username, cfg.password, dir),
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            updates.state.collect { st ->
                _form.update {
                    it.copy(
                        checkingUpdate = st.userInitiated && (st.checking || st.downloading),
                        downloading = st.downloading,
                        downloadPercent = st.progressPercent,
                        downloadIndeterminate = st.downloading && st.downloadTotal <= 0L,
                        update = st.info,
                        message = when {
                            !st.userInitiated -> it.message
                            st.downloading -> app.getString(R.string.downloading_update)
                            st.checking -> app.getString(R.string.checking_updates)
                            st.info != null -> app.getString(R.string.update_ready, st.info.version)
                            else -> st.error ?: it.message
                        },
                    )
                }
            }
        }
    }

    fun setNickname(value: String) = _form.update { it.copy(nickname = value, nicknameError = false) }
    fun setUrl(value: String) = _form.update {
        it.copy(url = value, shareCode = ShareCode.encode(value, it.username, it.password, it.remoteDir))
    }
    fun setRemoteDir(value: String) = _form.update {
        it.copy(remoteDir = value, shareCode = ShareCode.encode(it.url, it.username, it.password, value))
    }
    fun setUsername(value: String) = _form.update {
        it.copy(username = value, shareCode = ShareCode.encode(it.url, value, it.password, it.remoteDir))
    }
    fun setPassword(value: String) = _form.update {
        it.copy(password = value, shareCode = ShareCode.encode(it.url, it.username, value, it.remoteDir))
    }

    fun save() {
        val f = _form.value
        if (f.nickname.isBlank()) {
            _form.update {
                it.copy(
                    message = app.getString(R.string.error_nickname_required),
                    nicknameError = true,
                )
            }
            return
        }
        val url = f.url.trim().ifBlank { NutstoreDav.DEFAULT_SERVER }
        val dir = f.remoteDir.trim()
        viewModelScope.launch {
            _form.update { it.copy(busy = true, message = null, nicknameError = false) }
            runCatching {
                repo.saveServer(f.nickname, url, f.username, f.password, dir)
            }.onSuccess {
                _form.update {
                    it.copy(
                        busy = false,
                        url = url,
                        remoteDir = dir,
                        message = app.getString(R.string.saved),
                        shareCode = ShareCode.encode(url, f.username, f.password, dir),
                    )
                }
            }.onFailure { err ->
                _form.update { it.copy(busy = false, message = err.message) }
            }
        }
    }

    fun checkUpdate() {
        updates.checkNow()
    }

    fun installUpdate(context: android.content.Context) {
        updates.install(context)
        _form.update { it.copy(message = app.getString(R.string.install_prompt_opened)) }
    }
}
