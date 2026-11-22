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
        PlatformHooks.wiper = AccountWiper { _, player ->
            Together.resetSession()
            Startup.reset()
            Importer.cancel()
            Importer.clear()
            IosIntegrity.cancel()
            IosLikesAutoCache.cancel()
            Downloads.cancelList(PlatformContext.INSTANCE, IosLikesAutoCache.KEY)
            IosCar.disable()
            player?.pause()
            player?.setTracks(emptyList(), 0, 0)
            SleepTimer.cancel()
            Api.storeSession(null)
            Prefs.saveUsername(null)
            Prefs.saveAvatar(null)
            Prefs.saveUserUrn(null)
            Prefs.saveStar(false)
            Likes.reset()
            Dislikes.reset()
            LikedPlaylists.reset()
            LikedArtists.reset()
            BlockedArtists.reset()
            Auras.reset()
            LocalRadio.reset()
            WaveState.reset()
            Tiles.reset()
            Search.clear()
            Library.reset()
            Recommendations.reset()
            LocalPlaylists.reset()
            Prefs.changeSpotifyToken("", 0L)
            Prefs.changeSpotifyRefresh("")
            Prefs.setSpotifyPkce("", "")
            Prefs.changeYtCookies("")
            Prefs.changeYmToken("")
            listOf(ExternalAccounts.SPOTIFY, ExternalAccounts.YOUTUBE, ExternalAccounts.YANDEX).forEach(ExternalAccounts::forget)
            FeedCache.clear()
            PlaylistArt.clear()
        }
        SessionGuard.init()
        DurationCache.init(IosKvStore("durations"))
        WaveCache.localSamples = ::localWaveform
        WaveCache.init(IosKvStore("waveforms"))
        ServedQuality.init(IosKvStore("served_quality"))
        SpeedPitch.manualPitchSupported = true
        Downloads.init()
        ExternalAccounts.init()
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
        HistoryOutbox.start()
        IosNet.start()
        PlatformHooks.meteredBlocked = { Prefs.prefetchWifiOnly && IosNet.metered }
        PlatformHooks.offlineReady = { Downloads.isDownloaded(it) || StreamFiles.cached(it) != null }
        PlatformHooks.afterLikesSynced = { IosLikesAutoCache.run() }
        Likes.onLiked = IosLikesAutoCache::onLiked
        AppScope.scope.launch { StreamFiles.trim() }
        AppScope.scope.launch {
            delay(10_000)
            Downloads.repairCached()
        }
    }
}
