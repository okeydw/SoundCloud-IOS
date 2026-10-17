package com.scd.android

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController

class Media3Queue(val player: Player) : QueuePlayer {
    override val itemCount: Int get() = player.mediaItemCount
    override val currentIndex: Int get() = player.currentMediaItemIndex
    override val nextIndex: Int get() = player.nextMediaItemIndex.let { if (it == C.INDEX_UNSET) -1 else it }
    override val previousIndex: Int get() = player.previousMediaItemIndex.let { if (it == C.INDEX_UNSET) -1 else it }
    override val currentUrn: String? get() = player.currentMediaItem?.mediaId
    override fun urnAt(index: Int): String? = runCatching { player.getMediaItemAt(index).mediaId }.getOrNull()
    override fun trackAt(index: Int): Track? = runCatching { player.getMediaItemAt(index).toTrackOrNull() }.getOrNull()
    override val isPlaying: Boolean get() = player.isPlaying
    override val playWhenReady: Boolean get() = player.playWhenReady
    override val positionMs: Long get() = player.currentPosition.coerceAtLeast(0L)
    override val durationMs: Long get() = player.duration.let { if (it == C.TIME_UNSET) 0L else it.coerceAtLeast(0L) }
    override val failed: Boolean get() = player.playerError != null
    override val idle: Boolean get() = player.playbackState == Player.STATE_IDLE
    override var shuffle: Boolean
        get() = ShuffleQueue.on
        set(value) { ShuffleQueue.set(this, value) }
    override var repeatMode: Int
        get() = player.repeatMode
        set(value) { player.repeatMode = value }

    override fun setTracks(tracks: List<Track>, index: Int, positionMs: Long) =
        player.setMediaItems(tracks.map { it.toMediaItem() }, index, positionMs)

    override fun prepare() = player.prepare()
    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    override fun seekTo(index: Int, positionMs: Long) = player.seekTo(index, positionMs)
    override fun next() = player.seekToNextMediaItem()
    override fun previous() = player.seekToPrevious()
    override fun addTracks(tracks: List<Track>) = player.addMediaItems(tracks.map { it.toMediaItem() })
    override fun insert(index: Int, track: Track) = player.addMediaItem(index, track.toMediaItem())
    override fun insertTracks(index: Int, tracks: List<Track>) = player.addMediaItems(index, tracks.map { it.toMediaItem() })
    override fun removeAt(index: Int) = player.removeMediaItem(index)
    override fun removeRange(from: Int, to: Int) = player.removeMediaItems(from, to)
    override fun move(from: Int, to: Int) = player.moveMediaItem(from, to)

    private val listeners = HashMap<QueueListener, Player.Listener>()

    override fun addListener(listener: QueueListener) {
        val l = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = listener.onChanged()
        }
        listeners[listener] = l
        player.addListener(l)
    }

    override fun removeListener(listener: QueueListener) {
        listeners.remove(listener)?.let { player.removeListener(it) }
    }

    override fun equals(other: Any?): Boolean = other is Media3Queue && other.player === player
    override fun hashCode(): Int = System.identityHashCode(player)
}

fun Player.queue(): QueuePlayer = Media3Queue(this)

fun NowPlaying.sync(c: MediaController) {
    val md = c.currentMediaItem?.mediaMetadata
    urn = c.currentMediaItem?.mediaId
    title = md?.title?.toString() ?: ""
    artist = md?.artist?.toString() ?: ""
    artistUrn = md?.extras?.getString("artist_urn")
    artworkUri = md?.artworkUri?.toString()
    waveformUrl = md?.extras?.getString("waveform_url")
    genre = md?.extras?.getString("genre")
    isPlaying = c.isPlaying
    shuffle = ShuffleQueue.on && !ShuffleQueue.isWave(urn)
    repeatMode = c.repeatMode
    WaveState.remember(urn, c.currentPosition)
}

fun Recommendations.init(context: Context) {
    Dirs.attach(context)
    init()
}
