package com.scd.android

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object IosLikesAutoCache {
    const val KEY = "likes-autocache"
    private var job: Job? = null

    fun onLiked(track: Track) {
        if (!allowed() || Downloads.isDownloaded(track.urn)) return
        if (DownloadFailures.backingOff(track.urn)) return
        Downloads.enqueue(PlatformContext.INSTANCE, listOf(track), null, null, KEY)
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    private fun allowed() = Prefs.autoCacheLikes && !Prefs.offline && !PlatformHooks.meteredBlocked() && Api.sessionId != null

    fun run() {
        if (!allowed()) return
        if (job?.isActive == true) return
        job = AppScope.scope.launch {
            val session = Api.sessionId
            val pending = mutableListOf<Track>()
            val known = Library.recentLikes()
            if (known != null) pending += known.take(2000).filter { !Downloads.isDownloaded(it.urn) }
            var page = 0
            while (known == null && page < 40 && allowed() && Api.sessionId == session) {
                val res = runCatching { Api.likedTracks(page, 50) }.getOrNull() ?: break
                pending += res.collection.filter { !Downloads.isDownloaded(it.urn) }
                if (!res.has_more) break
                page++
            }
            val candidates = pending.distinctBy { it.urn }
            val todo = candidates.filter { !DownloadFailures.backingOff(it.urn) }
            val waiting = candidates.size - todo.size
            Logs.add(
                "download",
                "likes autocache: ${todo.size} to download" + if (waiting > 0) ", $waiting postponed after failures" else "",
            )
            if (todo.isNotEmpty() && allowed() && Api.sessionId == session) {
                val label = Strings.get("liked")
                Downloads.enqueue(PlatformContext.INSTANCE, todo, null, label, listKey = KEY)
            }
        }
    }
}
