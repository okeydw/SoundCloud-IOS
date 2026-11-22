package com.scd.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds

@OptIn(ExperimentalForeignApi::class)
object StreamFiles {
    private val maintenance = Any()
    private val exts = listOf("mp3", "m4a", "ogg", "flac", "wav")
    private val inflight = HashMap<String, Deferred<String?>>()
    private val lock = Any()
    private val validated = HashMap<String, Long>()

    private val dir: String by lazy {
        val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String
        "${caches ?: Dirs.files}/streams".also { Fs.mkdirs(it) }
    }

    private fun base(urn: String) = urn.replace(Regex("[^A-Za-z0-9_-]"), "_")

    fun cached(urn: String): String? = exts.map { "$dir/${base(urn)}.$it" }.firstOrNull { Fs.exists(it) }

    fun readyCached(urn: String): String? = if (locked(lock) { validated.containsKey(urn) }) cached(urn) else null

    fun readyCached(track: Track): String? = if (locked(lock) { validated[track.urn] == expected(track) }) cached(track.urn) else null

    private fun expected(track: Track): Long = maxOf(track.duration, track.full_duration ?: 0L, DurationCache.get(track.urn))

    fun drop(urn: String) = locked(maintenance) {
        locked(lock) { validated.remove(urn) }
        ServedQuality.forget(urn)
        exts.forEach { Fs.delete("$dir/${base(urn)}.$it") }
    }

    fun cachedTracks(): List<Track> = Fs.list(dir).mapNotNull { path ->
        val file = path.substringAfterLast('/')
        val match = Regex("^soundcloud_tracks_([0-9]+)\\.(mp3|m4a|ogg|flac|wav)$").matchEntire(file) ?: return@mapNotNull null
        val urn = "soundcloud:tracks:${match.groupValues[1]}"
        Track(urn = urn, duration = DurationCache.get(urn))
    }.distinctBy { it.urn }

    suspend fun repairCached(track: Track): RepairOutcome = withContext(IoDispatcher) {
        val path = cached(track.urn) ?: return@withContext RepairOutcome.CLEAN
        inspectCache(track, path, background = true)
    }

    private suspend fun inspectCache(track: Track, path: String, background: Boolean): RepairOutcome {
        fun busy() = background && (track.urn == NowPlaying.urn || locked(lock) { track.urn in inflight })
        if (busy()) return RepairOutcome.REMAINING
        val stamp = fileStamp(path) ?: return RepairOutcome.REMAINING
        val bytes = Fs.readAtomic(path) ?: return RepairOutcome.REMAINING
        val prepared = prepare(track, bytes) ?: return RepairOutcome.REMAINING
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        return locked(maintenance) {
            if (busy() || fileStamp(path) != stamp || cached(track.urn) != path) return@locked RepairOutcome.REMAINING
            val changed = prepared !== bytes
            if (changed) Fs.writeAtomic(path, prepared)
            locked(lock) { validated[track.urn] = expected(track) }
            if (changed) RepairOutcome.REPAIRED else RepairOutcome.CLEAN
        }
    }

    private fun fileStamp(path: String): Pair<Long, Double>? {
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, null) ?: return null
        val inode = (attrs[NSFileSystemFileNumber] as? NSNumber)?.longLongValue ?: return null
        val date = attrs[NSFileModificationDate] as? NSDate ?: return null
        return inode to date.timeIntervalSince1970
    }

    private fun qualityFetcher(track: Track) = QualityAudioFetcher(::request, ::source) { prepare(track, it) }

    internal fun prepare(track: Track, bytes: ByteArray): ByteArray? {
        if (!looksLikeAudio(bytes)) return null
        val indexed = AacSeekIndex.normalize(bytes) ?: return null
        val prepared = if (indexed === bytes && ClearMp4.needsRepair(bytes)) bytes.copyOf().also(ClearMp4::repair) else indexed
        val expected = expected(track)
        if (expected <= 45_000L && prepared === bytes) return bytes
        val path = "$dir/probe-${NSUUID().UUIDString}.${extFor(prepared)}"
        val duration = try {
            Fs.writeAtomic(path, prepared)
            val asset = AVURLAsset.URLAssetWithURL(NSURL.fileURLWithPath(path), mapOf(AVURLAssetPreferPreciseDurationAndTimingKey to true))
            if (prepared !== bytes && asset.tracksWithMediaType(AVMediaTypeAudio).isEmpty()) return null
            CMTimeGetSeconds(asset.duration).takeIf { it.isFinite() && it > 0 }?.let { (it * 1000).toLong() }
        } finally {
            Fs.delete(path)
        }
        if (prepared !== bytes && (duration == null || duration < expected - maxOf(1_000L, expected / 25))) return null
        val rejection = AudioCompleteness.rejection(duration, prepared.size, expected)
        if (rejection != null) Logs.add("stream", "$rejection ${track.urn}")
        return prepared.takeIf { rejection == null }
    }

    private fun streamUrls(track: Track): List<String> =
        ((if (Prefs.star) Endpoints.serviceHosts("stream-star") else emptyList()) + Endpoints.serviceHosts("stream"))
            .map { "$it/stream/${formEncode(track.urn)}" }

    suspend fun fetchFor(track: Track, above: Quality, wanted: Quality): QualityAudio? = withContext(IoDispatcher) {
        if (Prefs.offline || !isScUrn(track.urn)) return@withContext null
        qualityFetcher(track).fetch(streamUrls(track), storageUrls(track.urn), Quality.parse(track.scdMeta?.storage_quality), wanted, above)
    }

    fun replace(urn: String, bytes: ByteArray, tier: Quality) = locked(maintenance) {
        require(looksLikeAudio(bytes)) { "Invalid audio cache replacement" }
        val target = "$dir/${base(urn)}.${extFor(bytes)}"
        Fs.writeAtomic(target, bytes)
        exts.map { "$dir/${base(urn)}.$it" }.filter { it != target }.forEach { Fs.delete(it) }
        ServedQuality.record(urn, tier)
        locked(lock) { validated[urn] = DurationCache.get(urn) }
    }

    suspend fun get(track: Track): String? {
        readyCached(track)?.let { return it }
        val job = locked(lock) {
            inflight.getOrPut(track.urn) {
                AppScope.scope.async(IoDispatcher) {
                    cached(track.urn)?.let { path ->
                        if (inspectCache(track, path, background = false) != RepairOutcome.REMAINING) return@async path
                    }
                    val audio = audio(track) ?: return@async null
                    val path = "$dir/${base(track.urn)}.${extFor(audio.bytes)}"
                    replace(track.urn, audio.bytes, audio.tier)
                    locked(lock) { validated[track.urn] = expected(track) }
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

    suspend fun streamingSource(track: Track): Pair<NSURL, Quality>? = withContext(IoDispatcher) {
        val tier = QualityLadder.allowed(QualityLadder.wanted())
        if (!isScUrn(track.urn) || Prefs.offline || Prefs.eqEnabled ||
            (Prefs.pitchMode == SpeedPitch.MODE_MANUAL && Prefs.pitchSemitones != 0f)) return@withContext null
        val endpoint = streamUrls(track).firstOrNull() ?: return@withContext null
        val metadataUrl = QualityLadder.tierUrl(endpoint.replaceFirst("/stream/", "/download/"), tier)
        val (resolved, headers) = Api.authorizedHeaders(metadataUrl)
        val result = Http.execute(HttpRequest(resolved, headers = headers, profile = HttpProfile.QUICK, forceNetwork = true))
        if (!result.isSuccessful) return@withContext null
        val candidates = Api.parseDownload(result.text())?.candidates.orEmpty()
        val selected = if (Prefs.streamQuality == Prefs.QUALITY_AUTO) {
            val stored = Quality.parse(track.scdMeta?.storage_quality)
            (candidates.mapNotNull { Quality.parse(it.quality) } + listOfNotNull(stored))
                .filter { it.rank <= tier.rank }.maxByOrNull { it.rank } ?: return@withContext null
        } else tier
        for (candidate in candidates) {
            val quality = Quality.parse(candidate.quality) ?: continue
            if (quality != selected) continue
            val target = when (candidate.kind) {
                "progressive" -> candidate.url
                "hls" -> candidate.manifest_url
                else -> null
            } ?: continue
            val url = NSURL.URLWithString(target) ?: continue
            val host = url.host?.lowercase() ?: continue
            if (url.scheme != "https" || url.user != null || url.password != null) continue
            if (host.endsWith(".sndcdn.com") || host.endsWith(".soundcloud.cloud")) return@withContext url to quality
        }
        null
    }

    suspend fun bytes(track: Track): ByteArray? = audio(track)?.bytes

    suspend fun audio(track: Track, wanted: Quality = QualityLadder.wanted()): QualityAudio? = withContext(IoDispatcher) {
        if (Prefs.offline) return@withContext null
        if (YouTubeUrn.matches(track.urn)) {
            return@withContext YouTubeImport.fetch(track)?.let { prepare(track, it) }?.let { QualityAudio(it, Quality.SQ) }
        }
        val started = nowMillis()
        val got = qualityFetcher(track).fetch(streamUrls(track), storageUrls(track.urn), Quality.parse(track.scdMeta?.storage_quality), wanted)
            ?: if (isScUrn(track.urn)) {
                val url = try { ScAnon.sourceUrl(track.urn) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
                url?.let { source(it) }?.let { prepare(track, it) }?.let { QualityAudio(it, Quality.SQ) }
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

    fun cacheFiles(): List<String> = Fs.list(dir).filter { path ->
        (path.substringAfterLast('/').startsWith("soundcloud_tracks_") || path.substringAfterLast('/').startsWith("youtube_tracks_")) && exts.any { path.endsWith(".$it") }
    }

    fun sizeOf(path: String): Long =
        (NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L

    suspend fun trim(clear: Boolean = false) = withContext(IoDispatcher) { prune(clear) }

    private fun prune(clear: Boolean = false) = locked(maintenance) {
        val files = cacheFiles().map { StreamCacheFile(it, modified(it), sizeOf(it)) }
        val cutoff = Prefs.cacheDays.takeIf { it > 0 }?.let { nowMillis() / 1000.0 - it * 86_400.0 }
        pruneStreamCache(files, Prefs.cacheLimit, cutoff, clear, protected = { path ->
            val key = path.substringAfterLast('/').substringBeforeLast('.')
            key == NowPlaying.urn?.let(::base) || locked(lock) { inflight.keys.any { base(it) == key } }
        }, remove = Fs::delete)
    }
}
