package com.scd.android

import com.scd.android.res.*
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource

@Composable
fun iconLabel(res: DrawableResource): String? {
    val label = when (res) {
        Res.drawable.ic_play -> Res.string.cd_play
        Res.drawable.ic_pause -> Res.string.cd_pause
        Res.drawable.ic_next -> Res.string.cd_next
        Res.drawable.ic_prev -> Res.string.cd_prev
        Res.drawable.ic_shuffle, Res.drawable.ic_shuffle_on -> Res.string.notif_shuffle
        Res.drawable.ic_repeat -> Res.string.cd_repeat
        Res.drawable.ic_heart -> Res.string.notif_like
        Res.drawable.ic_heart_filled -> Res.string.notif_unlike
        Res.drawable.ic_thumb_down, Res.drawable.ic_thumb_down_filled -> Res.string.cd_dislike
        Res.drawable.ic_download -> Res.string.cd_download
        Res.drawable.ic_check -> Res.string.cd_remove_download
        Res.drawable.ic_lyrics -> Res.string.cd_lyrics
        Res.drawable.ic_equalizer -> Res.string.equalizer
        Res.drawable.ic_share -> Res.string.cd_share
        Res.drawable.ic_mix -> Res.string.cd_track_radio
        Res.drawable.ic_plus -> Res.string.add_to_playlist
        Res.drawable.ic_chevron_down -> Res.string.cd_close
        Res.drawable.ic_arrow_back -> Res.string.cd_back
        Res.drawable.ic_more_vert -> Res.string.cd_more
        Res.drawable.ic_settings -> Res.string.settings
        Res.drawable.ic_trash -> Res.string.delete
        Res.drawable.ic_search -> Res.string.tab_search
        Res.drawable.ic_clear -> Res.string.cd_clear
        Res.drawable.ic_refresh -> Res.string.wave_refresh
        Res.drawable.ic_edit -> Res.string.rename
        else -> return null
    }
    return stringResource(label)
}
