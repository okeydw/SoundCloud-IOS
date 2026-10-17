package com.scd.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object FeedCache {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var dir: String

    fun init() {
        dir = Dirs.sub("feed")
    }

    private fun fileFor(key: String) = "$dir/$key.json"

    fun load(key: String): List<Track> {
        if (!::dir.isInitialized) return emptyList()
        return runCatching {
            val text = Fs.readText(fileFor(key)) ?: return emptyList()
            json.decodeFromString(ListSerializer(Track.serializer()), text)
        }.getOrDefault(emptyList())
    }

    fun clear() {
        if (!::dir.isInitialized) return
        runCatching { Fs.list(dir).forEach { Fs.delete(it) } }
    }

    suspend fun save(key: String, tracks: List<Track>) = withContext(IoDispatcher) {
        if (!::dir.isInitialized || tracks.isEmpty()) return@withContext
        runCatching {
            Fs.writeText(
                fileFor(key),
                json.encodeToString(ListSerializer(Track.serializer()), tracks.take(60)),
            )
        }
    }
}
