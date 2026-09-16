package com.markq.data.remote

import android.content.Context
import com.markq.core.DnsAnswer
import com.markq.core.DnsWire
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.chromium.net.CronetEngine
import org.chromium.net.ExperimentalCronetEngine
import org.chromium.net.UploadDataProvider
import org.chromium.net.UploadDataSink
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo

/**
 * Resolves every OkHttp hostname through AliDNS DoH.
 *
 * Transport: HTTP/3 to `h3://223.5.5.5/dns-query` via Cronet (QUIC hint + host mapped
 * to 223.5.5.5 so bootstrap never uses system DNS). If H3/Cronet is unavailable,
 * the same IP is queried over HTTPS/2 DoH. App lookups never fall back to system DNS.
 */
class AliDohDns(
    context: Context,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : Dns {
    private val app = context.applicationContext
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val cronetExecutor = Executors.newFixedThreadPool(2)
    private val engine: CronetEngine? = runCatching { buildEngine(app) }.getOrNull()
    private val h2Fallback: OkHttpClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                if (hostname == DOH_TLS_NAME || hostname == SERVER_IP) {
                    return listOf(serverAddress())
                }
                throw UnknownHostException(hostname)
            }
        })
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun lookup(hostname: String): List<InetAddress> {
        val key = hostname.trim().trimEnd('.')
        if (key.isEmpty()) throw UnknownHostException(hostname)
        DnsWire.parseLiteral(key)?.let { return listOf(it) }
        if (key.equals("localhost", ignoreCase = true)) {
            return listOf(InetAddress.getByAddress("localhost", byteArrayOf(127, 0, 0, 1)))
        }
        val ascii = DnsWire.asciiName(key)
        val now = nowMs()
        cache[ascii]?.let { hit ->
            if (hit.expiresAtMs > now && hit.addresses.isNotEmpty()) return hit.addresses
        }
        val answer = resolve(ascii)
        if (answer.addresses.isEmpty()) {
            cache[ascii] = CacheEntry(emptyList(), now + NEGATIVE_TTL_MS)
            throw UnknownHostException(hostname)
        }
        val ttlMs = answer.ttlSec.coerceIn(MIN_TTL_SEC, MAX_TTL_SEC) * 1000L
        cache[ascii] = CacheEntry(answer.addresses, now + ttlMs)
        return answer.addresses
    }

    private fun resolve(hostname: String): DnsAnswer {
        val a = queryType(hostname, DnsWire.TYPE_A)
        val aaaa = queryType(hostname, DnsWire.TYPE_AAAA)
        val addresses = (a.addresses + aaaa.addresses).distinct()
        val ttl = listOf(a.ttlSec, aaaa.ttlSec).filter { it > 0 }.minOrNull() ?: DEFAULT_TTL_SEC
        return DnsAnswer(addresses, ttl)
    }

    private fun queryType(hostname: String, type: Int): DnsAnswer {
        val id = (hostname.hashCode() xor type xor nowMs().toInt()) and 0xFFFF
        val query = DnsWire.query(id, hostname, type)
        val body = queryDoh(query) ?: return DnsAnswer(emptyList(), 0)
        return runCatching { DnsWire.parseAnswers(body, hostname) }.getOrDefault(DnsAnswer(emptyList(), 0))
    }

    private fun queryDoh(query: ByteArray): ByteArray? {
        engine?.let { cronet ->
            val viaH3 = runCatching { cronetPost(cronet, DOH_URL, query) }.getOrNull()
            if (viaH3 != null) return viaH3
            val viaIp = runCatching { cronetPost(cronet, DOH_IP_URL, query) }.getOrNull()
            if (viaIp != null) return viaIp
        }
        return runCatching { h2Post(query) }.getOrNull()
    }

    private fun cronetPost(cronet: CronetEngine, url: String, query: ByteArray): ByteArray {
        val latch = CountDownLatch(1)
        val ok = AtomicReference<ByteArray>()
        val err = AtomicReference<IOHolder>()
        val collector = ByteArrayOutputStream()
        val buffer = ByteBuffer.allocateDirect(16 * 1024)
        val callback = object : UrlRequest.Callback() {
            override fun onRedirectReceived(
                request: UrlRequest,
                info: UrlResponseInfo,
                newLocationUrl: String,
            ) {
                request.followRedirect()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                if (info.httpStatusCode !in 200..299) {
                    err.set(IOHolder("DoH HTTP ${info.httpStatusCode}"))
                    request.cancel()
                    latch.countDown()
                    return
                }
                buffer.clear()
                request.read(buffer)
            }

            override fun onReadCompleted(
                request: UrlRequest,
                info: UrlResponseInfo,
                byteBuffer: ByteBuffer,
            ) {
                byteBuffer.flip()
                val chunk = ByteArray(byteBuffer.remaining())
                byteBuffer.get(chunk)
                collector.write(chunk)
                byteBuffer.clear()
                request.read(byteBuffer)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                ok.set(collector.toByteArray())
                latch.countDown()
            }

            override fun onFailed(
                request: UrlRequest,
                info: UrlResponseInfo?,
                error: org.chromium.net.CronetException,
            ) {
                err.set(IOHolder(error.message ?: "DoH failed"))
                latch.countDown()
            }

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                if (ok.get() == null && err.get() == null) {
                    err.set(IOHolder("DoH canceled"))
                }
                latch.countDown()
            }
        }
        val builder = cronet.newUrlRequestBuilder(url, callback, cronetExecutor)
            .setHttpMethod("POST")
            .addHeader("Accept", "application/dns-message")
            .addHeader("Content-Type", "application/dns-message")
            .addHeader("User-Agent", USER_AGENT)
            .setUploadDataProvider(BytesUpload(query), cronetExecutor)
        if (url.startsWith("https://$SERVER_IP")) {
            builder.addHeader("Host", DOH_TLS_NAME)
        }
        builder.build().start()
        if (!latch.await(8, TimeUnit.SECONDS)) {
            error("DoH timeout")
        }
        err.get()?.let { error(it.message) }
        return ok.get() ?: error("empty DoH body")
    }

    private fun h2Post(query: ByteArray): ByteArray {
        val req = Request.Builder()
            .url(DOH_URL)
            .header("Accept", "application/dns-message")
            .header("User-Agent", USER_AGENT)
            .post(query.toRequestBody(DNS_MESSAGE))
            .build()
        h2Fallback.newCall(req).execute().use { response ->
            if (!response.isSuccessful) error("DoH HTTP ${response.code}")
            return response.body?.bytes() ?: error("empty DoH body")
        }
    }

    private class BytesUpload(private val data: ByteArray) : UploadDataProvider() {
        private var offset = 0

        override fun getLength(): Long = data.size.toLong()

        override fun read(uploadDataSink: UploadDataSink, byteBuffer: ByteBuffer) {
            val n = minOf(byteBuffer.remaining(), data.size - offset)
            if (n > 0) {
                byteBuffer.put(data, offset, n)
                offset += n
            }
            uploadDataSink.onReadSucceeded(false)
        }

        override fun rewind(uploadDataSink: UploadDataSink) {
            offset = 0
            uploadDataSink.onRewindSucceeded()
        }
    }

    private class CacheEntry(
        val addresses: List<InetAddress>,
        val expiresAtMs: Long,
    )

    private class IOHolder(val message: String)

    companion object {
        const val SERVER_IP = "223.5.5.5"
        const val DOH_TLS_NAME = "dns.alidns.com"
        const val DOH_URL = "https://dns.alidns.com/dns-query"
        const val DOH_IP_URL = "https://223.5.5.5/dns-query"
        const val USER_AGENT = "MarkQ DoH"
        private val DNS_MESSAGE = "application/dns-message".toMediaType()
        private const val MIN_TTL_SEC = 30
        private const val MAX_TTL_SEC = 3600
        private const val DEFAULT_TTL_SEC = 300
        private const val NEGATIVE_TTL_MS = 15_000L

        fun serverAddress(): InetAddress =
            InetAddress.getByAddress(SERVER_IP, byteArrayOf(223.toByte(), 5, 5, 5))

        fun buildEngine(context: Context): CronetEngine {
            val store = context.cacheDir.resolve("cronet-doh").apply { mkdirs() }
            val rules = "MAP $DOH_TLS_NAME $SERVER_IP, MAP $SERVER_IP $SERVER_IP"
            return ExperimentalCronetEngine.Builder(context).apply {
                setStoragePath(store.absolutePath)
                enableHttp2(true)
                enableQuic(true)
                enableBrotli(true)
                enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK_NO_HTTP, 256 * 1024L)
                addQuicHint(DOH_TLS_NAME, 443, 443)
                addQuicHint(SERVER_IP, 443, 443)
                setExperimentalOptions(
                    """{"HostResolverRules":{"host_resolver_rules":"$rules"}}""",
                )
            }.build()
        }
    }
}
