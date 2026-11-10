package com.scd.android

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

class TrackSwipeFade(private val scope: CoroutineScope) {
    var window by mutableStateOf<QueuePreview?>(null)
        private set
    var distance by mutableFloatStateOf(0f)
        private set
    private var threshold = 1f
    private var travel = 1f
    private var distanceLimit = 1f
    private var animation: Job? = null
    private var settling = false

    val fraction: Float get() = (abs(distance) / travel).coerceIn(0f, 1f)
    val target: Track? get() = if (distance < 0f) window?.next else window?.previous

    fun begin(preview: QueuePreview, thresholdPx: Float, travelPx: Float = thresholdPx, limitPx: Float = Float.MAX_VALUE) {
        reset()
        window = preview
        threshold = thresholdPx.coerceAtLeast(1f)
        travel = travelPx.coerceAtLeast(threshold)
        distanceLimit = limitPx.coerceAtLeast(travel)
    }

    fun drag(dx: Float) {
        if (settling) return
        distance = (distance + dx).coerceIn(if (window?.next != null) -distanceLimit else 0f,
            if (window?.previous != null) distanceLimit else 0f)
    }

    fun cancel() {
        animation?.cancel()
        settling = true
        animation = scope.launch {
            animate(distance, 0f, animationSpec = tween(380, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) { value, _ -> distance = value }
            reset()
        }
    }

    fun finish(controller: QueuePlayer?, slide: Boolean = false) {
        if (settling) return
        val snapshot = window ?: return
        val next = distance < 0f
        val targetIndex = if (next) snapshot.nextIndex else snapshot.previousIndex
        val targetId = if (next) snapshot.nextId else snapshot.previousId
        fun stillValid(): Boolean {
            val player = controller ?: return false
            val actualIndex = if (next) player.nextIndex else player.previousIndex
            return player.currentUrn == snapshot.currentId && player.currentIndex == snapshot.currentIndex &&
                actualIndex == targetIndex && targetIndex in 0 until player.itemCount &&
                player.urnAt(targetIndex) == targetId
        }
        if (abs(distance) < threshold || controller == null || target == null || !stillValid()) {
            cancel()
            return
        }
        settling = true
        animation?.cancel()
        animation = scope.launch {
            if (slide) {
                animate(distance, sign(distance) * travel, animationSpec = tween(240, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) { value, _ -> distance = value }
            }
            if (!stillValid()) {
                reset()
                return@launch
            }
            controller.seekTo(targetIndex, 0L)
            delay(600)
            reset()
        }
    }

    fun reset() {
        animation?.cancel()
        animation = null
        settling = false
        distance = 0f
        window = null
    }
}

@Composable
fun SlidingTrack(
    state: TrackSwipeFade,
    current: Track?,
    modifier: Modifier = Modifier,
    tilt: Boolean = false,
    content: @Composable (Track) -> Unit,
) {
    val from = currentTrackMetadata(state, current)
    val to = state.target
    val progress = if (to == null) 0f else state.fraction
    val direction = sign(state.distance)
    Box(modifier.clipToBounds()) {
        Box(Modifier.fillMaxWidth().graphicsLayer {
            translationX = direction * progress * size.width
            if (tilt) rotationZ = direction * progress * 6f
        }) { content(from) }
        if (to != null && progress > 0f) {
            Box(Modifier.matchParentSize().graphicsLayer {
                translationX = direction * (progress - 1f) * size.width
                if (tilt) rotationZ = direction * (progress - 1f) * 6f
            }) { content(to) }
        }
    }
}

@Composable
fun rememberTrackSwipeFade(preview: QueuePreview): TrackSwipeFade {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { TrackSwipeFade(scope) }
    LaunchedEffect(preview.currentId, preview.currentIndex) {
        state.window?.let {
            if (it.currentId != preview.currentId || it.currentIndex != preview.currentIndex) state.reset()
        }
    }
    return state
}

@Composable
fun FadingTrack(
    state: TrackSwipeFade,
    current: Track?,
    modifier: Modifier = Modifier,
    content: @Composable (Track) -> Unit,
) {
    val from = currentTrackMetadata(state, current)
    val to = state.target
    val fraction = if (to == null) 0f else state.fraction
    Box(modifier) {
        Box(Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - fraction }) { content(from) }
        if (to != null && fraction > 0f) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = fraction }) { content(to) }
        }
    }
}

private fun currentTrackMetadata(state: TrackSwipeFade, current: Track?): Track =
    state.window?.current ?: current ?: Track(
        urn = NowPlaying.urn.orEmpty(), title = NowPlaying.title,
        user = TrackUser(username = NowPlaying.artist), artwork_url = NowPlaying.artworkUri,
    )
