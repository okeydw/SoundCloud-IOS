package com.scd.android

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import platform.AVFAudio.*
import platform.Foundation.*

object IosCar {
    private val scope = MainScope()
    private var observing = false
    private var pending: Job? = null
    private var routeId: String? = null
    var device by mutableStateOf<Pair<String, String>?>(null)
        private set

    private fun output(): Pair<String, String>? = AVAudioSession.sharedInstance().currentRoute.outputs
        .filterIsInstance<AVAudioSessionPortDescription>()
        .firstOrNull { it.portType in setOf(AVAudioSessionPortBluetoothA2DP, AVAudioSessionPortBluetoothHFP, AVAudioSessionPortBluetoothLE, AVAudioSessionPortCarAudio) }
        ?.let { it.UID to it.portName }

    fun start(player: QueuePlayer) {
        if (observing) return
        observing = true
        EqPresets.restoreCar()
        fun checkRoute() {
            val current = output()
            device = current
            if (current?.first == routeId) return
            routeId = current?.first
            pending?.cancel()
            EqPresets.restoreCar()
            if (current != null && Prefs.carAutoplay) connect(current.first, player)
        }
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(AVAudioSessionRouteChangeNotification, null, NSOperationQueue.mainQueue) { checkRoute() }
        center.addObserverForName(platform.UIKit.UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { checkRoute() }
        checkRoute()
    }

    fun disable() {
        pending?.cancel()
        EqPresets.restoreCar()
    }

    private fun connect(id: String, player: QueuePlayer) {
        if (Api.sessionId == null) return
        val scenario = CarScenarios.forDevice(id) ?: return
        EqPresets.applyCar(scenario.eq)
        if (player.playWhenReady) return
        if (player.itemCount > 0) {
            if (scenario.autostart) player.play()
            return
        }
        pending = scope.launch {
            val session = Api.sessionId
            val offline = Prefs.offline || !hasNetwork(PlatformContext.INSTANCE)
            val tracks = try {
                when (scenario.source) {
                    "liked" -> if (offline) Downloads.tracks().filter { Likes.isLiked(it.urn) } else Api.likedTracks(0, 100).collection
                    "wave" -> if (offline) WaveState.tracks else Api.waveTracks(null).first
                    "downloads" -> Downloads.tracks()
                    else -> Library.playlistPage(scenario.source.removePrefix("playlist:"), 0, fresh = !offline, offline = offline).first
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { emptyList() }
            val available = tracks.filter { Playback.playable(it) && !it.hidden && (!offline || Playback.offlineReady(it)) }
                .ifEmpty { Downloads.tracks().filter { !it.hidden && Playback.playable(it) } }
            if (available.isEmpty() || session != Api.sessionId || !Prefs.carAutoplay || routeId != id || player.itemCount > 0 || player.playWhenReady) return@launch
            NowPlaying.autoContinue = scenario.source == "wave"
            player.setTracks(if (scenario.shuffle) available.shuffled() else available, 0, 0)
            player.prepare()
            if (scenario.autostart) player.play()
        }
    }
}
