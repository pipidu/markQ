package com.markq.ui.setup

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markq.R
import com.markq.core.ShareCode
import com.markq.data.MarkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val nickname: String = "",
    val shareCode: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val nicknameError: Boolean = false,
)

class SetupViewModel(
    private val repo: MarkRepository,
    private val app: Application,
) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cfg = repo.currentSettings()
            _state.update {
                it.copy(
                    nickname = cfg.nickname,
                    url = cfg.webdavUrl,
                    username = cfg.username,
                    password = cfg.password,
                )
            }
        }
    }

    fun setNickname(value: String) = _state.update {
        it.copy(nickname = value, error = null, nicknameError = false)
    }
    fun setShareCode(value: String) = _state.update { it.copy(shareCode = value, error = null) }
    fun setUrl(value: String) = _state.update { it.copy(url = value, error = null) }
    fun setUsername(value: String) = _state.update { it.copy(username = value, error = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun applyShareCode() {
        val raw = _state.value.shareCode
        if (raw.isBlank()) return
        runCatching { ShareCode.decode(raw) }
            .onSuccess { payload ->
                _state.update {
                    it.copy(
                        url = payload.url,
                        username = payload.username,
                        password = payload.password,
                        error = null,
                    )
                }
            }
            .onFailure {
                _state.update { it.copy(error = app.getString(R.string.error_invalid_share_code)) }
            }
    }

    fun connect() {
        val s = _state.value
        if (s.nickname.isBlank()) {
            _state.update {
                it.copy(
                    error = app.getString(R.string.error_nickname_required),
                    nicknameError = true,
                )
            }
            return
        }
        if (s.shareCode.isNotBlank() && s.url.isBlank()) {
            applyShareCode()
        }
        if (_state.value.nickname.isBlank()) {
            _state.update {
                it.copy(
                    error = app.getString(R.string.error_nickname_required),
                    nicknameError = true,
                )
            }
            return
        }
        val url = _state.value.url.trim()
        if (url.isBlank()) {
            _state.update { it.copy(error = app.getString(R.string.error_url_or_share_required)) }
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _state.update { it.copy(error = app.getString(R.string.error_url_scheme)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, nicknameError = false) }
            val latest = _state.value
            runCatching {
                repo.saveServer(latest.nickname, latest.url, latest.username, latest.password)
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        busy = false,
                        error = err.message ?: app.getString(R.string.error_connect_failed),
                    )
                }
            }.onSuccess {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
