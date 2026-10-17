package com.scd.android

import com.scd.android.res.*
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PlayerGlass(controller: QueuePlayer, onClose: () -> Unit) {
    val p = LocalGlass.current
    val fx = LocalFx.current
    val nav = LocalNav.current
    val context = LocalAppContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val urn = NowPlaying.urn
    val art = NowPlaying.artworkUri
    val artColor = rememberArtColor(art)
    val glow = artColor ?: p.accent
    val immersive = Prefs.immersiveArtwork && art != null
    var showLyrics by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var showOutput by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Track?>(null) }
    val dragY = remember { Animatable(0f) }
    val dragX = remember { Animatable(0f) }
    val heart = remember(urn) { Animatable(0f) }

    PlatformBackHandler(onBack = onClose)

    var fullTrack by remember(urn) { mutableStateOf<Track?>(null) }
    LaunchedEffect(urn) {
        val u = urn ?: return@LaunchedEffect
        if (!Prefs.offline) fullTrack = runCatching { Api.trackByUrn(u) }.getOrNull()
    }
    var offlineQuality by remember(urn) { mutableStateOf(urn?.let { Downloads.cachedQuality(it) }) }
    LaunchedEffect(urn, Downloads.downloaded) {
        val u = urn ?: return@LaunchedEffect
        if (Downloads.isDownloaded(u)) offlineQuality = Downloads.quality(u)
    }

    val apiDuration = maxOf(fullTrack?.displayDuration ?: 0L, urn?.let { DurationCache.get(it) } ?: 0L)
    fun effectiveDuration(): Long {
        val player = NowPlaying.duration
        return when {
            player <= 0L -> apiDuration
            apiDuration > 45_000L && player < apiDuration - 10_000L -> apiDuration
            else -> player
        }
    }
    fun seekFraction(frac: Float) {
        val d = effectiveDuration()
        if (d <= 0L) return
        var target = (frac * d).toLong()
        val real = controller.durationMs
        if (real > 0L && target > real - 500L) target = (real - 500L).coerceAtLeast(0L)
        controller.seekTo(target)
    }
    fun currentTrack(): Track? = fullTrack?.takeIf { it.urn == urn } ?: controller.currentTrack

    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragY.value.roundToInt().coerceAtLeast(0)) }
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
    ) {
        if (immersive) ImmersiveBackdrop(art) else ArtBackdrop(art, artColor)
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(glow.copy(alpha = if (p.dark) 0.32f else 0.18f), Color.Transparent), center = Offset(size.width * 0.5f, size.height * 0.3f), radius = size.width * 0.9f),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.5f, size.height * 0.3f),
                )
            },
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
        val bars = with(density) {
            (WindowInsets.statusBars.getTop(this) + WindowInsets.navigationBars.getBottom(this)).toDp()
        }
        val cap = minOf(maxWidth - 32.dp, 420.dp)
        val artSide = (maxHeight - bars - 470.dp).coerceAtMost(cap).coerceAtLeast(minOf(150.dp, cap))
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .pointerInput(Unit) {
                    val threshold = with(density) { 140.dp.toPx() }
                    var total = Offset.Zero
                    detectDragGestures(
                        onDragStart = { total = Offset.Zero },
                        onDragEnd = {
                            scope.launch {
                                if (dragY.value > threshold) onClose() else dragY.animateTo(0f, tween(320, easing = EaseApple))
                            }
                        },
                        onDragCancel = { scope.launch { dragY.animateTo(0f) } },
                        onDrag = { change, amount ->
                            total += amount
                            if (total.y > 0 && abs(total.y) > abs(total.x)) {
                                change.consume()
                                scope.launch { dragY.snapTo(total.y) }
                            }
                        },
                    )
                }
                .padding(horizontal = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconOrb(Res.drawable.ic_chevron_down, stringResource(Res.string.cd_close), size = 48.dp, onClick = onClose)
                Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Eyebrow(stringResource(Res.string.now_playing), color = p.text3)
                    Spacer(Modifier.height(3.dp))
                    Text(sourceLabel(controller), style = GlassType.small.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp), color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconOrb(Res.drawable.ic_more_vert, stringResource(Res.string.cd_more), size = 48.dp, onClick = { showMenu = true })
            }

            Spacer(Modifier.weight(0.6f))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .then(if (immersive) Modifier.fillMaxWidth().height(artSide) else Modifier.size(artSide))
                        .offset { IntOffset(dragX.value.roundToInt(), 0) }
                        .graphicsLayer {
                            val f = (abs(dragX.value) / (size.width.coerceAtLeast(1f))).coerceIn(0f, 1f)
                            alpha = 1f - 0.35f * f
                            rotationZ = dragX.value / size.width.coerceAtLeast(1f) * 6f
                        }
                        .then(if (immersive) Modifier else Modifier.shadow(40.dp, RoundedCornerShape(30.dp), clip = false, ambientColor = glow, spotColor = glow))
                        .pointerInput(urn) {
                            val threshold = with(density) { 90.dp.toPx() }
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    scope.launch {
                                        when {
                                            total < -threshold -> controller.next()
                                            total > threshold -> controller.previous()
                                        }
                                        dragX.animateTo(0f, tween(380, easing = EaseApple))
                                    }
                                },
                                onDragCancel = { scope.launch { dragX.animateTo(0f) } },
                                onHorizontalDrag = { change, dx ->
                                    change.consume()
                                    total += dx
                                    scope.launch { dragX.snapTo(total * 0.7f) }
                                },
                            )
                        }
                        .pointerInput(urn) {
                            detectTapGestures(onDoubleTap = {
                                val t = currentTrack() ?: return@detectTapGestures
                                scope.launch { if (!Likes.isLiked(t.urn)) Likes.toggle(t) }
                                scope.launch {
                                    heart.snapTo(0f)
                                    heart.animateTo(1f, tween(650, easing = EaseApple))
                                    heart.animateTo(0f, tween(300))
                                }
                            })
                        },
                ) {
                    if (!immersive) Artwork(art, Modifier.matchParentSize(), corner = 30.dp)
                    Icon(
                        painterResource(Res.drawable.ic_heart_filled),
                        null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(96.dp)
                            .graphicsLayer {
                                alpha = heart.value
                                val s = 0.6f + 0.6f * heart.value
                                scaleX = s
                                scaleY = s
                            },
                    )
                }
            }

            Spacer(Modifier.weight(0.5f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        NowPlaying.title,
                        style = GlassType.title,
                        color = p.text,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    Spacer(Modifier.height(4.dp))
                    val artistUrn = NowPlaying.artistUrn
                    Text(
                        NowPlaying.artist,
                        style = GlassType.body.copy(fontSize = 16.sp),
                        color = p.text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (artistUrn != null && !Prefs.offline) {
                            Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                onClose()
                                nav.push(Screen.ArtistS(artistUrn))
                            }
                        } else Modifier,
                    )
                }
                Spacer(Modifier.width(12.dp))
                val liked = urn != null && Likes.isLiked(urn)
                IconOrb(
                    if (liked) Res.drawable.ic_heart_filled else Res.drawable.ic_heart,
                    stringResource(if (liked) Res.string.notif_unlike else Res.string.notif_like),
                    size = 52.dp,
                    iconSize = 22.dp,
                    active = liked,
                ) { currentTrack()?.let { t -> scope.launch { Likes.toggle(t) } } }
            }

            Spacer(Modifier.height(14.dp))
            PlayerChips(urn, fullTrack, offlineQuality, onDownload = { currentTrack()?.let { Downloads.enqueue(context, listOf(it)) } })
            Spacer(Modifier.height(14.dp))

            val samples = rememberWaveSamples(urn, NowPlaying.waveformUrl ?: fullTrack?.waveform_url, 72)
            Waveform(
                samples = samples,
                progress = {
                    val d = effectiveDuration()
                    if (d > 0) NowPlaying.position.toFloat() / d else 0f
                },
                height = 52.dp,
                reflection = fx.reflection,
                onSeek = { f -> seekFraction(f) },
            )
            TimeRow(apiDuration)

            Spacer(Modifier.height(10.dp))
            Transport(controller)
            Spacer(Modifier.weight(0.4f))
            Spacer(Modifier.height(12.dp))

            BottomDock(
                lyrics = !Prefs.offline,
                radioBusy = Playback.radioBusy,
                eqOn = Prefs.eqEnabled,
                disliked = urn != null && Dislikes.isDisliked(urn),
                onLyrics = { showLyrics = true },
                onRadio = {
                    val seed = currentTrack() ?: return@BottomDock
                    showToast(context, text(Res.string.radio_starting))
                    Playback.startTrackRadio(controller, seed) { added ->
                        if (added == 0) showToast(context, text(Res.string.radio_failed))
                    }
                },
                onEq = { showEq = true },
                onDislike = {
                    currentTrack()?.let { t -> scope.launch { if (Dislikes.toggle(t)) controller.next() } }
                },
            )
            Spacer(Modifier.height(10.dp))
        }
        }
    }

    if (showMenu) {
        val t = currentTrack()
        GlassSheet({ showMenu = false }, NowPlaying.title.ifBlank { null }) {
            SheetAction(Res.drawable.ic_music, stringResource(Res.string.queue)) {
                showMenu = false
                showQueue = true
            }
            SheetAction(Res.drawable.ic_speaker, stringResource(Res.string.output_title)) {
                showMenu = false
                showOutput = true
            }
            if (t != null) {
                SheetAction(Res.drawable.ic_plus, stringResource(Res.string.add_to_playlist)) {
                    showMenu = false
                    picking = t
                }
                t.permalink_url?.let { url ->
                    SheetAction(Res.drawable.ic_share, stringResource(Res.string.cd_share)) {
                        showMenu = false
                        shareText(context, url)
                    }
                }
                NowPlaying.artistUrn?.let { a ->
                    if (!Prefs.offline) {
                        SheetAction(Res.drawable.ic_user, stringResource(Res.string.to_artist)) {
                            showMenu = false
                            onClose()
                            nav.push(Screen.ArtistS(a))
                        }
                    }
                }
                if (Downloads.isDownloaded(t.urn)) {
                    SheetAction(Res.drawable.ic_trash, stringResource(Res.string.cd_remove_download)) {
                        showMenu = false
                        scope.launch { Downloads.remove(t.urn) }
                    }
                }
            }
        }
    }
    if (showQueue) QueueSheet(controller, onDismiss = { showQueue = false })
    picking?.let { t -> PlaylistPickerSheet(t, onDismiss = { picking = null }) }
    if (showLyrics && urn != null) {
        GlassLyricsSheet(urn = urn, controller = controller, onClose = { showLyrics = false })
    }
    if (showEq) EqualizerModal(onDismiss = { showEq = false })
    if (showOutput) AudioOutputDialog(onDismiss = { showOutput = false })
}

@Composable
private fun sourceLabel(controller: QueuePlayer): String {
    val urn = NowPlaying.urn
    return when {
        urn != null && WaveFeedback.isOwned(urn) -> stringResource(Res.string.source_river)
        Playback.radioBusy || (NowPlaying.autoContinue && LocalRadio.trackRadioSeed != null) -> stringResource(Res.string.source_radio)
        urn != null && Downloads.isDownloaded(urn) && Prefs.offline -> stringResource(Res.string.downloads)
        else -> {
            val count = controller.itemCount
            if (count > 1) stringResource(Res.string.source_queue, controller.currentIndex + 1, count) else stringResource(Res.string.source_track)
        }
    }
}

@Composable
private fun PlayerChips(urn: String?, track: Track?, offlineQuality: String?, onDownload: () -> Unit) {
    val p = LocalGlass.current
    val genre = (NowPlaying.genre ?: track?.genre)?.takeIf { it.isNotBlank() }
    val saved = urn != null && Downloads.isDownloaded(urn)
    val busy = urn != null && (urn in Downloads.inProgress || urn in Downloads.queued)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (genre != null) GlassChip(genre, dot = genreColor(genre.lowercase()))
        if (Prefs.streamTags || Prefs.streamDebug) GlassChip(qualityText(urn, saved, offlineQuality), mono = true)
        if (track?.goPlus == true) StarBadgeGlass()
        if (YouTubeUrn.matches(urn)) GlassChip(stringResource(Res.string.youtube_badge), dot = YouTubeNeon.core)
        when {
            saved -> GlassChip(stringResource(Res.string.offline_chip), icon = Res.drawable.ic_download)
            busy -> Row(
                Modifier.glass(CircleShape, p, shadow = 0.dp, sheen = false).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(12.dp), color = p.accent, strokeWidth = 1.5.dp)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.downloading), style = GlassType.small, color = p.text)
            }
            urn != null && !Prefs.offline -> GlassChip(stringResource(Res.string.cd_download), icon = Res.drawable.ic_download, onClick = onDownload)
        }
        if (StreamStatus.isProtected(urn)) {
            GlassChip(stringResource(Res.string.stream_protected), icon = Res.drawable.ic_lock, iconTint = p.text2)
        }
    }
}

private fun qualityText(urn: String?, saved: Boolean, offlineQuality: String?): String {
    if (saved && offlineQuality != null) {
        val parts = offlineQuality.split(' ')
        val tier = parts.firstOrNull()?.takeIf { it != "?" } ?: "SQ"
        val kbps = parts.getOrNull(1)?.removeSuffix("k")?.toIntOrNull()
        return if (kbps != null) "$tier · $kbps kbps" else tier
    }
    return if (StreamInfo.wasHq(urn)) "HQ" else "SQ"
}

@Composable
private fun TimeRow(apiDuration: Long) {
    val p = LocalGlass.current
    val player = NowPlaying.duration
    val d = when {
        player <= 0L -> apiDuration
        apiDuration > 45_000L && player < apiDuration - 10_000L -> apiDuration
        else -> player
    }
    val pos = NowPlaying.position.coerceAtMost(if (d > 0) d else Long.MAX_VALUE)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(formatDuration(pos), style = GlassType.mono.copy(fontSize = 12.sp), color = p.text)
        Spacer(Modifier.weight(1f))
        Text(if (d > 0) "-" + formatDuration((d - pos).coerceAtLeast(0L)) else "--:--", style = GlassType.mono.copy(fontSize = 12.sp), color = p.text3)
    }
}

@Composable
private fun Transport(controller: QueuePlayer) {
    val p = LocalGlass.current
    val context = LocalAppContext.current
    val wave = ShuffleQueue.isWave(NowPlaying.urn)
    val shuffle = NowPlaying.shuffle && !wave
    val repeat = NowPlaying.repeatMode
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        PlainButton(
            if (shuffle) Res.drawable.ic_shuffle_on else Res.drawable.ic_shuffle,
            stringResource(if (shuffle) Res.string.notif_shuffle_on else Res.string.notif_shuffle),
            tint = if (shuffle) p.accent else if (wave) p.text3.copy(alpha = 0.4f) else p.text2,
            dot = shuffle,
        ) {
            if (wave) showToast(context, text(Res.string.shuffle_wave_off)) else controller.shuffle = !shuffle
        }
        PlainButton(Res.drawable.ic_prev, stringResource(Res.string.cd_prev), size = 34.dp, tint = p.text) { controller.previous() }
        PlayOrb(NowPlaying.isPlaying, 80.dp) { if (controller.isPlaying) controller.pause() else controller.play() }
        PlainButton(Res.drawable.ic_next, stringResource(Res.string.cd_next), size = 34.dp, tint = p.text) { controller.next() }
        Box(contentAlignment = Alignment.Center) {
            PlainButton(
                Res.drawable.ic_repeat,
                stringResource(Res.string.cd_repeat),
                tint = if (repeat != REPEAT_OFF) p.accent else p.text2,
                dot = repeat != REPEAT_OFF,
            ) {
                controller.repeatMode = when (repeat) {
                    REPEAT_OFF -> REPEAT_ALL
                    REPEAT_ALL -> REPEAT_ONE
                    else -> REPEAT_OFF
                }
            }
            if (repeat == REPEAT_ONE) {
                Text("1", style = GlassType.eyebrow.copy(fontSize = 9.sp), color = p.accent, modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
            }
        }
    }
}

@Composable
private fun PlainButton(icon: DrawableResource, label: String, size: androidx.compose.ui.unit.Dp = 24.dp, tint: Color, dot: Boolean = false, onClick: () -> Unit) {
    val p = LocalGlass.current
    Box(
        Modifier.size(56.dp).clip(CircleShape).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), label, tint = tint, modifier = Modifier.size(size))
        if (dot) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(p.accent),
            )
        }
    }
}

@Composable
private fun BottomDock(
    lyrics: Boolean,
    radioBusy: Boolean,
    eqOn: Boolean,
    disliked: Boolean,
    onLyrics: () -> Unit,
    onRadio: () -> Unit,
    onEq: () -> Unit,
    onDislike: () -> Unit,
) {
    val p = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .dockGlass(RoundedCornerShape(26.dp), null)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockAction(Res.drawable.ic_lyrics, stringResource(Res.string.dock_lyrics), enabled = lyrics, onClick = onLyrics)
        DockAction(Res.drawable.ic_mix, stringResource(Res.string.dock_radio), active = radioBusy, enabled = !radioBusy, onClick = onRadio)
        DockAction(Res.drawable.ic_equalizer, stringResource(Res.string.equalizer), active = eqOn, onClick = onEq)
        DockAction(
            if (disliked) Res.drawable.ic_thumb_down_filled else Res.drawable.ic_thumb_down,
            stringResource(Res.string.not_that),
            tint = if (disliked) Color(0xFFFF6B6B) else null,
            onClick = onDislike,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DockAction(
    icon: DrawableResource,
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val p = LocalGlass.current
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(if (active) Modifier.background(Brush.verticalGradient(listOf(p.glowStrong.copy(alpha = 0.5f), p.glow.copy(alpha = 0.04f)))) else Modifier)
            .pressable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled || active) 1f else 0.4f },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(icon), label, tint = tint ?: if (active) p.accent else p.text, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = GlassType.small.copy(fontSize = 11.sp), color = if (active) p.text else p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QueueSheet(controller: QueuePlayer, onDismiss: () -> Unit) {
    val p = LocalGlass.current
    val count = controller.itemCount
    val current = controller.currentIndex
    val items = remember(count, current, NowPlaying.urn) {
        (0 until count).mapNotNull { i -> controller.trackAt(i)?.let { i to it } }
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))
    GlassSheet(onDismiss, stringResource(Res.string.queue)) {
        if (items.isEmpty()) {
            EmptyState(Res.drawable.ic_music, stringResource(Res.string.queue_empty))
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), state = state) {
                itemsIndexed(items, key = { _, it -> "q:" + it.first + ":" + it.second.urn }) { _, (i, item) ->
                    val isCurrent = i == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .then(if (isCurrent) Modifier.background(p.glow) else Modifier)
                            .clickable {
                                controller.seekTo(i, 0)
                                controller.play()
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp)) {
                            Artwork(item.artModel("t250x250"), Modifier.size(40.dp), corner = 10.dp)
                            if (isCurrent) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                                    EqBars(NowPlaying.isPlaying, Color.White, Modifier.width(16.dp).height(14.dp))
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = GlassType.rowTitle.copy(fontSize = 14.sp), color = if (isCurrent) p.accent else p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.user?.username ?: "", style = GlassType.small, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (!isCurrent) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).clickable { controller.removeAt(i) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(painterResource(Res.drawable.ic_clear), stringResource(Res.string.queue_remove), tint = p.text3, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImmersiveBackdrop(model: Any?) {
    val p = LocalGlass.current
    Box(Modifier.fillMaxSize().background(p.bg)) {
        coil3.compose.AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.08f; scaleY = 1.08f },
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to p.bg.copy(alpha = 0.35f),
                    0.32f to p.bg.copy(alpha = 0.05f),
                    0.55f to p.bg.copy(alpha = 0.45f),
                    1f to p.bg.copy(alpha = 0.94f),
                ),
            ),
        )
    }
}
