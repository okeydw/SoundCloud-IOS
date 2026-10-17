package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

const val REPEAT_OFF = 0
const val REPEAT_ONE = 1
const val REPEAT_ALL = 2

interface QueueListener {
    fun onChanged()
}

interface QueuePlayer {
    val itemCount: Int
    val currentIndex: Int
    val nextIndex: Int
    val previousIndex: Int
    val currentUrn: String?
    fun urnAt(index: Int): String?
    fun trackAt(index: Int): Track?
    val isPlaying: Boolean
    val playWhenReady: Boolean
    val positionMs: Long
    val durationMs: Long
    val failed: Boolean
    val idle: Boolean
    var shuffle: Boolean
    var repeatMode: Int
    fun setTracks(tracks: List<Track>, index: Int, positionMs: Long = 0L)
    fun prepare()
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun seekTo(index: Int, positionMs: Long)
    fun next()
    fun previous()
    fun addTracks(tracks: List<Track>)
    fun insert(index: Int, track: Track)
    fun insertTracks(index: Int, tracks: List<Track>)
    fun removeAt(index: Int)
    fun removeRange(from: Int, to: Int)
    fun move(from: Int, to: Int)
    fun addListener(listener: QueueListener)
    fun removeListener(listener: QueueListener)
}

val QueuePlayer.currentTrack: Track? get() = currentIndex.takeIf { it in 0 until itemCount }?.let { trackAt(it) }

object PlayerHub {
    var player by mutableStateOf<QueuePlayer?>(null)
}
