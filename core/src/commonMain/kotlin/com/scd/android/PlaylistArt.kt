package com.scd.android

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

object PlaylistArt {

    private val resolved = SyncMap<String, List<Any>>()
    private val sizeSuffix = Regex("-(large|original|crop|small|badge|tiny|mini|t\\d+x\\d+)(\\.\\w+)?$")
    private val hashGate = Semaphore(6)
    private val hashCache = SyncMap<String, Long>()

    fun cached(urn: String): List<Any>? = if (LocalPlaylists.owns(urn)) null else resolved[urn]

    fun clear() = resolved.clear()

    fun coverKey(urn: String): String =
        LocalPlaylists.find(urn)?.tracks?.take(12)?.joinToString(",") { it.urn } ?: ""

    suspend fun resolve(playlist: Playlist): List<Any> {
        val own = LocalPlaylists.find(playlist.urn)
        if (own != null && (own.isLocalOrigin || own.tracks.isNotEmpty())) return covers(own.tracks)
        resolved[playlist.urn]?.let { return it }
        val remote = own?.remoteUrn ?: playlist.urn
        val page = runCatching { Api.playlistTracks(remote, 0, limit = 20) }.getOrNull() ?: return emptyList()
        val found = covers(page.collection)
        resolved[playlist.urn] = found
        return found
    }

    private fun artKey(url: String): String =
        url.substringBefore('?').substringAfterLast('/').replace(sizeSuffix, "")

    private suspend fun covers(tracks: List<Track>, need: Int = 4): List<Any> {
        val candidates = tracks.filter { !it.artwork_url.isNullOrBlank() }.distinctBy { artKey(it.artwork_url!!) }.take(12)
        val hashes = coroutineScope { candidates.map { t -> async { imageHash(t) } }.awaitAll() }
        val picked = mutableListOf<Track>()
        val pickedHashes = mutableListOf<Long>()
        candidates.forEachIndexed { i, t ->
            if (picked.size >= need) return@forEachIndexed
            val h = hashes[i]
            if (h != null && pickedHashes.any { (it xor h).countOneBits() <= 6 }) return@forEachIndexed
            picked += t
            if (h != null) pickedHashes += h
        }
        return picked.mapNotNull { it.artModel("t120x120") }
    }

    private suspend fun imageHash(track: Track): Long? {
        val url = track.artwork_url ?: return null
        val key = artKey(url)
        hashCache[key]?.let { return it }
        val hash = hashGate.withPermit { withContext(IoDispatcher) { platformArtHash(track) } } ?: return null
        hashCache[key] = hash
        return hash
    }
}
