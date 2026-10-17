package com.scd.android

import com.scd.android.core.R
import android.content.Context

fun Prefs.init(context: Context) {
    if (initialized) return
    blurAvailable = { BlurSupport.available }
    onCacheLimitChanged = { MediaCache.applyLimit() }
    init(AndroidKvStore(context, "prefs"))
}

fun Endpoints.init(context: Context) = init(AndroidKvStore(context, "endpoints"))

fun DurationCache.init(context: Context) = init(AndroidKvStore(context, "durations"))

fun Likes.init(context: Context) {
    onLiked = { LikesAutoCache.onLiked(it) }
    init(AndroidKvStore(context, "liked_tracks"))
}

fun Dislikes.init(context: Context) = init(AndroidKvStore(context, "disliked_tracks"))

fun BlockedArtists.init(context: Context) = init(AndroidKvStore(context, "blocked_artists"))

fun Auras.init(context: Context) = init(AndroidKvStore(context, "aura"))

fun LikedArtists.init(context: Context) = init(AndroidKvStore(context, "liked_artists"))


fun DownloadFailures.init(context: Context) = init(AndroidKvStore(context, "download_failures"))

fun ScAnon.init(context: Context) = init(AndroidKvStore(context, "sc_anon"))

fun Dirs.attach(context: Context) {
    if (!ready) files = context.filesDir.path
}

fun FeedCache.init(context: Context) {
    Dirs.attach(context)
    init()
}

fun LocalPlaylists.init(context: Context) {
    Dirs.attach(context)
    init()
}

fun PlaylistSnapshots.init(context: Context) {
    Dirs.attach(context)
    init()
}


fun Playlist.kindLabelRes(): Int = when (kind) {
    "album" -> R.string.kind_album
    "ep" -> R.string.kind_ep
    "single" -> R.string.kind_single
    "compilation" -> R.string.kind_compilation
    else -> R.string.playlist_label
}

fun installAndroidHooks(context: Context) {
    val app = context.applicationContext
    Strings.lookup = androidStrings(app)
    PlatformHooks.wiper = AccountWiper { ctx, player -> AccountData.wipe(ctx, (player as? Media3Queue)?.player) }
    PlatformHooks.forgetResolvedStreams = { ScDataSource.forgetResolved() }
    PlatformHooks.afterLikesSynced = { LikesAutoCache.run(app) }
    PlatformHooks.offlineReady = { Downloads.isDownloaded(it) || MediaCache.holds(it) || ScDataSource.hasSpooled(it) }
    PlatformHooks.meteredBlocked = { Prefs.prefetchWifiOnly && NetMonitor.isMetered(app) }
}
