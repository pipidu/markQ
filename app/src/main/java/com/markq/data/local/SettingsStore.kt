package com.markq.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("markq_settings")

data class AppSettings(
    val nickname: String = "",
    val webdavUrl: String = "",
    val username: String = "",
    val password: String = "",
    val lastSyncEpochMs: Long = 0L,
) {
    val isConfigured: Boolean
        get() = nickname.isNotBlank() && webdavUrl.isNotBlank()
}

class SettingsStore(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            nickname = prefs[NICKNAME].orEmpty(),
            webdavUrl = prefs[URL].orEmpty(),
            username = prefs[USERNAME].orEmpty(),
            password = prefs[PASSWORD].orEmpty(),
            lastSyncEpochMs = prefs[LAST_SYNC] ?: 0L,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun saveServer(
        nickname: String,
        url: String,
        username: String,
        password: String,
    ) {
        dataStore.edit { prefs ->
            prefs[NICKNAME] = nickname.trim()
            prefs[URL] = url.trim()
            prefs[USERNAME] = username.trim()
            prefs[PASSWORD] = password
        }
    }

    suspend fun saveNickname(nickname: String) {
        dataStore.edit { it[NICKNAME] = nickname.trim() }
    }

    suspend fun setLastSync(epochMs: Long) {
        dataStore.edit { it[LAST_SYNC] = epochMs }
    }

    private companion object {
        val NICKNAME = stringPreferencesKey("nickname")
        val URL = stringPreferencesKey("webdav_url")
        val USERNAME = stringPreferencesKey("webdav_username")
        val PASSWORD = stringPreferencesKey("webdav_password")
        val LAST_SYNC = longPreferencesKey("last_sync")
    }
}
