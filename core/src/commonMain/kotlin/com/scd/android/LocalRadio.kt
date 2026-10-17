package com.scd.android

import kotlin.concurrent.Volatile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

class RadioEngine(private val tag: String) {
    private val seen = SyncSet<String>()
    private val seeds = mutableListOf<String>()
    private val seedPage = SyncMap<String, Int>()
    private val lock = Any()

    @Volatile
    var seedUrn: String? = null
        private set

    @Volatile
    private var seedHint: Track? = null

    fun reset() {
        seen.clear()
        locked(lock) { seeds.clear() }
        seedPage.clear()
        seedUrn = null
        seedHint = null
    }

    fun startFrom(track: Track, alreadyQueued: Collection<String>) {
        reset()
        seedUrn = track.urn
        seedHint = track
        seen += alreadyQueued
        seen += track.urn
        locked(lock) { seeds += track.urn }
        Logs.add(tag, "radio from ${track.title.take(30)}")
    }

    fun stop() {
        seedUrn = null
    }

    fun addSeed(urn: String) {
        locked(lock) {
            seeds.remove(urn)
            seeds.add(0, urn)
            if (seeds.size > 200) seeds.removeAt(seeds.lastIndex)
        }
    }

    private suspend fun hintTracks(): List<Track> {
        val hint = seedHint ?: return emptyList()
        val queries = listOfNotNull(
            hint.user?.username?.takeIf { it.isNotBlank() },
            hint.genre?.takeIf { it.isNotBlank() },
        )
        val out = mutableListOf<Track>()
        for (q in queries) {
            runCatching { Api.searchTracks(q, 0, 20) }.getOrNull()?.collection
                ?.filter { it.urn != hint.urn }
                ?.let { out += it }
            if (out.size >= 10) break
        }
        return out.distinctBy { it.urn }
    }

    private suspend fun loadPersonalSeeds() {
        val found = LinkedHashSet<String>()
        runCatching { Api.likedTracks(0, 50) }.getOrNull()?.collection?.forEach { found += it.urn }
        runCatching { Api.history(0, 40) }.getOrNull()?.collection?.forEach { found += it.scTrackId }
        found += Likes.urns
        if (found.isEmpty()) {
            val genre = GENRES.shuffled().first()
            runCatching { Api.searchTracks(genre, 0, 20) }.getOrNull()?.collection?.forEach { found += it.urn }
        }
        locked(lock) {
            found.filter { it.startsWith("soundcloud:tracks:") }.forEach { if (it !in seeds) seeds += it }
        }
        Logs.add(tag, "${locked(lock) { seeds.size }} seeds from likes/history")
    }

    private fun usable(t: Track) =
        t.urn !in seen && !t.starLocked && (!t.unavailable || Prefs.playBlocked) && !t.hidden

    suspend fun batch(limit: Int = 20): List<Track> {
        if (locked(lock) { seeds.isEmpty() } && seedHint == null) loadPersonalSeeds()
        repeat(3) { attempt ->
            val picked = locked(lock) {
                val fresh = seeds.take(6)
                (fresh.shuffled().take(2) + seeds.shuffled().take(3)).distinct()
            }
            if (picked.isEmpty()) return emptyList()
            val pool = coroutineScope {
                picked.map { seed ->
                    async {
                        val page = seedPage[seed] ?: 0
                        val id = seed.substringAfterLast(':')
                        val fromApi = withTimeoutOrNull(20_000) {
                            runCatching { Api.relatedTracks(seed, 20, page).collection }
                                .onFailure { Logs.add(tag, "related $id: ${it::class.simpleName} ${it.message?.take(60) ?: ""}") }
                                .getOrNull()
                        }
                        if (fromApi == null) Logs.add(tag, "related $id: no answer in 20s")
                        else if (fromApi.isEmpty()) Logs.add(tag, "related $id: api empty")
                        val list = fromApi?.takeIf { it.isNotEmpty() }
                            ?: kotlinx.coroutines.withContext(IoDispatcher) {
                                ScAnon.related(seed, 20, page * 20)
                            }
                        list.also { if (it.isNotEmpty()) seedPage[seed] = page + 1 }
                    }
                }.awaitAll().flatten()
            }
            val fresh = pool.filter(::usable).distinctBy { it.urn }.shuffled().take(limit)
            if (fresh.isNotEmpty()) {
                fresh.forEach { seen += it.urn }
                locked(lock) {
                    fresh.shuffled().take(3).forEach { t -> if (t.urn !in seeds) seeds += t.urn }
                }
                Logs.add(tag, "+${fresh.size} related from ${picked.size} seeds")
                return fresh
            }
            if (attempt == 0 && seedHint != null) {
                val extra = hintTracks()
                Logs.add(tag, "related empty → ${extra.size} by artist/genre")
                locked(lock) { extra.forEach { if (it.urn !in seeds) seeds += it.urn } }
                val direct = extra.filter(::usable)
                if (direct.isNotEmpty()) {
                    direct.forEach { seen += it.urn }
                    return direct.shuffled().take(limit)
                }
            }
            if (attempt == 1) {
                val genre = seedHint?.genre?.takeIf { it.isNotBlank() } ?: GENRES.shuffled().first()
                runCatching { Api.searchTracks(genre, 0, 20) }.getOrNull()?.collection
                    ?.forEach { locked(lock) { if (it.urn !in seeds) seeds += it.urn } }
            }
        }
        Logs.add(tag, "nothing new")
        return emptyList()
    }
}

object LocalRadio {
    const val CURSOR = LOCAL_RADIO_CURSOR

    private val wave = RadioEngine("wave")
    private val track = RadioEngine("radio")

    fun reset() {
        wave.reset()
        track.reset()
    }

    fun resetWave() = wave.reset()

    fun addSeed(urn: String) = wave.addSeed(urn)

    suspend fun batch(limit: Int = 20): List<Track> = wave.batch(limit)

    val trackRadioSeed: String? get() = track.seedUrn

    fun startFrom(seed: Track, alreadyQueued: Collection<String>) = track.startFrom(seed, alreadyQueued)

    fun stopTrackRadio() = track.stop()

    suspend fun trackBatch(limit: Int = 20): List<Track> = track.batch(limit)
}
