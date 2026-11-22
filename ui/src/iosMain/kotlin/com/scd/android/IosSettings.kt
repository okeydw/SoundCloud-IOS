package com.scd.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scd.android.res.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private enum class IosSec { Root, Account, Look, Equalizer, Playback, Storage, Import, Spotify, YouTube, Yandex, Auto, Network, About, Logs }

@Composable
actual fun SettingsScreen(
    username: String?,
    avatar: String?,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    visualExtras: @Composable () -> Unit,
    accountExtras: @Composable () -> Unit,
) {
    val p = LocalGlass.current
    var sec by remember { mutableStateOf(IosSec.Root) }
    var showQr by remember { mutableStateOf(false) }
    if (showQr) LinkQrDialog(push = true, onDismiss = { showQr = false })
    val back = { if (sec == IosSec.Root) onBack() else sec = when (sec) {
        IosSec.Spotify, IosSec.YouTube, IosSec.Yandex -> IosSec.Import
        else -> IosSec.Root
    } }
    val title = when (sec) {
        IosSec.Root -> stringResource(Res.string.settings)
        IosSec.Account -> stringResource(Res.string.tab_account)
        IosSec.Look -> stringResource(Res.string.tab_visual)
        IosSec.Equalizer -> stringResource(Res.string.equalizer)
        IosSec.Playback -> stringResource(Res.string.tab_advanced)
        IosSec.Import -> stringResource(Res.string.import_title)
        IosSec.Spotify -> stringResource(Res.string.import_sp_title)
        IosSec.YouTube -> stringResource(Res.string.import_yt_section)
        IosSec.Yandex -> stringResource(Res.string.import_src_yandex)
        IosSec.Auto -> stringResource(Res.string.car_section)
        IosSec.Storage -> stringResource(Res.string.tab_storage)
        IosSec.Network -> stringResource(Res.string.relay_section)
        IosSec.About -> stringResource(Res.string.tab_about)
        IosSec.Logs -> stringResource(Res.string.tab_logs)
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        ScreenTop(title, onBack = back)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (sec) {
                IosSec.Root -> {
                    Panel {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Artwork(avatar, Modifier.size(52.dp).clip(CircleShape), corner = 26.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(username ?: "", style = GlassType.rowTitle, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Panel {
                        IosRow(Res.drawable.ic_user, stringResource(Res.string.tab_account)) { sec = IosSec.Account }
                        IosRow(Res.drawable.ic_music, stringResource(Res.string.tab_visual)) { sec = IosSec.Look }
                        IosRow(Res.drawable.ic_equalizer, stringResource(Res.string.equalizer)) { sec = IosSec.Equalizer }
                        IosRow(Res.drawable.ic_settings, stringResource(Res.string.tab_advanced)) { sec = IosSec.Playback }
                        IosRow(Res.drawable.ic_download, stringResource(Res.string.tab_storage)) { sec = IosSec.Storage }
                        IosRow(Res.drawable.ic_import, stringResource(Res.string.import_title)) { sec = IosSec.Import }
                        IosRow(Res.drawable.ic_play, stringResource(Res.string.car_section)) { sec = IosSec.Auto }
                        IosRow(Res.drawable.ic_globe, stringResource(Res.string.relay_section)) { sec = IosSec.Network }
                        IosRow(Res.drawable.ic_history, stringResource(Res.string.tab_logs)) { sec = IosSec.Logs }
                        IosRow(Res.drawable.ic_comment, stringResource(Res.string.tab_about)) { sec = IosSec.About }
                    }
                }
                IosSec.Account -> {
                    Panel {
                        IosRow(Res.drawable.ic_share, stringResource(Res.string.link_share_title)) { showQr = true }
                        IosRow(Res.drawable.ic_power, stringResource(Res.string.logout)) { onLogout() }
                    }
                    AccountOptions()
                    accountExtras()
                }
                IosSec.Look -> {
                    Panel {
                        Label(stringResource(Res.string.theme))
                        Choice(stringResource(Res.string.theme_system), Prefs.theme == "system") { Prefs.setThemeMode("system") }
                        Choice(stringResource(Res.string.theme_dark), Prefs.theme == "dark") { Prefs.setThemeMode("dark") }
                        Choice(stringResource(Res.string.theme_light), Prefs.theme == "light") { Prefs.setThemeMode("light") }
                    }
                    Panel {
                        Label(stringResource(Res.string.accent_color))
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AccentPalette.colors.forEach { c ->
                                val selected = Prefs.accent == c
                                Box(
                                    Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(c))
                                        .border(if (selected) 3.dp else 0.dp, p.text, CircleShape)
                                        .clickable { Prefs.changeAccent(c) },
                                )
                            }
                        }
                    }
                    Panel {
                        Label(stringResource(Res.string.background_color))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val options = listOf(0 to 0) + BackgroundPalette.solids.map { it to 0 }
                            options.forEach { (a, b) ->
                                val selected = Prefs.bgColor1 == a && Prefs.bgColor2 == b
                                val fill = when {
                                    a == 0 -> Brush.linearGradient(listOf(p.bg, p.bg))
                                    b == 0 -> Brush.linearGradient(listOf(Color(a), Color(a)))
                                    else -> Brush.linearGradient(listOf(Color(a), Color(b)))
                                }
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(fill)
                                        .border(if (selected) 3.dp else 1.dp, if (selected) p.accent else p.border, CircleShape)
                                        .clickable { Prefs.changeBackgroundColors(a, b) },
                                )
                            }
                        }
                    }
                    Panel {
                        Toggle(stringResource(Res.string.immersive_artwork), stringResource(Res.string.immersive_artwork_hint), Prefs.immersiveArtwork) { Prefs.changeImmersiveArtwork(it) }
                        Toggle(stringResource(Res.string.hide_title_artists), stringResource(Res.string.hide_title_artists_hint), Prefs.hideTitleArtists) { Prefs.changeHideTitleArtists(it) }
                    }
                    AppearanceOptions()
                    BackgroundPanel()
                    visualExtras()
                }
                IosSec.Equalizer -> {
                    Panel {
                        Column(Modifier.padding(14.dp)) { GlassEqualizer(onClose = null) }
                    }
                    Panel {
                        SpeedPitchPanel(Modifier.padding(14.dp))
                    }
                }
                IosSec.Playback -> {
                    QualitySettingsPanel()
                    Panel {
                        Toggle(stringResource(Res.string.crossfade), "", Prefs.crossfade, Prefs::changeCrossfade)
                        if (Prefs.crossfade) {
                            Label(stringResource(Res.string.crossfade_seconds, Prefs.crossfadeSeconds))
                            androidx.compose.material3.Slider(Prefs.crossfadeSeconds.toFloat(), { Prefs.changeCrossfadeSeconds(it.toInt()) },
                                Modifier.padding(horizontal = 16.dp), valueRange = 1f..30f, steps = 28)
                        }
                        Toggle(stringResource(Res.string.stream_tags), "", Prefs.streamTags, Prefs::changeStreamTags)
                    }
                    Panel {
                        Toggle(stringResource(Res.string.haptics), stringResource(Res.string.haptics_hint), Prefs.haptics) { Prefs.changeHaptics(it) }
                        Toggle(stringResource(Res.string.anon_fallback), stringResource(Res.string.anon_fallback_hint), Prefs.anonFallback) { Prefs.changeAnonFallback(it) }
                        Toggle(stringResource(Res.string.offline_mode), "", Prefs.offline) { Prefs.setOfflineMode(it) }
                        Toggle(stringResource(Res.string.offline_auto), stringResource(Res.string.offline_auto_hint), Prefs.autoOffline) { Prefs.changeAutoOffline(it) }
                    }
                }
                IosSec.Import -> IosImportScreen({ sec = IosSec.Spotify }, { sec = IosSec.YouTube }, { sec = IosSec.Yandex })
                IosSec.Spotify -> IosSpotifyAccount()
                IosSec.YouTube -> IosYouTubeAccount { sec = IosSec.Import }
                IosSec.Yandex -> IosYandexAccount()
                IosSec.Auto -> CarOptions()
                IosSec.Storage -> {
                    StorageOptions()
                    StoragePanel()
                }
                IosSec.Network -> NetworkPanel()
                IosSec.About -> AboutPanel()
                IosSec.Logs -> LogsPanel()
            }
            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
internal fun Panel(content: @Composable () -> Unit) {
    val p = LocalGlass.current
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), p, shadow = 0.dp).padding(vertical = 4.dp)) { content() }
}

@Composable
internal fun Label(text: String) {
    val p = LocalGlass.current
    Text(text.uppercase(), style = GlassType.eyebrow, color = p.text3, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
internal fun IosRow(icon: DrawableResource, title: String, subtitle: String? = null, onClick: () -> Unit) {
    val p = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).glass(RoundedCornerShape(12.dp), p, shadow = 0.dp, tint = p.glow), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = p.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = GlassType.rowTitle, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) Text(subtitle, style = GlassType.small, color = p.text2, maxLines = 2)
        }
        Icon(painterResource(Res.drawable.ic_chevron_right), null, tint = p.text3, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun Choice(title: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalGlass.current
    Row(Modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = GlassType.rowTitle, color = p.text, modifier = Modifier.weight(1f))
        if (selected) Icon(painterResource(Res.drawable.ic_check), null, tint = p.accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun Toggle(title: String, hint: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalGlass.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = GlassType.rowTitle, color = p.text)
            if (hint.isNotEmpty()) Text(hint, style = GlassType.small, color = p.text2)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = value,
            onCheckedChange = {
                Haptics.tap()
                onChange(it)
            },
            colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.onAccent),
        )
    }
}

@Composable
private fun NetworkPanel() {
    val p = LocalGlass.current
    val scope = rememberCoroutineScope()
    Panel {
        Toggle(stringResource(Res.string.storage_first), stringResource(Res.string.storage_first_hint), Prefs.storageFirst) { Prefs.changeStorageFirst(it) }
    }
    Panel {
        Text(stringResource(Res.string.relay_hint), style = GlassType.small, color = p.text2, modifier = Modifier.padding(16.dp))
        Choice(stringResource(Res.string.relay_auto), Endpoints.relayMode == Endpoints.AUTO) { scope.launch { Endpoints.choose(Endpoints.AUTO) } }
        Endpoints.nodes.forEach { node ->
            val st = Endpoints.statuses.firstOrNull { it.node == node }
            val label = (if (node == Endpoints.DIRECT) stringResource(Res.string.relay_direct) else node) +
                (st?.ms?.let { "  ·  ${it} ms" } ?: "")
            Choice(label, Endpoints.relayMode == node) { scope.launch { Endpoints.choose(node) } }
        }
        IosRow(Res.drawable.ic_refresh, stringResource(Res.string.relay_measure)) { scope.launch { Endpoints.measure() } }
    }
}

@Composable
private fun AboutPanel() {
    DiagnosticsOptions()
    val p = LocalGlass.current
    val context = LocalAppContext.current
    val scope = rememberCoroutineScope()
    Panel {
        Text(
            stringResource(Res.string.version, appVersionName(context) ?: "?"),
            style = GlassType.rowTitle,
            color = p.text,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        IosRow(Res.drawable.ic_comment, stringResource(Res.string.support_app_ios), stringResource(Res.string.support_app_sub)) {
            scope.launch { openLink(context, SupportLinks.APP_ISSUES) }
        }
        IosRow(Res.drawable.ic_globe, stringResource(Res.string.support_discord)) { openLink(context, SupportLinks.APP_DISCORD) }
    }
}

@Composable
private fun LogsPanel() {
    val p = LocalGlass.current
    val context = LocalAppContext.current
    Panel {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            IosRow(Res.drawable.ic_share, stringResource(Res.string.logs_copy)) { copyText(context, Logs.dump()) }
        }
        val lines = Logs.lines.take(200)
        if (lines.isEmpty()) {
            Text(stringResource(Res.string.logs_empty), style = GlassType.small, color = p.text2, modifier = Modifier.padding(16.dp))
        } else {
            lines.forEach { Text(it, style = GlassType.mono, color = p.text2, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
        }
    }
}

@Composable
actual fun SettingsLink(icon: DrawableResource, title: String, subtitle: String, onClick: () -> Unit) = IosRow(icon, title, subtitle, onClick)

@Composable
internal fun Hint(text: String) {
    Text(text, style = GlassType.small, color = LocalGlass.current.text2, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun BackgroundPanel() {
    val p = LocalGlass.current
    val failed = stringResource(Res.string.background_failed)
    Panel {
        Label(stringResource(Res.string.background_image))
        Hint(stringResource(Res.string.background_image_hint))
        Prefs.backgroundImage?.let { path ->
            Artwork("file://$path", Modifier.fillMaxWidth().height(150.dp).padding(12.dp), corner = 14.dp)
        }
        if (IosImages.busy) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp), color = p.accent)
        } else {
            IosRow(Res.drawable.ic_music, stringResource(if (Prefs.backgroundImage == null) Res.string.background_choose else Res.string.background_replace)) {
                IosImages.pick(IosBackground::set) { Toasts.show(failed) }
            }
            if (Prefs.backgroundImage != null) {
                IosRow(Res.drawable.ic_trash, stringResource(Res.string.background_remove)) { IosBackground.clear() }
            }
        }
        if (Prefs.backgroundImage != null) {
            Toggle(stringResource(Res.string.background_blur), stringResource(Res.string.background_blur_hint), Prefs.backgroundBlur) { Prefs.changeBackgroundBlur(it) }
            if (Prefs.backgroundBlur && BlurSupport.available) {
                Label(stringResource(Res.string.background_blur_amount))
                androidx.compose.material3.Slider(Prefs.backgroundBlurRadius, { Prefs.changeBackgroundBlurRadius(it) }, Modifier.padding(horizontal = 16.dp), valueRange = 0f..60f)
            }
            Label(stringResource(Res.string.background_dim))
            androidx.compose.material3.Slider(Prefs.backgroundDim, { Prefs.changeBackgroundDim(it) }, Modifier.padding(horizontal = 16.dp), valueRange = 0f..0.95f)
        }
    }
}

@Composable
private fun StoragePanel() {
    val p = LocalGlass.current
    Panel {
        Label(stringResource(Res.string.integrity_check))
        Hint(stringResource(Res.string.integrity_hint))
        if (IosIntegrity.running) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), color = p.accent)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(Res.string.integrity_progress, IosIntegrity.done, IosIntegrity.total), color = p.text, style = GlassType.small)
            }
            IosRow(Res.drawable.ic_clear, stringResource(Res.string.cancel)) { IosIntegrity.cancel() }
        } else {
            IosRow(Res.drawable.ic_refresh, stringResource(Res.string.integrity_check)) { IosIntegrity.start() }
        }
        if (IosIntegrity.failed) Hint(stringResource(Res.string.integrity_failed))
        IosIntegrity.report?.let { report ->
            Hint(stringResource(Res.string.integrity_result, report.checked, report.repaired, report.remaining))
        }
    }
}
