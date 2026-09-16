package com.markq.data

import android.content.Context
import com.markq.BuildConfig
import com.markq.core.SemVer
import java.io.File
import kotlinx.coroutines.Dispatchers
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
    @SerialName("html_url") val htmlUrl: String,
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
    val apkUrl: String,
    val htmlUrl: String,
)

class UpdateChecker(
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MarkQ")
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) {
                error("Update check failed (${response.code})")
            }
            val body = response.body?.string().orEmpty()
            val release = json.decodeFromString(GithubRelease.serializer(), body)
            if (!SemVer.isNewer(release.tagName, BuildConfig.VERSION_NAME)) return@withContext null
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return@withContext null
            UpdateInfo(
                version = release.tagName.removePrefix("v").removePrefix("V"),
                apkUrl = apk.browserDownloadUrl,
                htmlUrl = release.htmlUrl,
            )
        }
    }

    suspend fun downloadApk(context: Context, apkUrl: String): File = withContext(Dispatchers.IO) {
        val dest = File(context.cacheDir, "markq-update.apk")
        val request = Request.Builder()
            .url(apkUrl)
            .header("User-Agent", "MarkQ")
            .header("Accept", "application/octet-stream")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed (${response.code})")
            val bytes = response.body?.bytes() ?: error("Empty APK")
            dest.writeBytes(bytes)
        }
        dest
    }
}
