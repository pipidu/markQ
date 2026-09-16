package com.markq.data.remote

import com.markq.BuildConfig
import com.markq.core.NominatimAddressParts
import com.markq.core.NominatimPlace
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class NominatimGeocoder(
    http: OkHttpClient,
    private val cacheFile: File,
) {
    private val client = http.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val memory = LinkedHashMap<String, String>(32, 0.75f, true)
    private var lastRequestAtMs = 0L

    init {
        runCatching { loadCache() }
    }

    suspend fun reverse(latitude: Double, longitude: Double): String? {
        val key = NominatimPlace.cacheKey(latitude, longitude)
        synchronized(memory) { memory[key] }?.let { return it }
        return mutex.withLock {
            synchronized(memory) { memory[key] }?.let { return@withLock it }
            throttle()
            val name = withContext(Dispatchers.IO) {
                runCatching { fetch(latitude, longitude) }.getOrNull()
            } ?: return@withLock null
            remember(key, name)
            name
        }
    }

    private suspend fun throttle() {
        val wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastRequestAtMs)
        if (wait > 0) delay(wait)
        lastRequestAtMs = System.currentTimeMillis()
    }

    private fun fetch(latitude: Double, longitude: Double): String? {
        val url = REVERSE_URL.newBuilder()
            .addQueryParameter("lat", latitude.toString())
            .addQueryParameter("lon", longitude.toString())
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("zoom", "18")
            .addQueryParameter("addressdetails", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent())
            .header("Accept", "application/json")
            .header("Accept-Language", "zh,en")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val parsed = runCatching { json.decodeFromString(NominatimReverse.serializer(), body) }.getOrNull()
                ?: return null
            return NominatimPlace.format(parsed.displayName, parsed.address?.toParts())
        }
    }

    private fun remember(key: String, name: String) {
        synchronized(memory) {
            memory[key] = name
            while (memory.size > MAX_CACHE) {
                val oldest = memory.keys.first()
                memory.remove(oldest)
            }
            runCatching { persistCacheLocked() }
        }
    }

    private fun loadCache() {
        if (!cacheFile.exists()) return
        val text = cacheFile.readText()
        if (text.isBlank()) return
        val stored = json.decodeFromString(NominatimCacheFile.serializer(), text)
        synchronized(memory) {
            memory.clear()
            stored.entries.entries.take(MAX_CACHE).forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) memory[key] = value
            }
        }
    }

    private fun persistCacheLocked() {
        cacheFile.parentFile?.mkdirs()
        val payload = NominatimCacheFile(entries = memory.toMap())
        cacheFile.writeText(json.encodeToString(NominatimCacheFile.serializer(), payload))
    }

    companion object {
        private val REVERSE_URL = "https://nominatim.openstreetmap.org/reverse".toHttpUrl()
        private const val MIN_INTERVAL_MS = 1_100L
        private const val MAX_CACHE = 200

        fun userAgent(): String =
            "MarkQ/${BuildConfig.VERSION_NAME} (https://github.com/pipidu/markQ)"
    }
}

@Serializable
private data class NominatimCacheFile(
    val entries: Map<String, String> = emptyMap(),
)

@Serializable
private data class NominatimReverse(
    @SerialName("display_name") val displayName: String? = null,
    val address: NominatimAddressDto? = null,
)

@Serializable
private data class NominatimAddressDto(
    @SerialName("house_number") val houseNumber: String? = null,
    val road: String? = null,
    val pedestrian: String? = null,
    val neighbourhood: String? = null,
    val suburb: String? = null,
    val quarter: String? = null,
    @SerialName("city_district") val cityDistrict: String? = null,
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val county: String? = null,
    val state: String? = null,
) {
    fun toParts(): NominatimAddressParts = NominatimAddressParts(
        houseNumber = houseNumber,
        road = road,
        pedestrian = pedestrian,
        neighbourhood = neighbourhood,
        suburb = suburb,
        quarter = quarter,
        cityDistrict = cityDistrict,
        city = city,
        town = town,
        village = village,
        county = county,
        state = state,
    )
}
