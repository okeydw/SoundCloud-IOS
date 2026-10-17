package com.scd.android

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFAudio.*
import platform.AVFoundation.*
import platform.CoreMedia.*
import platform.MediaPlayer.*
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIImage

@OptIn(ExperimentalForeignApi::class)
class IosPlayer : QueuePlayer {
    private enum class Reason { AUTO, SEEK, REPEAT, LIST, ERROR }

    private val av = AVQueuePlayer()
    private val scope = MainScope()
    private val tracks = ArrayList<Track>()
    private var preItem: AVPlayerItem? = null
    private var preUrn: String? = null
    private var preIndex = -1
    private var preFile: String? = null
    private var idleTicks = 0
    private var cur = 0
    private var pendingPos = 0L
    private var wantPlay = false
    private var loading = false
    private var hasFailed = false
    private var filePath: String? = null
    private var retriedUrn: String? = null
    private var prefetchUrn: String? = null
    private var loadJob: Job? = null
    private var fillJob: Job? = null
    private var resumeRetries = 0
    private var skipped = 0
    private var recordedDuration: String? = null
    private val listeners = ArrayList<QueueListener>()

    private var evUrn: String? = null
    private var evPos = 0L
    private var evDur = 0L
    private var lastReportedUrn: String? = null

    private var artUrn: String? = null
    private var artwork: MPMediaItemArtwork? = null
    private var lastSnapshot = ""
    private var ticks = 0
    private var interruptedWhilePlaying = false

    init {
        runCatching { AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null) }
        av.automaticallyWaitsToMinimizeStalling = true
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, null, NSOperationQueue.mainQueue) { n: NSNotification? ->
            val item = n?.`object` as? AVPlayerItem
            if (item != null && item != preItem) onEnded()
        }
        center.addObserverForName(AVPlayerItemFailedToPlayToEndTimeNotification, null, NSOperationQueue.mainQueue) { n: NSNotification? ->
            val item = n?.`object` as? AVPlayerItem
            if (item != null && item != preItem) onItemError("stalled")
        }
        center.addObserverForName(AVAudioSessionInterruptionNotification, null, NSOperationQueue.mainQueue) { n: NSNotification? ->
            val type = (n?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
            if (type == 1uL) {
                interruptedWhilePlaying = isPlaying
                sessionActive = false
            } else if (interruptedWhilePlaying) {
                interruptedWhilePlaying = false
                runCatching { AVAudioSession.sharedInstance().setActive(true, error = null) }
                play()
            }
        }
        remoteCommands()
        scope.launch { tick() }
    }

    override val itemCount: Int get() = tracks.size
    override val currentIndex: Int get() = if (tracks.isEmpty()) 0 else cur
    override val nextIndex: Int get() = step(1)
    override val previousIndex: Int get() = step(-1)
    override val currentUrn: String? get() = tracks.getOrNull(cur)?.urn
    override fun urnAt(index: Int): String? = tracks.getOrNull(index)?.urn
    override fun trackAt(index: Int): Track? = tracks.getOrNull(index)
    override val isPlaying: Boolean get() = av.timeControlStatus == AVPlayerTimeControlStatusPlaying
    override val playWhenReady: Boolean get() = wantPlay
    override val failed: Boolean get() = hasFailed
    override val idle: Boolean get() = tracks.isEmpty() || (av.currentItem == null && !loading)

    override val positionMs: Long
        get() {
            if (av.currentItem == null || loading) return pendingPos
            val s = CMTimeGetSeconds(av.currentTime())
            return if (s.isNaN() || s < 0) 0L else (s * 1000).toLong()
        }

    override val durationMs: Long
        get() {
            val item = av.currentItem
            val s = item?.let { CMTimeGetSeconds(it.duration) } ?: Double.NaN
            if (!s.isNaN() && !s.isInfinite() && s > 0) return (s * 1000).toLong()
            val t = tracks.getOrNull(cur) ?: return 0L
            return maxOf(DurationCache.get(t.urn), t.full_duration ?: t.duration).coerceAtLeast(0L)
        }

    override var shuffle: Boolean
        get() = ShuffleQueue.on
        set(value) {
            ShuffleQueue.set(this, value)
            notifyChanged()
        }

    override var repeatMode: Int = Prefs.repeatMode.coerceIn(0, 2)
        set(value) {
            field = value
            Prefs.changeRepeatMode(value)
            if (value == REPEAT_ONE) dropPreload()
            notifyChanged()
        }

    private fun step(dir: Int): Int {
        if (tracks.isEmpty()) return -1
        val q = cur + dir
        return when {
            q in tracks.indices -> q
            repeatMode != REPEAT_OFF -> if (dir > 0) 0 else tracks.lastIndex
            else -> -1
        }
    }

    private fun rebuildOrder() {
        dropPreload()
    }

    override fun setTracks(tracks: List<Track>, index: Int, positionMs: Long) {
        noteTransition(Reason.LIST)
        this.tracks.clear()
        this.tracks.addAll(tracks)
        cur = index.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        pendingPos = positionMs
        rebuildOrder()
        stopItem()
        hasFailed = false
        evUrn = currentUrn
        notifyChanged()
        scheduleFill()
    }

    override fun prepare() {
        if (tracks.isEmpty()) return
        if (av.currentItem == null && !loading || hasFailed) load()
    }

    private var sessionActive = false

    private fun activateSession() {
        if (sessionActive) return
        sessionActive = runCatching { AVAudioSession.sharedInstance().setActive(true, error = null) }.getOrDefault(false)
    }

    override fun play() {
        if (tracks.isEmpty()) return
        wantPlay = true
        activateSession()
        if (av.currentItem == null && !loading || hasFailed) load() else av.play()
        notifyChanged()
    }

    override fun pause() {
        wantPlay = false
        av.pause()
        notifyChanged()
    }

    override fun seekTo(positionMs: Long) {
        if (av.currentItem == null || loading) {
            pendingPos = positionMs
        } else {
            av.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 1000))
        }
        notifyChanged()
    }

    override fun seekTo(index: Int, positionMs: Long) {
        if (index !in tracks.indices) return
        if (index == cur && av.currentItem != null && !hasFailed) {
            seekTo(positionMs)
            return
        }
        transition(index, Reason.SEEK, positionMs)
    }

    override fun next() {
        val n = nextIndex
        if (n >= 0) transition(n, Reason.SEEK, 0L)
    }

    override fun previous() {
        if (positionMs > 3_000L || previousIndex < 0) {
            seekTo(0L)
            return
        }
        transition(previousIndex, Reason.SEEK, 0L)
    }

    override fun addTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val start = this.tracks.size
        this.tracks.addAll(tracks)
        if (start == cur + 1) dropPreload()
        notifyChanged()
    }

    override fun insert(index: Int, track: Track) = insertTracks(index, listOf(track))

    override fun insertTracks(index: Int, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val at = index.coerceIn(0, this.tracks.size)
        val wasEmpty = this.tracks.isEmpty()
        this.tracks.addAll(at, tracks)
        if (!wasEmpty && at <= cur) cur += tracks.size
        rebuildOrder()
        notifyChanged()
    }

    override fun removeAt(index: Int) {
        if (index !in tracks.indices) return
        tracks.removeAt(index)
        when {
            tracks.isEmpty() -> {
                cur = 0
                stopItem()
                wantPlay = false
            }
            index < cur -> cur--
            index == cur -> {
                cur = cur.coerceAtMost(tracks.size - 1)
                pendingPos = 0L
                load()
            }
        }
        rebuildOrder()
        notifyChanged()
    }

    override fun removeRange(from: Int, to: Int) {
        val a = from.coerceIn(0, tracks.size)
        val b = to.coerceIn(a, tracks.size)
        if (a == b) return
        val removingCurrent = cur in a until b
        repeat(b - a) { tracks.removeAt(a) }
        when {
            tracks.isEmpty() -> {
                cur = 0
                stopItem()
                wantPlay = false
            }
            cur >= b -> cur -= b - a
            removingCurrent -> {
                cur = a.coerceAtMost(tracks.size - 1)
                pendingPos = 0L
                load()
            }
        }
        rebuildOrder()
        notifyChanged()
    }

    override fun move(from: Int, to: Int) {
        if (from !in tracks.indices || to !in tracks.indices || from == to) return
        val t = tracks.removeAt(from)
        tracks.add(to, t)
        cur = when {
            from == cur -> to
            from < cur && to >= cur -> cur - 1
            from > cur && to <= cur -> cur + 1
            else -> cur
        }
        rebuildOrder()
        notifyChanged()
    }

    override fun addListener(listener: QueueListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: QueueListener) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        listeners.toList().forEach { runCatching { it.onChanged() } }
        updateNowPlaying()
    }

    private fun stopItem() {
        loadJob?.cancel()
        loading = false
        av.pause()
        av.removeAllItems()
        preItem = null
        preUrn = null
        preIndex = -1
        preFile = null
    }

    private fun dropPreload() {
        val item = preItem ?: return
        runCatching { av.removeItem(item) }
        preItem = null
        preUrn = null
        preIndex = -1
        preFile = null
    }

    private fun preloadNext() {
        if (preItem != null || loading || av.currentItem == null || repeatMode == REPEAT_ONE) return
        val n = nextIndex
        val t = tracks.getOrNull(n) ?: return
        if (n == cur) return
        val path = Downloads.localPath(t.urn)?.takeIf { Fs.exists(it) } ?: StreamFiles.cached(t.urn) ?: return
        val item = AVPlayerItem.playerItemWithURL(NSURL.fileURLWithPath(path))
        if (!av.canInsertItem(item, afterItem = av.currentItem)) return
        av.insertItem(item, afterItem = av.currentItem)
        preItem = item
        preUrn = t.urn
        preIndex = n
        preFile = path
    }

    private fun advanceToPreloaded(): Boolean {
        val item = preItem ?: return false
        val n = preIndex
        if (preUrn == null || urnAt(n) != preUrn) {
            dropPreload()
            return false
        }
        val prevUrn = currentUrn
        noteTransition(Reason.AUTO)
        cur = n
        filePath = preFile
        pendingPos = 0L
        recordedDuration = null
        retriedUrn = null
        preItem = null
        preUrn = null
        preIndex = -1
        preFile = null
        evUrn = currentUrn
        if (av.currentItem != item) {
            av.removeAllItems()
            av.insertItem(item, afterItem = null)
        }
        if (wantPlay) av.play()
        if (prevUrn != null && prevUrn != currentUrn) WaveFeedback.positive(prevUrn)
        val urn = currentUrn
        if (urn != null && Dislikes.isDisliked(urn) && nextIndex >= 0) {
            Logs.add("player", "disliked → skip")
            transition(nextIndex, Reason.SEEK, 0L)
            return true
        }
        notifyChanged()
        scheduleFill()
        return true
    }

    private fun transition(index: Int, reason: Reason, positionMs: Long) {
        val prevUrn = currentUrn
        val prevPos = evPos
        noteTransition(reason)
        cur = index
        pendingPos = positionMs
        evUrn = currentUrn
        load()
        if (reason != Reason.LIST && prevUrn != null && prevUrn != currentUrn) {
            if (reason == Reason.SEEK && prevPos < 30_000L) WaveFeedback.negative(prevUrn)
            if (reason == Reason.AUTO) WaveFeedback.positive(prevUrn)
        }
        val urn = currentUrn
        if (reason == Reason.AUTO && urn != null && Dislikes.isDisliked(urn) && nextIndex >= 0) {
            Logs.add("player", "disliked → skip")
            transition(nextIndex, Reason.SEEK, 0L)
            return
        }
        notifyChanged()
        scheduleFill()
    }

    private fun noteTransition(reason: Reason) {
        val prev = evUrn
        val pos = evPos
        val dur = evDur
        evPos = 0L
        evDur = 0L
        if (prev == null || !isScUrn(prev)) return
        when (reason) {
            Reason.AUTO, Reason.REPEAT -> Events.fullPlay(prev)
            Reason.SEEK -> if (dur > 0L) {
                val pct = pos.toDouble() / dur
                if (pct >= 0.9) Events.fullPlay(prev) else Events.skip(prev, pct)
            }
            else -> {}
        }
    }

    private fun load() {
        val t = tracks.getOrNull(cur) ?: return
        stopItem()
        hasFailed = false
        loading = true
        recordedDuration = null
        filePath = null
        val local = Downloads.localPath(t.urn)?.takeIf { Fs.exists(it) } ?: StreamFiles.cached(t.urn)
        if (local != null) {
            startFile(local)
            return
        }
        val urn = t.urn
        notifyChanged()
        loadJob = scope.launch {
            val path = StreamFiles.get(t)
            if (currentUrn != urn) return@launch
            if (path == null) trackFailed("no source") else startFile(path)
        }
    }

    private fun startFile(path: String) {
        filePath = path
        av.removeAllItems()
        av.insertItem(AVPlayerItem.playerItemWithURL(NSURL.fileURLWithPath(path)), afterItem = null)
        if (pendingPos > 0L) av.seekToTime(CMTimeMakeWithSeconds(pendingPos / 1000.0, 1000))
        loading = false
        if (wantPlay) av.play()
        notifyChanged()
    }

    private fun onItemError(detail: String) {
        val urn = currentUrn
        val pos = positionMs
        Logs.add("player", detail)
        stopItem()
        if (urn != null && filePath != null && !Downloads.isDownloaded(urn) && retriedUrn != urn) {
            retriedUrn = urn
            StreamFiles.drop(urn)
            pendingPos = pos
            load()
            return
        }
        trackFailed(detail)
    }

    private fun prefetchNext() {
        if (Prefs.offline || positionMs < 5_000L) return
        val next = tracks.getOrNull(nextIndex) ?: return
        if (next.urn == prefetchUrn || next.urn == currentUrn || Downloads.isDownloaded(next.urn)) return
        prefetchUrn = next.urn
        scope.launch { runCatching { StreamFiles.get(next) } }
    }

    private fun trackFailed(detail: String) {
        loading = false
        val n = nextIndex
        if (n >= 0 && n != cur && skipped < 3) {
            skipped++
            Logs.add("player", "track won't play → next")
            transition(n, Reason.ERROR, 0L)
            return
        }
        skipped = 0
        hasFailed = true
        wantPlay = false
        av.pause()
        Logs.add("player", detail)
        notifyChanged()
    }

    private fun onEnded() {
        idleTicks = 0
        if (repeatMode == REPEAT_ONE) {
            noteTransition(Reason.REPEAT)
            evUrn = currentUrn
            pendingPos = 0L
            load()
            return
        }
        if (advanceToPreloaded()) return
        val n = nextIndex
        if (n >= 0) {
            transition(n, Reason.AUTO, 0L)
        } else {
            wantPlay = false
            pendingPos = 0L
            av.pause()
            av.removeAllItems()
            notifyChanged()
        }
    }

    private suspend fun tick() {
        while (true) {
            delay(250)
            val item = av.currentItem
            if (item != null && !loading) {
                if (item.status == AVPlayerItemStatusFailed) {
                    onItemError(item.error?.localizedDescription ?: "failed")
                } else if (item.status == AVPlayerItemStatusReadyToPlay && recordedDuration != currentUrn) {
                    recordedDuration = currentUrn
                    DurationCache.record(currentUrn, durationMs)
                }
            }
            val playing = isPlaying
            if (wantPlay && !loading && !hasFailed && tracks.isNotEmpty() && av.currentItem == null) {
                if (++idleTicks >= 3) onEnded()
            } else {
                idleTicks = 0
            }
            if (preItem != null && (preUrn != urnAt(nextIndex) || repeatMode == REPEAT_ONE)) dropPreload()
            if (playing) preloadNext()
            if (playing) {
                resumeRetries = 0
                skipped = 0
                if (currentUrn == evUrn) {
                    evPos = positionMs
                    evDur = durationMs
                }
                watchHistory()
                prefetchNext()
            }
            val snap = "$playing|$cur|${tracks.size}|$hasFailed|$loading|${durationMs > 0}"
            ticks++
            if (snap != lastSnapshot) {
                lastSnapshot = snap
                notifyChanged()
            } else if (ticks % 4 == 0) {
                updateNowPlaying()
            }
        }
    }

    private fun watchHistory() {
        val t = tracks.getOrNull(cur) ?: return
        if (t.urn == lastReportedUrn || !isScUrn(t.urn)) return
        val pos = positionMs
        val dur = durationMs
        if (pos >= 30_000L || (dur > 0L && pos >= dur / 2)) {
            lastReportedUrn = t.urn
            scope.launch {
                runCatching {
                    Api.postHistory(t.urn, t.title, t.user?.username ?: "", t.user?.urn, t.artwork_url, t.duration)
                }
            }
        }
    }

    private fun scheduleFill() {
        fillJob?.cancel()
        fillJob = scope.launch { runCatching { fillQueue() } }
    }

    private suspend fun fillQueue() {
        if (Prefs.offline || repeatMode == REPEAT_ONE) return
        val urn = currentUrn ?: return
        val wave = WaveFeedback.isOwned(urn)
        if (repeatMode == REPEAT_ALL && !wave) return
        val tail = if (wave) 5 else 2
        if (tracks.isEmpty() || cur < tracks.size - tail) return
        val existing = tracks.map { it.urn }.toSet()
        fun usable(list: List<Track>) = list.filter {
            it.urn !in existing && !it.starLocked && (!it.unavailable || Prefs.playBlocked) && !it.hidden
        }
        var add = emptyList<Track>()
        var source = ""
        if (LocalRadio.trackRadioSeed != null) {
            add = usable(LocalRadio.trackBatch(20))
            source = "track radio"
        }
        if (add.isEmpty() && wave && !WaveState.loading) {
            WaveState.loading = true
            add = try {
                usable(runCatching { WaveState.more() }.getOrDefault(emptyList()))
            } finally {
                WaveState.loading = false
            }
            source = "wave cursor"
        }
        if (add.isEmpty()) {
            add = usable(Api.waveFromTrack(urn))
            source = "wave from track"
        }
        if (add.isEmpty()) {
            add = usable(runCatching { Api.relatedTracks(urn, 20).collection }.getOrDefault(emptyList()))
            source = "related"
        }
        if (add.isEmpty()) {
            add = usable(withContext(IoDispatcher) { ScAnon.related(urn, 20) })
            source = "soundcloud related"
        }
        if (add.isEmpty() || currentUrn != urn) return
        Logs.add("queue", "autopilot +${add.size} from $source")
        withContext(Dispatchers.Main) { addTracks(add) }
    }

    private fun remoteCommands() {
        val c = MPRemoteCommandCenter.sharedCommandCenter()
        fun ok(block: () -> Unit): (MPRemoteCommandEvent?) -> Long = {
            block()
            MPRemoteCommandHandlerStatusSuccess
        }
        c.playCommand.addTargetWithHandler(ok { play() })
        c.pauseCommand.addTargetWithHandler(ok { pause() })
        c.togglePlayPauseCommand.addTargetWithHandler(ok { if (wantPlay) pause() else play() })
        c.nextTrackCommand.addTargetWithHandler(ok { next() })
        c.previousTrackCommand.addTargetWithHandler(ok { previous() })
        c.changePlaybackPositionCommand.addTargetWithHandler { e: MPRemoteCommandEvent? ->
            val ev = e as? MPChangePlaybackPositionCommandEvent
            if (ev == null) {
                MPRemoteCommandHandlerStatusCommandFailed
            } else {
                seekTo((ev.positionTime * 1000).toLong())
                MPRemoteCommandHandlerStatusSuccess
            }
        }
    }

    private fun updateNowPlaying() {
        val t = tracks.getOrNull(cur)
        val center = MPNowPlayingInfoCenter.defaultCenter()
        if (t == null) {
            center.nowPlayingInfo = null
            return
        }
        if (artUrn != t.urn) {
            artUrn = t.urn
            artwork = null
            loadArtwork(t)
        }
        val info = HashMap<Any?, Any?>()
        info[MPMediaItemPropertyTitle] = t.title
        info[MPMediaItemPropertyArtist] = t.user?.username ?: ""
        info[MPMediaItemPropertyPlaybackDuration] = durationMs / 1000.0
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = positionMs / 1000.0
        info[MPNowPlayingInfoPropertyPlaybackRate] = if (isPlaying) 1.0 else 0.0
        artwork?.let { info[MPMediaItemPropertyArtwork] = it }
        center.nowPlayingInfo = info
    }

    private fun loadArtwork(t: Track) {
        val urn = t.urn
        val model = t.artModel() as? String ?: return
        scope.launch {
            val bytes = withContext(IoDispatcher) {
                runCatching {
                    if (model.startsWith("file://")) {
                        Fs.readAtomic(model.removePrefix("file://"))
                    } else {
                        val (n, v) = Api.imageProxyTarget(model)
                        val res = Http.execute(HttpRequest("${Api.IMAGES_BASE}/?t=${formEncode(v)}", headers = listOf(n to v)))
                        res.body.takeIf { res.isSuccessful && it.isNotEmpty() }
                    }
                }.getOrNull()
            } ?: return@launch
            if (artUrn != urn) return@launch
            val image = UIImage.imageWithData(bytes.toNSData()) ?: return@launch
            artwork = MPMediaItemArtwork(boundsSize = image.size, requestHandler = { _ -> image })
            updateNowPlaying()
        }
    }
}
