package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object NowPlaying {
    var urn by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var artistUrn by mutableStateOf<String?>(null)
    var artworkUri by mutableStateOf<String?>(null)
    var waveformUrl by mutableStateOf<String?>(null)
    var genre by mutableStateOf<String?>(null)
    var isPlaying by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var repeatMode by mutableStateOf(REPEAT_OFF)
    var position by mutableStateOf(0L)
    var duration by mutableStateOf(0L)
    var openPlayerRequest by mutableStateOf(false)
    var autoContinue by mutableStateOf(false)
}

fun NowPlaying.sync(q: QueuePlayer) {
    val t = q.currentTrack
    urn = q.currentUrn
    title = t?.title ?: ""
    artist = t?.user?.username ?: ""
    artistUrn = t?.user?.urn
    artworkUri = t?.artModel() as? String
    waveformUrl = t?.waveform_url
    genre = t?.genre
    isPlaying = q.isPlaying
    shuffle = ShuffleQueue.active(q)
    repeatMode = q.repeatMode
    WaveState.remember(urn, q.positionMs)
}
