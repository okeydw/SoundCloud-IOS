package com.scd.android

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AndroidNet {
    lateinit var http: OkHttpClient

    val ready: Boolean get() = ::http.isInitialized
}

val Api.http: OkHttpClient get() = AndroidNet.http

fun Api.initHttp(context: Context) {
    if (AndroidNet.ready) return
    ScDataSource.cacheDir = File(context.cacheDir, "goplus").apply { mkdirs() }
    val cache = Cache(File(context.cacheDir, "http"), 300L * 1024 * 1024)
    val dispatcher = okhttp3.Dispatcher().apply {
        maxRequests = 96
        maxRequestsPerHost = 24
    }
    AndroidNet.http = OkHttpClient.Builder()
        .cache(cache)
        .dispatcher(dispatcher)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(NetMonitor.offlineInterceptor(context))
        .addNetworkInterceptor { chain ->
            val req = chain.request()
            if (Endpoints.trustedHost(req.url.host)) return@addNetworkInterceptor chain.proceed(req)
            val hasSession = req.header("x-session-id") != null || req.url.queryParameter("session_id") != null
            if (!hasSession) return@addNetworkInterceptor chain.proceed(req)
            val cleanUrl = req.url.newBuilder().removeAllQueryParameters("session_id").build()
            chain.proceed(req.newBuilder().url(cleanUrl).removeHeader("x-session-id").build())
        }
        .addNetworkInterceptor { chain ->
            val req = chain.request()
            val res = chain.proceed(req)
            val host = req.url.host
            val path = req.url.encodedPath
            val isApi = host in Endpoints.apiHostnames
            val maxAge = when {
                path.startsWith("/auth") || path.startsWith("/health") ||
                    path.startsWith("/recommendations") -> null
                host in Endpoints.imageHostnames || host.endsWith("sndcdn.com") -> 604800
                isApi && (
                    path.contains("/playlists") ||
                        path.startsWith("/users/") ||
                        path.startsWith("/me/likes") ||
                        path.startsWith("/me/playlists")
                    ) -> 604800
                isApi && (path.startsWith("/tracks") || path.startsWith("/history")) -> 3600
                isApi -> 60
                else -> null
            }
            if (req.method == "GET" && maxAge != null && res.code == 200) {
                res.newBuilder()
                    .removeHeader("Pragma")
                    .header("Cache-Control", "public, max-age=$maxAge")
                    .build()
            } else res
        }
        .build()
    Http.engine = OkHttpEngine()
    onSessionCleared = { runCatching { AndroidNet.http.cache?.evictAll() } }
}

fun Api.loadSession(context: Context) {
    attachStore(AndroidKvStore(context, "scd"))
    loadSession()
}

fun Api.storeSession(context: Context, id: String?) {
    attachStore(AndroidKvStore(context, "scd"))
    storeSession(id)
}

fun Api.authorize(builder: Request.Builder, url: String): Request.Builder {
    val (finalUrl, sid) = streamAuth(url) ?: return builder
    builder.header("x-session-id", sid)
    if (finalUrl == url) return builder
    return builder.url(finalUrl)
}

class OkHttpEngine : HttpEngine {
    private val clients = ConcurrentHashMap<HttpProfile, OkHttpClient>()

    private fun client(p: HttpProfile): OkHttpClient = clients.getOrPut(p) { build(p) }

    private fun build(p: HttpProfile): OkHttpClient {
        val http = AndroidNet.http
        return when (p) {
            HttpProfile.BASE -> http
            HttpProfile.API -> http.newBuilder()
                .callTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .build()
            HttpProfile.MUTATE -> http.newBuilder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .build()
            HttpProfile.SLOW -> http.newBuilder()
                .callTimeout(180, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .build()
            HttpProfile.PROBE -> ScDataSource.probeClient(http)
            HttpProfile.SHORT_LINK -> ScDataSource.probeClient(http).newBuilder()
                .cache(null)
                .followRedirects(true)
                .build()
            HttpProfile.LOGOUT -> http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
            HttpProfile.REFRESH -> http.newBuilder().callTimeout(45, TimeUnit.SECONDS).build()
            HttpProfile.VOLUME -> http.newBuilder()
                .cache(null)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
            HttpProfile.QUICK -> http.newBuilder()
                .cache(null)
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .build()
            HttpProfile.ANON -> http.newBuilder()
                .cache(null)
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .build()
            HttpProfile.ANON_HOME -> client(HttpProfile.ANON).newBuilder()
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }

    private fun request(r: HttpRequest): Request {
        val body = r.body?.let { b -> b.bytes.toRequestBody(b.contentType?.toMediaType()) }
        return Request.Builder()
            .url(r.url)
            .apply {
                r.headers.forEach { (k, v) -> header(k, v) }
                if (r.forceNetwork) cacheControl(CacheControl.FORCE_NETWORK)
            }
            .method(r.method, body)
            .build()
    }

    override suspend fun execute(req: HttpRequest): HttpResponse {
        val call = client(req.profile).newCall(request(req))
        val res = if (req.queued) call.await() else call.execute()
        return res.use { r ->
            val headers = HashMap<String, String>()
            for ((name, value) in r.headers) headers[name.lowercase()] = value
            val bytes = if (req.discardBody) ByteArray(0) else r.body?.bytes() ?: ByteArray(0)
            HttpResponse(r.code, headers, bytes, r.request.url.toString())
        }
    }

    override fun <T> stream(req: HttpRequest, block: (HttpStream) -> T): T =
        client(req.profile).newCall(request(req)).execute().use { res ->
            val input = res.body?.byteStream()
            block(object : HttpStream {
                override val code: Int = res.code
                override fun header(name: String): String? = res.header(name)
                override fun read(buf: ByteArray): Int = input?.read(buf) ?: -1
            })
        }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }
}
