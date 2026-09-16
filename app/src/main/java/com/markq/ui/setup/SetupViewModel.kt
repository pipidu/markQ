package com.markq.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

class SetupViewModel(
    private val repo: MarkRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    fun setNickname(value: String) = _state.update { it.copy(nickname = value, error = null) }
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
            .onFailure { err ->
                _state.update { it.copy(error = err.message ?: "Invalid share code") }
            }
    }

    fun connect() {
        val s = _state.value
        if (s.nickname.isBlank()) {
            _state.update { it.copy(error = "Nickname is required") }
            return
        }
        if (s.shareCode.isNotBlank() && s.url.isBlank()) {
            applyShareCode()
        }
        val url = _state.value.url.trim()
        if (url.isBlank()) {
            _state.update { it.copy(error = "WebDAV URL or share code is required") }
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _state.update { it.copy(error = "WebDAV URL must start with http:// or https://") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val latest = _state.value
            runCatching {
                repo.saveServer(latest.nickname, latest.url, latest.username, latest.password)
            }.onFailure { err ->
                _state.update { it.copy(busy = false, error = err.message ?: "Could not connect") }
            }.onSuccess {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
