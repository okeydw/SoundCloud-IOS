package com.scd.android

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object EncHls {
    const val PREFIX = "enchls:"
    const val WINDOW = 3

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Payload(val init: String, val segments: List<String>, val key: String)

    fun isEnc(url: String): Boolean = url.startsWith(PREFIX)

    fun wrap(initBase64: String?, segments: List<String>, keyBase64: String?): String? {
        if (initBase64.isNullOrEmpty() || keyBase64.isNullOrEmpty() || segments.isEmpty()) return null
        val raw = json.encodeToString(Payload.serializer(), Payload(initBase64, segments, keyBase64)).encodeToByteArray()
        return PREFIX + Base64.UrlSafe.encode(raw)
    }

    private fun payload(url: String): Payload {
        val raw = Base64.UrlSafe.decode(url.removePrefix(PREFIX))
        return json.decodeFromString(Payload.serializer(), raw.decodeToString())
    }

    suspend fun download(url: String, get: suspend (String) -> ByteArray): ByteArray = coroutineScope {
        val p = payload(url)
        val key = Base64.decode(p.key)
        if (key.size != 16) throw IOException("enchls: key length ${key.size}")
        val init = Base64.decode(p.init)
        if (init.isEmpty()) throw IOException("enchls: empty init")
        if (p.segments.isEmpty()) throw IOException("enchls: no segments")

        val parts = ArrayList<ByteArray>(p.segments.size + 1)
        parts += init
        val inflight = ArrayDeque<Deferred<ByteArray>>()
        var next = 0
        fun fill() {
            while (next < p.segments.size && inflight.size < WINDOW) {
                val segUrl = p.segments[next++]
                inflight.addLast(async { Cenc.decryptSegment(get(segUrl), key) })
            }
        }
        fill()
        while (inflight.isNotEmpty()) {
            parts += inflight.removeFirst().await()
            fill()
        }
        val out = ByteArray(parts.sumOf { it.size })
        var at = 0
        for (part in parts) {
            part.copyInto(out, at)
            at += part.size
        }
        out
    }
}
