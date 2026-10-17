package com.scd.android

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

object HlsDownload {
    private const val WINDOW = HlsSource.WINDOW

    private val pool = Executors.newFixedThreadPool(WINDOW * 2) { r -> Thread(r, "hls-segment").apply { isDaemon = true } }

    fun isHls(url: String): Boolean = HlsSource.isHls(url)

    fun wrap(manifest: String): String = HlsSource.wrap(manifest)

    fun manifest(url: String): String = HlsSource.manifest(url)

    private fun get(client: OkHttpClient, url: String): ByteArray {
        val req = Request.Builder().url(url).header("User-Agent", ScAnon.USER_AGENT).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("hls HTTP ${res.code}")
            return res.body?.bytes() ?: throw IOException("hls empty body")
        }
    }

    private fun playlist(client: OkHttpClient, manifestUrl: String): HlsSource.Playlist {
        val text = get(client, manifestUrl).decodeToString()
        val media = HlsSource.variant(text, manifestUrl) ?: return HlsSource.parse(text, manifestUrl)
        return HlsSource.parse(get(client, media).decodeToString(), media)
    }

    fun writeTo(client: OkHttpClient, manifestUrl: String, target: File) {
        val list = playlist(client, manifestUrl)
        val segments = list.segments
        if (segments.isEmpty()) throw IOException("hls: no segments")
        target.parentFile?.mkdirs()
        val inflight = ArrayDeque<Future<ByteArray>>()
        try {
            target.outputStream().buffered().use { out ->
                list.init?.let { init ->
                    val head = get(client, init)
                    if (HlsSource.hasEnca(head)) throw IOException("hls: encrypted")
                    out.write(head)
                }
                var next = 0
                fun fill() {
                    while (next < segments.size && inflight.size < WINDOW) {
                        val url = segments[next++]
                        inflight.addLast(pool.submit(Callable { get(client, url) }))
                    }
                }
                fill()
                while (inflight.isNotEmpty()) {
                    if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("hls cancelled")
                    out.write(inflight.removeFirst().get(45, TimeUnit.SECONDS))
                    fill()
                }
            }
        } catch (e: java.util.concurrent.ExecutionException) {
            target.delete()
            throw (e.cause as? IOException) ?: IOException("hls segment: ${e.cause?.javaClass?.simpleName}")
        } catch (e: java.util.concurrent.TimeoutException) {
            target.delete()
            throw java.net.SocketTimeoutException("hls segment timeout")
        } catch (e: InterruptedException) {
            target.delete()
            throw java.io.InterruptedIOException("hls cancelled")
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            inflight.forEach { it.cancel(true) }
        }
        if (!Downloads.looksLikeAudio(target)) {
            target.delete()
            throw IOException("hls: not audio")
        }
    }
}
