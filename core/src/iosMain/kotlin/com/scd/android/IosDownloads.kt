package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    private var job: Job? = null
    private val pending = ArrayDeque<Pair<Track, String?>>()

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

    actual fun cachedQuality(urn: String): String? = null

    actual suspend fun quality(urn: String): String? = null

    actual fun enqueue(
        context: PlatformContext,
        tracks: List<Track>,
        playlistUrn: String?,
        playlistTitle: String?,
        listKey: String?,
    ) {
        val fresh = tracks.filter { !isDownloaded(it.urn) && it.urn !in queued }
        if (fresh.isEmpty()) return
        locked(pending) { fresh.forEach { pending.addLast(it to listKey) } }
        queued = queued + fresh.map { it.urn }
        listKey?.let { activeLists = activeLists + it }
        if (job?.isActive != true) job = AppScope.scope.launch { drain() }
    }

    private suspend fun drain() = mutex.withLock {
        while (true) {
            val next = locked(pending) { pending.removeFirstOrNull() } ?: break
            val (track, key) = next
            inProgress = inProgress + track.urn
            val ok = runCatching { fetch(track) }.getOrDefault(false)
            inProgress = inProgress - track.urn
            queued = queued - track.urn
            if (ok) {
                locked(lock) { index[track.urn] = track }
                downloaded = downloaded + track.urn
                save()
                Logs.add("download", "${track.title.take(30)} ✓")
            } else {
                Logs.add("download", "${track.title.take(30)} failed")
            }
            if (key != null && locked(pending) { pending.none { it.second == key } }) activeLists = activeLists - key
        }
    }

    private suspend fun fetch(track: Track): Boolean = withContext(IoDispatcher) {
        val body = StreamFiles.cached(track.urn)?.let { Fs.readAtomic(it) } ?: StreamFiles.bytes(track) ?: return@withContext false
        Fs.writeAtomic("$dir/${base(track.urn)}.${StreamFiles.extFor(body)}", body)
        track.artwork_url?.let { art ->
            runCatching {
                val big = Api.artworkUrl(art) ?: return@runCatching
                val (n, v) = Api.imageProxyTarget(big)
                val img = Http.execute(HttpRequest("${Api.IMAGES_BASE}/?t=${formEncode(v)}", headers = listOf(n to v)))
                if (img.isSuccessful && img.body.isNotEmpty()) Fs.writeAtomic(artFile(track.urn), img.body)
            }
        }
        true
    }

    actual fun cancelList(context: PlatformContext, key: String) {
        val dropped = locked(pending) {
            val gone = pending.filter { it.second == key }.map { it.first.urn }
            pending.removeAll { it.second == key }
            gone
        }
        queued = queued - dropped.toSet()
        activeLists = activeLists - key
    }

    actual suspend fun remove(urn: String) = removeAll(listOf(urn))

    actual suspend fun removeAll(urns: Collection<String>) {
        withContext(IoDispatcher) {
            urns.forEach { u ->
                exts.forEach { e -> Fs.delete("$dir/${base(u)}.$e") }
                Fs.delete(artFile(u))
                locked(lock) { index.remove(u) }
            }
            save()
        }
        downloaded = downloaded - urns.toSet()
    }

    actual fun artPath(urn: String): String? = artFile(urn).takeIf { isDownloaded(urn) && Fs.exists(it) }

    actual fun localPath(urn: String): String? = audioPath(urn).takeIf { isDownloaded(urn) }

    actual fun upgradeAll(context: PlatformContext, onResult: (Int) -> Unit) = onResult(0)

    actual suspend fun backfillArt() {}
}

object StreamPicker {
    private suspend fun ok(url: String): Boolean = runCatching {
        val (u, h) = Api.authorizedHeaders(url)
        val res = Http.execute(HttpRequest(u, headers = h + ("Range" to "bytes=0-1"), profile = HttpProfile.PROBE, forceNetwork = true))
        res.code in 200..299 && res.header("content-type")?.let { !it.contains("json") && !it.contains("html") } != false
    }.getOrDefault(false)

    fun candidates(track: Track): List<String> = buildList {
        if (Prefs.star && Prefs.hqStreaming) add(Api.streamUrl(track.urn, hq = true, goPlus = true))
        add(Api.streamUrl(track.urn, goPlus = track.goPlus))
        Endpoints.streamHosts.forEach { add("$it/stream/${formEncode(track.urn)}") }
    }.distinct()

    suspend fun pick(track: Track): String? {
        for (c in candidates(track)) if (ok(c)) return c
        return withContext(IoDispatcher) { runCatching { ScAnon.sourceUrl(track.urn)?.takeIf { !HlsSource.isHls(it) } }.getOrNull() }
    }
}
