package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

actual object Downloads {
    actual val UPGRADE_KEY: String = "upgrade-quality"

    actual var downloaded by mutableStateOf(setOf<String>())
    actual var inProgress by mutableStateOf(setOf<String>())
    actual var queued by mutableStateOf(setOf<String>())
    actual var activeLists by mutableStateOf(setOf<String>())

    private val json = Json { ignoreUnknownKeys = true }
    private val index = LinkedHashMap<String, Track>()
    private val lock = Any()
    private val mutex = Mutex()
    private val artworkMutex = Mutex()
    private val qualityStore by lazy { IosKvStore("download_quality") }
    private var job: Job? = null
    private var upgradeJob: Job? = null
    private val pending = ArrayDeque<Pair<Track, String?>>()
    private val validated = HashMap<String, Long>()
    private val revisions = HashMap<String, Int>()

    private val dir: String get() = Dirs.sub("downloads")
    private val indexPath: String get() = "$dir/index.json"

    fun init() {
        val list = runCatching {
            Fs.readAtomic(indexPath)?.decodeToString()?.let { json.decodeFromString(ListSerializer(Track.serializer()), it) }
        }.getOrNull().orEmpty()
        locked(lock) {
            index.clear()
            list.forEach { if (Fs.exists(audioPath(it.urn))) index[it.urn] = it }
        }
        downloaded = locked(lock) { index.keys.toSet() }
    }

    private fun base(urn: String) = urn.replace(Regex("[^A-Za-z0-9_-]"), "_")
    private val exts = listOf("mp3", "m4a", "ogg", "flac", "wav", "audio")
    private fun audioPath(urn: String): String =
        exts.map { "$dir/${base(urn)}.$it" }.firstOrNull { Fs.exists(it) } ?: "$dir/${base(urn)}.audio"
    private fun artFile(urn: String) = "$dir/${base(urn)}.jpg"

    private fun save() {
        val snapshot = locked(lock) { index.values.toList() }
        runCatching { Fs.writeAtomic(indexPath, json.encodeToString(ListSerializer(Track.serializer()), snapshot).encodeToByteArray()) }
    }

    actual fun isDownloaded(urn: String): Boolean = urn in downloaded

    actual fun tracks(): List<Track> = locked(lock) { index.values.toList() }.reversed()

    internal actual fun savedTrack(urn: String): Track? = locked(lock) { index[urn] }

    actual fun cachedQuality(urn: String): String? {
        if (!isDownloaded(urn)) return null
        val tier = Quality.parse(qualityStore.getString(urn, null)) ?: return null
        return "${tier.badge} ${audioPath(urn).substringAfterLast('.').uppercase()}"
    }

    actual suspend fun quality(urn: String): String? = cachedQuality(urn)

    actual fun enqueue(
        context: PlatformContext,
        tracks: List<Track>,
        playlistUrn: String?,
        playlistTitle: String?,
        listKey: String?,
    ) {
        locked(pending) {
            val fresh = tracks.distinctBy { it.urn }.filter { !isDownloaded(it.urn) && it.urn !in queued }
            if (fresh.isNotEmpty()) {
                fresh.forEach { pending.addLast(it to listKey) }
                queued = queued + fresh.map { it.urn }
                listKey?.let { activeLists = activeLists + it }
                if (job == null) job = AppScope.scope.launch { drain() }
            }
        }
    }

    private suspend fun drain() {
        while (true) {
            val next = locked(pending) { pending.removeFirstOrNull().also { if (it == null) job = null } } ?: return
            val (track, key) = next
            try {
                mutex.withLock {
                    inProgress = inProgress + track.urn
                    val automatic = key == IosLikesAutoCache.KEY
                    if (automatic && (!Prefs.autoCacheLikes || Prefs.offline || PlatformHooks.meteredBlocked())) return@withLock
                    val ok = try { fetch(track) }
                    catch (_: CancellationException) {
                        currentCoroutineContext().ensureActive()
                        false
                    } catch (_: Exception) { false }
                    if (automatic) {
                        if (ok) DownloadFailures.clear(track.urn) else DownloadFailures.record(track.urn)
                    }
                    if (ok) {
                        locked(lock) { index[track.urn] = track }
                        downloaded = downloaded + track.urn
                        save()
                        WaveCache.prefetch(track.urn, track.waveform_url)
                        Logs.add("download", "${track.title.take(30)} ✓")
                    } else {
                        Logs.add("download", "${track.title.take(30)} failed")
                    }
                }
            } finally {
                inProgress = inProgress - track.urn
                locked(pending) {
                    queued = queued - track.urn
                    if (key != null && pending.none { it.second == key }) activeLists = activeLists - key
                }
            }
        }
    }

    private suspend fun fetch(track: Track): Boolean = withContext(IoDispatcher) {
        val wanted = QualityLadder.downloadWanted()
        val cached = StreamFiles.cached(track.urn)?.let { Fs.readAtomic(it) }
            ?.let { StreamFiles.prepare(track, it) }?.let { QualityAudio(it, ServedQuality.of(track.urn) ?: Quality.SQ) }
        val audio = if (cached != null && cached.tier.rank >= wanted.rank) cached else {
            val fresh = StreamFiles.audio(track, wanted)
            listOfNotNull(cached, fresh).maxByOrNull { it.tier.rank }
        } ?: return@withContext false
        storeAudio(track.urn, audio)
        saveArtwork(track)
        true
    }

    private suspend fun saveArtwork(track: Track, recover: Boolean = false) {
        val source = track.artwork_url?.takeIf { it.isNotBlank() } ?: if (recover && isScUrn(track.urn)) {
            try {
                val fresh = Api.trackByUrn(track.urn)
                fresh.artwork_url ?: fresh.user?.avatar_url
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } else null
        (source ?: track.user?.avatar_url)?.let { art ->
            runCatching {
                val big = Api.artworkUrl(art) ?: return@runCatching
                val (n, v) = Api.imageProxyTarget(big)
                val img = Http.execute(HttpRequest("${Api.IMAGES_BASE}/?t=${formEncode(v)}", headers = listOf(n to v)))
                if (img.isSuccessful && img.body.isNotEmpty() && Fs.exists(audioPath(track.urn))) Fs.writeAtomic(artFile(track.urn), img.body)
            }
        }
    }

    private fun storeAudio(urn: String, audio: QualityAudio) {
        val target = "$dir/${base(urn)}.${StreamFiles.extFor(audio.bytes)}"
        Fs.writeAtomic(target, audio.bytes)
        locked(lock) { validated.remove(urn); revisions[urn] = (revisions[urn] ?: 0) + 1 }
        exts.map { "$dir/${base(urn)}.$it" }.filter { it != target }.forEach { Fs.delete(it) }
        qualityStore.edit().putString(urn, audio.tier.label).apply()
    }

    actual fun cancelList(context: PlatformContext, key: String) {
        if (key == UPGRADE_KEY) upgradeJob?.cancel()
        locked(pending) {
            val gone = pending.filter { it.second == key }.map { it.first.urn }.toSet()
            pending.removeAll { it.second == key }
            queued = queued - gone
            activeLists = activeLists - key
        }
    }

    actual suspend fun remove(urn: String) = removeAll(listOf(urn))

    actual suspend fun removeAll(urns: Collection<String>) = mutex.withLock {
        withContext(IoDispatcher) {
            urns.forEach { u ->
                exts.forEach { e -> Fs.delete("$dir/${base(u)}.$e") }
                Fs.delete(artFile(u))
                qualityStore.edit().remove(u).apply()
                locked(lock) { index.remove(u); validated.remove(u); revisions[u] = (revisions[u] ?: 0) + 1 }
            }
            save()
        }
        downloaded = downloaded - urns.toSet()
    }

    actual fun artPath(urn: String): String? = artFile(urn).takeIf { isDownloaded(urn) && Fs.exists(it) }

    actual fun localPath(urn: String): String? = audioPath(urn).takeIf { isDownloaded(urn) }

    fun readyLocal(track: Track): String? = locked(lock) {
        val expected = maxOf(track.duration, track.full_duration ?: 0L, DurationCache.get(track.urn))
        localPath(track.urn)?.takeIf { validated[track.urn] == expected && Fs.exists(it) }
    }

    suspend fun playablePath(track: Track, forceCheck: Boolean = false, allowNetworkRepair: Boolean = true, onRepair: () -> Unit = {}): String? {
        if (forceCheck && (track.urn == NowPlaying.urn || track.urn in inProgress)) return null
        if (!forceCheck) readyLocal(track)?.let { return it }
        if (!isDownloaded(track.urn)) return null
        val revision = locked(lock) { revisions[track.urn] ?: 0 }
        val path = localPath(track.urn)?.takeIf { Fs.exists(it) } ?: return null
        val valid = withContext(IoDispatcher) {
            val bytes = Fs.readAtomic(path) ?: return@withContext null
            StreamFiles.prepare(track, bytes)?.let { it to (it !== bytes) }
        }
        if (valid?.second == false) return locked(lock) {
            if (!isDownloaded(track.urn) || (revisions[track.urn] ?: 0) != revision || !Fs.exists(path)) null
            else {
                validated[track.urn] = maxOf(track.duration, track.full_duration ?: 0L, DurationCache.get(track.urn))
                path
            }
        }
        val audio = if (valid == null) {
            if (Prefs.offline || !allowNetworkRepair) return null
            StreamFiles.audio(track, QualityLadder.downloadWanted()) ?: return null
        } else null
        return mutex.withLock {
            if (!isDownloaded(track.urn)) return@withLock null
            if (forceCheck && (track.urn == NowPlaying.urn || track.urn in inProgress)) return@withLock null
            if (locked(lock) { (revisions[track.urn] ?: 0) != revision }) return@withLock readyLocal(track)
            withContext(IoDispatcher) {
                currentCoroutineContext().ensureActive()
                if (audio != null) {
                    storeAudio(track.urn, audio)
                    WaveCache.prefetch(track.urn, track.waveform_url)
                    Logs.add("cache", "repaired ${track.urn}")
                } else if (valid?.second == true) {
                    Fs.writeAtomic(path, valid.first)
                    locked(lock) { revisions[track.urn] = revision + 1 }
                    Logs.add("cache", "repaired local audio ${track.urn}")
                }
            }
            if (audio != null || valid?.second == true) onRepair()
            locked(lock) { validated[track.urn] = maxOf(track.duration, track.full_duration ?: 0L, DurationCache.get(track.urn)) }
            localPath(track.urn)
        }
    }

    suspend fun repairCached() {
        val migration = IosKvStore("audio_integrity")
        if (migration.getInt("repair_revision", 0) >= 2) return
        var complete = true
        for (track in tracks()) {
            currentCoroutineContext().ensureActive()
            if (!isScUrn(track.urn)) continue
            if (track.urn == NowPlaying.urn || track.urn in inProgress) {
                complete = false
                continue
            }
            try {
                val allowNetwork = !Prefs.offline && !PlatformHooks.meteredBlocked()
                if (playablePath(track, forceCheck = true, allowNetworkRepair = allowNetwork) == null) complete = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                complete = false
                Logs.add("cache", "repair ${track.urn}: ${e::class.simpleName}")
            }
        }
        if (complete) migration.edit().putInt("repair_revision", 2).apply()
    }

    actual fun upgradeAll(context: PlatformContext, onResult: (Int) -> Unit) {
        if (Prefs.offline || upgradeJob?.isActive == true) return
        val wanted = QualityLadder.allowed(QualityLadder.upgradeWanted())
        val targets = tracks().filter {
            val known = cachedQuality(it.urn) ?: return@filter false
            isScUrn(it.urn) && Quality.fromLabel(known).rank < wanted.rank
        }
        onResult(targets.size)
        if (targets.isEmpty()) return
        activeLists = activeLists + UPGRADE_KEY
        upgradeJob = AppScope.scope.launch {
            try {
                for (track in targets) {
                    currentCoroutineContext().ensureActive()
                    if (Prefs.offline) break
                    mutex.withLock {
                        if (!isDownloaded(track.urn)) return@withLock
                        inProgress = inProgress + track.urn
                        try {
                            withContext(IoDispatcher) {
                                val known = cachedQuality(track.urn) ?: return@withContext
                                val current = Quality.fromLabel(known)
                                if (current.rank >= wanted.rank) return@withContext
                                val audio = TrackTurns.withTurn(TrackTurns.Priority.BULK) {
                                    StreamFiles.fetchFor(track, above = current, wanted = wanted)
                                } ?: return@withContext
                                currentCoroutineContext().ensureActive()
                                if (audio.tier.rank > current.rank) {
                                    storeAudio(track.urn, audio)
                                    WaveCache.prefetch(track.urn, track.waveform_url)
                                    Logs.add("download", "${track.title.take(30)}: ${current.badge} → ${audio.tier.badge}")
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Logs.add("download", "upgrade ${track.urn}: ${e::class.simpleName}")
                        } finally {
                            inProgress = inProgress - track.urn
                        }
                    }
                }
            } finally {
                activeLists = activeLists - UPGRADE_KEY
            }
        }
    }

    actual suspend fun backfillArt() {
        restoreArtwork()
    }

    suspend fun restoreArtwork(): Pair<Int, Int> = withContext(IoDispatcher) {
        artworkMutex.withLock {
            val session = Api.sessionId
            val missing = tracks().filter { StreamFiles.sizeOf(artFile(it.urn)) == 0L }
            var restored = 0
            for (track in missing) {
                currentCoroutineContext().ensureActive()
                if (Prefs.offline || PlatformHooks.meteredBlocked() || Api.sessionId != session) break
                saveArtwork(track, recover = true)
                if (StreamFiles.sizeOf(artFile(track.urn)) > 0L) restored++
            }
            restored to missing.size
        }
    }
}

object StreamPicker {
    private suspend fun ok(url: String): Boolean = runCatching {
        val (u, h) = Api.authorizedHeaders(url)
        val res = Http.execute(HttpRequest(u, headers = h + ("Range" to "bytes=0-1"), profile = HttpProfile.PROBE, forceNetwork = true))
        res.code in 200..299 && res.header("content-type")?.let { !it.contains("json") && !it.contains("html") } != false
    }.getOrDefault(false)

    fun candidates(track: Track): List<String> = buildList {
        val tail = "/stream/${formEncode(track.urn)}"
        if (Prefs.star && Prefs.hqStreaming) Endpoints.serviceHosts("stream-star").forEach { add("$it$tail?hq=true") }
        if (Prefs.star && track.goPlus) Endpoints.serviceHosts("stream-star").forEach { add("$it$tail") }
        Endpoints.serviceHosts("stream").forEach { add("$it$tail") }
    }.distinct()

    suspend fun pick(track: Track): String? {
        for (c in candidates(track)) if (ok(c)) return c
        return withContext(IoDispatcher) { runCatching { ScAnon.sourceUrl(track.urn)?.takeIf { !HlsSource.isHls(it) } }.getOrNull() }
    }
}
