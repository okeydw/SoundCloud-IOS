package com.scd.android

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object IosLikesAutoCache {
    private var job: Job? = null

    fun onLiked(track: Track) {
        if (!Prefs.autoCacheLikes || Prefs.offline || Downloads.isDownloaded(track.urn)) return
        if (DownloadFailures.backingOff(track.urn)) return
        Downloads.enqueue(PlatformContext.INSTANCE, listOf(track), null, null, null)
    }

    fun run() {
        if (!Prefs.autoCacheLikes || Prefs.offline) return
        if (job?.isActive == true) return
        job = AppScope.scope.launch {
            val pending = mutableListOf<Track>()
            var page = 0
            while (page < 40) {
                val res = runCatching { Api.likedTracks(page, 50) }.getOrNull() ?: break
                pending += res.collection.filter { !Downloads.isDownloaded(it.urn) && !it.starLocked }
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
            if (todo.isNotEmpty()) {
                val label = Strings.get("liked")
                Downloads.enqueue(PlatformContext.INSTANCE, todo, null, label, listKey = label)
            }
        }
    }
}
