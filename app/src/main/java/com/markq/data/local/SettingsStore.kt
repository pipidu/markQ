package com.markq.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    val remoteDir: String = "",
    val username: String = "",
    val password: String = "",
    val lastSyncEpochMs: Long = 0L,
    val backgroundSync: Boolean = true,
    val barColor: String = com.markq.core.UiThemeDefaults.BAR,
    val backgroundColor: String = com.markq.core.UiThemeDefaults.BACKGROUND,
    val fabColor: String = com.markq.core.UiThemeDefaults.FAB,
) {
    val hasNickname: Boolean get() = nickname.isNotBlank()
    val hasServer: Boolean get() = webdavUrl.isNotBlank()
    val isConfigured: Boolean get() = hasNickname && hasServer
    val collectionUrl: String get() = com.markq.core.NutstoreDav.collectionUrl(webdavUrl, remoteDir)

    fun toWebDavConfig() = com.markq.data.remote.WebDavConfig(
        baseUrl = collectionUrl,
        username = username,
        password = password,
        davRoot = webdavUrl.ifBlank { collectionUrl },
    )
}

class SettingsStore(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            nickname = prefs[NICKNAME].orEmpty(),
            webdavUrl = prefs[URL].orEmpty(),
            remoteDir = prefs[REMOTE_DIR].orEmpty(),
            username = prefs[USERNAME].orEmpty(),
            password = prefs[PASSWORD].orEmpty(),
            lastSyncEpochMs = prefs[LAST_SYNC] ?: 0L,
            backgroundSync = prefs[BACKGROUND_SYNC] ?: true,
            barColor = prefs[BAR_COLOR] ?: com.markq.core.UiThemeDefaults.BAR,
            backgroundColor = prefs[BG_COLOR] ?: com.markq.core.UiThemeDefaults.BACKGROUND,
            fabColor = prefs[FAB_COLOR] ?: com.markq.core.UiThemeDefaults.FAB,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun saveServer(
        nickname: String,
        url: String,
        username: String,
        password: String,
        remoteDir: String,
    ) {
        val nick = nickname.trim()
        require(nick.isNotEmpty()) { "nickname" }
        dataStore.edit { prefs ->
            prefs[NICKNAME] = nick
            prefs[URL] = url.trim()
            prefs[REMOTE_DIR] = remoteDir.trim().trim('/')
            prefs[USERNAME] = username.trim()
            prefs[PASSWORD] = password
        }
    }

    suspend fun saveNickname(nickname: String) {
        val nick = nickname.trim()
        require(nick.isNotEmpty()) { "nickname" }
        dataStore.edit { it[NICKNAME] = nick }
    }

    suspend fun saveTheme(
        barColor: String? = null,
        backgroundColor: String? = null,
        fabColor: String? = null,
    ) {
        dataStore.edit { prefs ->
            barColor?.let { prefs[BAR_COLOR] = it }
            backgroundColor?.let { prefs[BG_COLOR] = it }
            fabColor?.let { prefs[FAB_COLOR] = it }
        }
    }

    suspend fun setLastSync(epochMs: Long) {
        dataStore.edit { it[LAST_SYNC] = epochMs }
    }

    suspend fun setBackgroundSync(enabled: Boolean) {
        dataStore.edit { it[BACKGROUND_SYNC] = enabled }
    }

    private companion object {
        val NICKNAME = stringPreferencesKey("nickname")
        val URL = stringPreferencesKey("webdav_url")
        val REMOTE_DIR = stringPreferencesKey("webdav_remote_dir")
        val USERNAME = stringPreferencesKey("webdav_username")
        val PASSWORD = stringPreferencesKey("webdav_password")
        val LAST_SYNC = longPreferencesKey("last_sync")
        val BACKGROUND_SYNC = booleanPreferencesKey("background_sync")
        val BAR_COLOR = stringPreferencesKey("theme_bar_color")
        val BG_COLOR = stringPreferencesKey("theme_background_color")
        val FAB_COLOR = stringPreferencesKey("theme_fab_color")
    }
}
