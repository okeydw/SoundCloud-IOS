package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

object AccountData {

    fun wipe(context: android.content.Context, controller: androidx.media3.common.Player?) {
        runCatching {
            controller?.stop()
            controller?.clearMediaItems()
        }
        Api.storeSession(context, null)
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
        Support.reset()
        DiscordInfo.reset()
        LocalRadio.reset()
        WaveState.reset()
        Tiles.reset()
        Search.clear()
        Library.reset()
        Recommendations.reset()
        LocalPlaylists.reset()
        Startup.reset()
        FeedCache.clear()
        PlaylistArt.clear()
        WaveCache.clear()
        ScDataSource.forgetResolved()
        val app = context.applicationContext
        AppScope.scope.launch { runCatching { MediaCache.clear(app) } }
    }
}
