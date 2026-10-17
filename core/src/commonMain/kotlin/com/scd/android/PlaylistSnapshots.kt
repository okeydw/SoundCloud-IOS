package com.scd.android

import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object PlaylistSnapshots {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var dir: String
    private val lock = Any()
    private val loaded = mutableMapOf<String, List<Track>>()
    private val pending = mutableMapOf<String, Pending>()
    private val stamps = mutableMapOf<String, Long>()
    private var stampsLoaded = false
    private val stampSerializer = MapSerializer(String.serializer(), Long.serializer())

    private class Pending {
        val pages = mutableMapOf<Int, List<Track>>()
        var last = -1
    }

    fun init() {
        dir = Dirs.sub("playlists") + "/snapshots"
        Fs.mkdirs(dir)
    }

    private fun stampsFile() = "$dir/saved_at.json"

    private fun loadStamps() {
        if (stampsLoaded || !::dir.isInitialized) return
        stampsLoaded = true
        runCatching { Fs.readText(stampsFile())?.let { stamps.putAll(json.decodeFromString(stampSerializer, it)) } }
    }

    fun isFresh(urn: String, maxAgeMs: Long): Boolean {
        if (!::dir.isInitialized) return false
        val at = locked(lock) {
            loadStamps()
            stamps[urn]
        } ?: return false
        return nowMillis() - at < maxAgeMs && Fs.exists(fileFor(urn))
    }

    private fun stamp(urn: String) {
        val text = locked(lock) {
            loadStamps()
            stamps[urn] = nowMillis()
            json.encodeToString(stampSerializer, stamps)
        }
        runCatching { Fs.writeText(stampsFile(), text) }
    }

    private fun fileFor(urn: String) = "$dir/" + urn.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json"

    private fun read(urn: String): List<Track> {
        locked(lock) { loaded[urn] }?.let { return it }
        if (!::dir.isInitialized) return emptyList()
        val list = runCatching {
            val bytes = Fs.readAtomic(fileFor(urn))
            if (bytes == null) emptyList()
            else json.decodeFromString(ListSerializer(Track.serializer()), bytes.decodeToString())
        }.getOrDefault(emptyList())
        locked(lock) { loaded[urn] = list }
        return list
    }

    suspend fun get(urn: String): List<Track>? = withContext(IoDispatcher) {
        read(urn).takeIf { it.isNotEmpty() }
    }

    suspend fun record(urn: String, page: Int, tracks: List<Track>, hasMore: Boolean) = withContext(IoDispatcher) {
        if (!::dir.isInitialized) return@withContext
        val result = locked(lock) {
            val p = if (page == 0) Pending().also { pending[urn] = it } else pending[urn]
            if (p == null) {
                null
            } else {
                p.pages[page] = tracks
                if (!hasMore) p.last = page
                val acc = mutableListOf<Track>()
                var i = 0
                while (p.pages[i] != null) {
                    acc += p.pages.getValue(i)
                    i++
                }
                val done = p.last in 0 until i
                if (done) pending.remove(urn)
                acc.distinctBy { it.urn } to done
            }
        } ?: return@withContext
        val (list, complete) = result
        if (list.isEmpty()) return@withContext
        if (!complete && list.size < read(urn).size) return@withContext
        locked(lock) { loaded[urn] = list }
        runCatching {
            Fs.writeAtomic(fileFor(urn), json.encodeToString(ListSerializer(Track.serializer()), list).encodeToByteArray())
            stamp(urn)
        }.onFailure { Logs.add("playlist", "snapshot save failed: ${it::class.simpleName}") }
    }

    fun clear() {
        locked(lock) {
            loaded.clear()
            pending.clear()
            stamps.clear()
            stampsLoaded = true
        }
        if (!::dir.isInitialized) return
        runCatching { Fs.list(dir).forEach { Fs.delete(it) } }
    }
}
