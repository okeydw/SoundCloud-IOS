package com.scd.android

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

object IosCore {
    private var started = false

    fun start() {
        if (started) return
        started = true
        val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).firstOrNull() as? String
        Dirs.files = "${support ?: "/tmp"}/SoundCloud".also { Fs.mkdirs(it) }
        Http.engine = DarwinHttpEngine()
        Prefs.init(IosKvStore("prefs"))
        Endpoints.init(IosKvStore("endpoints"))
        Api.attachStore(IosKvStore("scd"))
        Api.loadSession()
        Api.waveBackup = LocalRadioBackup
        SessionGuard.init()
        DurationCache.init(IosKvStore("durations"))
        Downloads.init()
        FeedCache.init()
        LikedArtists.init(IosKvStore("liked_artists"))
        Likes.init(IosKvStore("liked_tracks"))
        Dislikes.init(IosKvStore("disliked_tracks"))
        BlockedArtists.init(IosKvStore("blocked_artists"))
        Auras.init(IosKvStore("aura"))
        LocalPlaylists.init()
        PlaylistSnapshots.init()
        PlaylistCovers.init(IosKvStore("playlist_covers"), Dirs.sub("covers"))
        Recommendations.init()
        DownloadFailures.init(IosKvStore("download_failures"))
        ScAnon.init(IosKvStore("sc_anon"))
        AppScope.scope.launch {
            delay(3_000)
            ScAnon.warmUp()
        }
        AppScope.scope.launch { runCatching { Endpoints.probeAll() } }
    }
}
