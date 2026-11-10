package com.scd.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds

@OptIn(ExperimentalForeignApi::class)
object StreamFiles {
    private const val KEEP = 25
    private val exts = listOf("mp3", "m4a", "ogg", "flac", "wav")
    private val inflight = HashMap<String, Deferred<String?>>()
    private val lock = Any()
    private val validated = HashSet<String>()

    private val dir: String by lazy {
        val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String
        "${caches ?: Dirs.files}/streams".also { Fs.mkdirs(it) }
    }

    private fun base(urn: String) = urn.replace(Regex("[^A-Za-z0-9_-]"), "_")

    fun cached(urn: String): String? = exts.map { "$dir/${base(urn)}.$it" }.firstOrNull { Fs.exists(it) }

    fun readyCached(urn: String): String? = if (locked(lock) { urn in validated }) cached(urn) else null

    fun drop(urn: String) {
        locked(lock) { validated.remove(urn) }
        ServedQuality.forget(urn)
        exts.forEach { Fs.delete("$dir/${base(urn)}.$it") }
    }

    private fun qualityFetcher(track: Track) = QualityAudioFetcher(::request, ::source) { accepts(track, it) }

    fun accepts(track: Track, bytes: ByteArray): Boolean {
        if (!looksLikeAudio(bytes)) return false
        ClearMp4.repair(bytes)
        val expected = maxOf(track.duration, track.full_duration ?: 0L)
        if (expected <= 45_000L) return true
        val path = "$dir/probe-${NSUUID().UUIDString}.${extFor(bytes)}"
        val duration = try {
            Fs.writeAtomic(path, bytes)
            val asset = AVURLAsset.URLAssetWithURL(NSURL.fileURLWithPath(path), mapOf(AVURLAssetPreferPreciseDurationAndTimingKey to true))
            CMTimeGetSeconds(asset.duration).takeIf { it.isFinite() && it > 0 }?.let { (it * 1000).toLong() }
        } finally {
            Fs.delete(path)
        }
        val rejection = AudioCompleteness.rejection(duration, bytes.size, expected)
        if (rejection != null) Logs.add("stream", "$rejection ${track.urn}")
        return rejection == null
    }

    private fun streamUrls(track: Track): List<String> =
        ((if (Prefs.star) Endpoints.serviceHosts("stream-star") else emptyList()) + Endpoints.serviceHosts("stream"))
            .map { "$it/stream/${formEncode(track.urn)}" }

    suspend fun fetchFor(track: Track, above: Quality, wanted: Quality): QualityAudio? = withContext(IoDispatcher) {
        if (Prefs.offline || !isScUrn(track.urn)) return@withContext null
        qualityFetcher(track).fetch(streamUrls(track), storageUrls(track.urn), Quality.parse(track.scdMeta?.storage_quality), wanted, above)
    }

    fun replace(urn: String, bytes: ByteArray, tier: Quality) {
        require(looksLikeAudio(bytes)) { "Invalid audio cache replacement" }
        val target = "$dir/${base(urn)}.${extFor(bytes)}"
        Fs.writeAtomic(target, bytes)
        exts.map { "$dir/${base(urn)}.$it" }.filter { it != target }.forEach { Fs.delete(it) }
        ServedQuality.record(urn, tier)
        locked(lock) { validated.add(urn) }
    }

    suspend fun get(track: Track): String? {
        readyCached(track.urn)?.let { return it }
        cached(track.urn)?.let { path ->
            val valid = withContext(IoDispatcher) {
                val bytes = Fs.readAtomic(path)
                val repair = bytes?.let(ClearMp4::needsRepair) == true
                if (bytes != null && accepts(track, bytes)) {
                    if (repair) Fs.writeAtomic(path, bytes)
                    true
                } else {
                    drop(track.urn)
                    false
                }
            }
            if (valid) {
                locked(lock) { validated.add(track.urn) }
                return path
            }
        }
        val job = locked(lock) {
            inflight.getOrPut(track.urn) {
                AppScope.scope.async(IoDispatcher) {
                    val audio = audio(track) ?: return@async null
                    val path = "$dir/${base(track.urn)}.${extFor(audio.bytes)}"
                    Fs.writeAtomic(path, audio.bytes)
                    ServedQuality.record(track.urn, audio.tier)
                    locked(lock) { validated.add(track.urn) }
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

    suspend fun bytes(track: Track): ByteArray? = audio(track)?.bytes

    suspend fun audio(track: Track, wanted: Quality = QualityLadder.wanted()): QualityAudio? = withContext(IoDispatcher) {
        if (Prefs.offline) return@withContext null
        val started = nowMillis()
        val got = qualityFetcher(track).fetch(streamUrls(track), storageUrls(track.urn), Quality.parse(track.scdMeta?.storage_quality), wanted)
            ?: if (isScUrn(track.urn)) {
                val url = try { ScAnon.sourceUrl(track.urn) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
                url?.let { source(it) }?.takeIf { accepts(track, it) }?.let { QualityAudio(it, Quality.SQ) }
            } else null
        got?.also {
            Logs.add("stream", "${track.title.take(24)}: ${it.tier.badge} ${it.bytes.size / 1024}K in ${(nowMillis() - started) / 1000}s")
        }
    }

    private suspend fun source(url: String): ByteArray? {
        if (EncHls.isEnc(url)) {
            return runCatching {
                EncHls.download(url) { part ->
                    val (target, headers) = Api.authorizedHeaders(part)
                    val res = Http.execute(HttpRequest(target, headers = headers, profile = HttpProfile.SLOW, forceNetwork = true))
                    if (!res.isSuccessful) throw kotlinx.io.IOException("encrypted HLS HTTP ${res.code}")
                    res.body
                }.takeIf(::looksLikeAudio)
            }.getOrElse { if (it is CancellationException) throw it; null }
        }
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
        val bases = (if (Prefs.star) Endpoints.serviceHosts("storage-star") else emptyList()) + Endpoints.serviceHosts("storage")
        return bases.map { "$it/$file" }
            .filter { !storageCooling(it.substringAfter("://").substringBefore('/')) }
            .sortedBy { if (it.substringAfter("://").substringBefore('/') == lastGoodStorage) 0 else 1 }
    }

    private suspend fun fetch(url: String): ByteArray? =
        request(url)?.takeIf { it.isSuccessful && looksLikeAudio(it.body) }?.body

    private suspend fun request(url: String): HttpResponse? {
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
            if (res.code == 401 && url.contains("/stream/")) SessionState.streamDenied()
            return res
        }
        if (storage && looksLikeAudio(res.body)) {
            lastGoodStorage = host
            locked(lock) { storageDown.remove(host) }
        }
        return res
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
        val files = Fs.list(dir).filter { path -> !path.substringAfterLast('/').startsWith("probe-") && exts.any { path.endsWith(".$it") } }
        if (files.size <= KEEP) return
        val busy = locked(lock) { inflight.keys.map(::base).toSet() }
        files.map { it to modified(it) }
            .sortedBy { it.second }
            .take(files.size - KEEP)
            .forEach { (path, _) -> if (path.substringAfterLast('/').substringBeforeLast('.') !in busy) Fs.delete(path) }
    }
}
