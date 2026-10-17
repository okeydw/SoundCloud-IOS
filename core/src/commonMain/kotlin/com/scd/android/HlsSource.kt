package com.scd.android

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.io.IOException

object HlsSource {
    const val PREFIX = "hls:"
    const val WINDOW = 3

    class Playlist(val init: String?, val segments: List<String>)

    fun isHls(url: String): Boolean = url.startsWith(PREFIX)

    fun wrap(manifest: String): String = PREFIX + manifest

    fun manifest(url: String): String = url.removePrefix(PREFIX)

    fun resolve(base: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        val scheme = base.substringBefore("://", "https")
        if (ref.startsWith("//")) return "$scheme:$ref"
        val origin = scheme + "://" + base.substringAfter("://").substringBefore('/').substringBefore('?')
        if (ref.startsWith("/")) return origin + ref
        val path = base.substringBefore('?').substringBefore('#')
        val dir = if (path.substringAfter("://").contains('/')) path.substringBeforeLast('/') else origin
        return "$dir/$ref"
    }

    fun variant(text: String, base: String): String? {
        if (!text.contains("#EXT-X-STREAM-INF")) return null
        var best: String? = null
        var bestRate = -1L
        var rate = -1L
        var pending = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                rate = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                pending = true
                continue
            }
            if (pending && line.isNotEmpty() && !line.startsWith("#")) {
                if (rate > bestRate) {
                    bestRate = rate
                    best = resolve(base, line)
                }
                pending = false
            }
        }
        return best
    }

    fun parse(text: String, base: String): Playlist {
        var init: String? = null
        val segments = mutableListOf<String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            val mapAt = line.indexOf("#EXT-X-MAP:URI=\"")
            if (mapAt >= 0) {
                val rest = line.substring(mapAt + 16)
                val end = rest.indexOf('"')
                if (end > 0) init = resolve(base, rest.substring(0, end))
                continue
            }
            if (line.startsWith("#EXT-X-KEY") && !line.contains("METHOD=NONE")) throw IOException("hls: encrypted")
            if (line.isEmpty() || line.startsWith("#")) continue
            segments += resolve(base, line)
        }
        return Playlist(init, segments)
    }

    fun hasEnca(b: ByteArray): Boolean {
        for (i in 0..b.size - 4) {
            if (b[i] == 'e'.code.toByte() && b[i + 1] == 'n'.code.toByte() && b[i + 2] == 'c'.code.toByte() && b[i + 3] == 'a'.code.toByte()) return true
        }
        return false
    }

    suspend fun playlist(manifestUrl: String, get: suspend (String) -> ByteArray): Playlist {
        val text = get(manifestUrl).decodeToString()
        val media = variant(text, manifestUrl) ?: return parse(text, manifestUrl)
        return parse(get(media).decodeToString(), media)
    }

    suspend fun download(manifestUrl: String, get: suspend (String) -> ByteArray): ByteArray = coroutineScope {
        val list = playlist(manifestUrl, get)
        if (list.segments.isEmpty()) throw IOException("hls: no segments")
        val parts = ArrayList<ByteArray>(list.segments.size + 1)
        list.init?.let { url ->
            val head = get(url)
            if (hasEnca(head)) throw IOException("hls: encrypted")
            parts += head
        }
        val inflight = ArrayDeque<Deferred<ByteArray>>()
        var next = 0
        fun fill() {
            while (next < list.segments.size && inflight.size < WINDOW) {
                val url = list.segments[next++]
                inflight.addLast(async { get(url) })
            }
        }
        fill()
        while (inflight.isNotEmpty()) {
            parts += inflight.removeFirst().await()
            fill()
        }
        val out = ByteArray(parts.sumOf { it.size })
        var at = 0
        for (p in parts) {
            p.copyInto(out, at)
            at += p.size
        }
        out
    }
}
