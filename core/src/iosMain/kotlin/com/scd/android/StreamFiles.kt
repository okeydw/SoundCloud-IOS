package com.scd.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
object StreamFiles {
    private const val KEEP = 25
    private val exts = listOf("mp3", "m4a", "ogg", "flac", "wav")
    private val inflight = HashMap<String, Deferred<String?>>()
    private val lock = Any()

    private val dir: String by lazy {
        val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String
        "${caches ?: Dirs.files}/streams".also { Fs.mkdirs(it) }
    }

    private fun base(urn: String) = urn.replace(Regex("[^A-Za-z0-9_-]"), "_")

    fun cached(urn: String): String? = exts.map { "$dir/${base(urn)}.$it" }.firstOrNull { Fs.exists(it) }

    fun drop(urn: String) {
        exts.forEach { Fs.delete("$dir/${base(urn)}.$it") }
    }

    suspend fun get(track: Track): String? {
        cached(track.urn)?.let { return it }
        val job = locked(lock) {
            inflight.getOrPut(track.urn) {
                AppScope.scope.async(IoDispatcher) {
                    val bytes = bytes(track) ?: return@async null
                    val path = "$dir/${base(track.urn)}.${extFor(bytes)}"
                    Fs.writeAtomic(path, bytes)
                    prune()
                    path
                }
            }
        }
        return try {
            job.await()
        } finally {
            locked(lock) { if (inflight[track.urn] === job) inflight.remove(track.urn) }
        }
    }

    suspend fun bytes(track: Track): ByteArray? = withContext(IoDispatcher) {
        if (Prefs.offline) return@withContext null
        val started = nowMillis()
        val storage = storageUrls(track.urn)
        val storageFirst = Prefs.storageFirst || track.goPlus || (Prefs.star && Prefs.hqStreaming)
        fun done(b: ByteArray, from: String): ByteArray {
            Logs.add("stream", "${track.title.take(24)}: ${b.size / 1024}K from $from in ${(nowMillis() - started) / 1000}s")
            return b
        }
        fun host(url: String) = url.substringAfter("://").substringBefore('/')
        if (storageFirst) firstOk(storage)?.let { (u, b) -> return@withContext done(b, host(u)) }
        firstOk(StreamPicker.candidates(track))?.let { (u, b) -> return@withContext done(b, host(u)) }
        viaDownload(Api.streamUrl(track.urn, goPlus = track.goPlus))?.let { return@withContext done(it, "download") }
        if (!storageFirst) firstOk(storage)?.let { (u, b) -> return@withContext done(b, host(u)) }
        if (isScUrn(track.urn)) {
            runCatching { ScAnon.sourceUrl(track.urn) }.getOrNull()?.let { url -> source(url)?.let { return@withContext done(it, "anon") } }
        }
        null
    }

    private suspend fun firstOk(urls: List<String>): Pair<String, ByteArray>? {
        for (url in urls) fetch(url)?.let { return url to it }
        return null
    }

    private suspend fun source(url: String): ByteArray? {
        if (!HlsSource.isHls(url)) return fetch(url)
        val manifest = HlsSource.manifest(url)
        val host = manifest.substringAfter("://").substringBefore('/')
        val body = runCatching {
            HlsSource.download(manifest) { part ->
                val res = Http.execute(HttpRequest(part, headers = listOf("User-Agent" to ScAnon.USER_AGENT), profile = HttpProfile.SLOW, forceNetwork = true))
                if (!res.isSuccessful) throw kotlinx.io.IOException("hls HTTP ${res.code}")
                res.body
            }
        }.getOrElse {
            if (it is CancellationException) throw it
            Logs.add("stream", "hls ${it.message ?: it::class.simpleName} @$host")
            return null
        }
        if (!looksLikeAudio(body)) {
            Logs.add("stream", "hls not audio (${body.size} B) @$host")
            return null
        }
        return body
    }

    @kotlin.concurrent.Volatile
    private var lastGoodStorage: String? = null

    private val storageDown = HashMap<String, Long>()

    private fun storageCooling(host: String): Boolean = locked(lock) {
        val at = storageDown[host] ?: return@locked false
        nowMillis() - at < 60_000L
    }

    private fun storageUrls(urn: String): List<String> {
        if (!urn.startsWith("soundcloud:tracks:")) return emptyList()
        val file = urn.replace(':', '_') + ".m4a"
        val bases = if (Prefs.star) listOf(Endpoints.STORAGE_STAR, Endpoints.STORAGE_MAIN) else listOf(Endpoints.STORAGE_MAIN)
        return bases.map { "$it/$file" }
            .filter { !storageCooling(it.substringAfter("://").substringBefore('/')) }
            .sortedBy { if (it.substringAfter("://").substringBefore('/') == lastGoodStorage) 0 else 1 }
    }

    private suspend fun fetch(url: String): ByteArray? {
        val (u, h) = Api.authorizedHeaders(url)
        val host = u.substringAfter("://").substringBefore('/')
        val storage = host.startsWith("storage")
        val res = runCatching {
            Http.execute(HttpRequest(u, headers = h, profile = HttpProfile.SLOW, forceNetwork = true))
        }.getOrElse {
            if (it is CancellationException) throw it
            Logs.add("stream", "fail ${it::class.simpleName} @$host")
            if (storage) locked(lock) { storageDown[host] = nowMillis() }
            return null
        }
        if (!res.isSuccessful) {
            if (!(host.startsWith("storage") && res.code == 404)) Logs.add("stream", "HTTP ${res.code} @$host")
            if (res.code == 401 && u.contains("/stream/")) SessionState.streamDenied()
            return null
        }
        if (!looksLikeAudio(res.body)) {
            Logs.add("stream", "not audio (${res.body.size} B, ${res.header("content-type") ?: "?"}) @$host")
            return null
        }
        if (storage) {
            lastGoodStorage = host
            locked(lock) { storageDown.remove(host) }
        }
        return res.body
    }

    private suspend fun viaDownload(streamUrl: String): ByteArray? {
        val url = streamUrl.replaceFirst("/stream/", "/download/")
        val (u, h) = Api.authorizedHeaders(url)
        val res = runCatching { Http.execute(HttpRequest(u, headers = h, profile = HttpProfile.PROBE, forceNetwork = true)) }.getOrNull() ?: return null
        if (!res.isSuccessful) {
            Logs.add("stream", "download HTTP ${res.code}")
            return null
        }
        val parsed = Api.parseDownload(res.text()) ?: return null
        val hqPref = streamUrl.contains("hq=true") || (Prefs.star && Prefs.hqStreaming)
        val usable = parsed.candidates
            .filter { it.quality != "lq" }
            .filter { (it.kind == "progressive" && !it.url.isNullOrEmpty()) || (it.kind == "hls" && !it.manifest_url.isNullOrEmpty()) }
            .sortedWith(compareBy({ if ((it.quality == "hq") == hqPref) 0 else 1 }, { if (it.kind == "progressive") 0 else 1 }))
        if (usable.isEmpty()) {
            Logs.add("stream", "download: no usable stream (${parsed.candidates.joinToString(",") { it.kind }})")
            return null
        }
        for (c in usable) {
            val pick = if (c.kind == "progressive") c.url!! else HlsSource.wrap(c.manifest_url!!)
            source(pick)?.let { return it }
        }
        return null
    }

    fun looksLikeAudio(b: ByteArray): Boolean {
        if (b.size < 8_192) return false
        fun at(i: Int, s: String) = s.indices.all { b[i + it] == s[it].code.toByte() }
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF
        return at(4, "ftyp") || at(0, "ID3") || at(0, "OggS") || at(0, "RIFF") || at(0, "fLaC") ||
            (b0 == 0xFF && (b1 and 0xE0) == 0xE0)
    }

    fun extFor(b: ByteArray): String {
        fun at(i: Int, s: String) = b.size > i + s.length && s.indices.all { b[i + it] == s[it].code.toByte() }
        return when {
            at(4, "ftyp") -> "m4a"
            at(0, "OggS") -> "ogg"
            at(0, "fLaC") -> "flac"
            at(0, "RIFF") -> "wav"
            else -> "mp3"
        }
    }

    private fun modified(path: String): Double =
        (NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileModificationDate) as? NSDate)?.timeIntervalSince1970 ?: 0.0

    private fun prune() {
        val files = Fs.list(dir).filter { path -> exts.any { path.endsWith(".$it") } }
        if (files.size <= KEEP) return
        val busy = locked(lock) { inflight.keys.map(::base).toSet() }
        files.map { it to modified(it) }
            .sortedBy { it.second }
            .take(files.size - KEEP)
            .forEach { (path, _) -> if (path.substringAfterLast('/').substringBeforeLast('.') !in busy) Fs.delete(path) }
    }
}
