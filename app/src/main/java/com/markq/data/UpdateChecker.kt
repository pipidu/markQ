package com.markq.data

import android.app.Application
import android.content.Context
import com.markq.BuildConfig
import com.markq.R
import com.markq.core.SemVer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    @SerialName("content_type") val contentType: String? = null,
)

data class UpdateInfo(
    val version: String,
    val apk: File,
)

data class UpdateUiState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val info: UpdateInfo? = null,
    val error: String? = null,
    val userInitiated: Boolean = false,
) {
    val readyToInstall: Boolean get() = info?.apk?.exists() == true
}

class UpdateChecker(
    private val http: OkHttpClient,
    private val app: Application,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkAndDownload(): UpdateInfo? = withContext(Dispatchers.IO) {
        val metaUrl = "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
        val request = Request.Builder()
            .url(metaUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MarkQ")
            .build()
        val release = http.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) {
                error(app.getString(R.string.error_update_check, response.code))
            }
            json.decodeFromString(GithubRelease.serializer(), response.body?.string().orEmpty())
        }
        if (!SemVer.isNewer(release.tagName, BuildConfig.VERSION_NAME)) return@withContext null
        val version = release.tagName.trim().removePrefix("v").removePrefix("V")
        val tag = if (release.tagName.startsWith("v", ignoreCase = true)) release.tagName else "v$version"
        val asset = release.assets.firstOrNull {
            it.name.equals("MarkQ-$version.apk", ignoreCase = true) || it.name.endsWith(".apk", ignoreCase = true)
        } ?: error(app.getString(R.string.error_no_apk_asset))
        val apkUrl = asset.browserDownloadUrl.ifBlank {
            "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/download/$tag/MarkQ-$version.apk"
        }
        val apk = downloadApk(apkUrl)
        UpdateInfo(version = version, apk = apk)
    }

    private fun downloadApk(apkUrl: String): File {
        val dest = File(app.cacheDir, "markq-update.apk")
        val request = Request.Builder()
            .url(apkUrl)
            .header("User-Agent", "MarkQ")
            .header("Accept", "application/vnd.android.package-archive,application/octet-stream")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error(app.getString(R.string.error_update_download, response.code))
            }
            val body = response.body ?: error(app.getString(R.string.error_update_empty))
            dest.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        if (dest.length() == 0L) {
            dest.delete()
            error(app.getString(R.string.error_update_empty))
        }
        if (!isApkFile(dest)) {
            dest.delete()
            error(app.getString(R.string.error_update_not_apk))
        }
        return dest
    }

    companion object {
        fun isApkFile(file: File): Boolean {
            if (!file.exists() || file.length() < 4) return false
            val header = ByteArray(4)
            file.inputStream().use { input ->
                if (input.read(header) < 2) return false
            }
            return header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        }
    }
}

class UpdateManager(
    private val checker: UpdateChecker,
    private val app: Application,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()
    @Volatile private var started = false

    fun startOnLaunch() {
        if (started) return
        started = true
        scope.launch { checkAndDownload(userInitiated = false) }
    }

    fun checkNow() {
        scope.launch { checkAndDownload(userInitiated = true) }
    }

    suspend fun checkAndDownload(userInitiated: Boolean = false): UpdateInfo? = mutex.withLock {
        _state.update {
            it.copy(checking = true, downloading = false, error = null, userInitiated = userInitiated)
        }
        return try {
            val info = checker.checkAndDownload()
            _state.update {
                it.copy(
                    checking = false,
                    downloading = false,
                    info = info,
                    userInitiated = userInitiated,
                    error = if (userInitiated && info == null) {
                        app.getString(R.string.already_latest, BuildConfig.VERSION_NAME)
                    } else {
                        null
                    },
                )
            }
            info
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    checking = false,
                    downloading = false,
                    userInitiated = userInitiated,
                    error = if (userInitiated) {
                        e.message ?: app.getString(R.string.error_update_failed)
                    } else {
                        null
                    },
                )
            }
            null
        }
    }

    fun install(context: Context) {
        val apk = _state.value.info?.apk ?: return
        try {
            ApkInstaller.install(context, apk)
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: app.getString(R.string.error_update_failed)) }
        }
    }

    fun dismiss() {
        _state.update { it.copy(info = null, downloading = false, checking = false) }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }
}
