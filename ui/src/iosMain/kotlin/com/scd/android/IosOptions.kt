package com.scd.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scd.android.res.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.*
import platform.UIKit.*

@Composable
internal fun AccountOptions() {
    var blocked by remember { mutableStateOf(false) }
    Panel {
        IosRow(Res.drawable.ic_user, stringResource(Res.string.blocked_title)) { blocked = true }
        if (Prefs.star) {
            Label(stringResource(Res.string.api_server))
            Choice(stringResource(Res.string.api_server_main), !Prefs.apiStar) { Prefs.changeApiStar(false) }
            Choice(stringResource(Res.string.api_server_star), Prefs.apiStar) { Prefs.changeApiStar(true) }
        }
    }
    if (blocked) BlockedArtistsDialog { blocked = false }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@Composable
internal fun StorageOptions() {
    val scope = rememberCoroutineScope()
    val imageCache = coil3.SingletonImageLoader.get(coil3.compose.LocalPlatformContext.current)
    val tracks = Downloads.downloaded
    var revision by remember { mutableIntStateOf(0) }
    var sizes by remember { mutableStateOf(Triple(0L, 0L, 0L)) }
    var wipe by remember { mutableStateOf<List<String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var restored by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(tracks, revision, Prefs.cacheLimit, Prefs.cacheDays) {
        sizes = withContext(IoDispatcher) {
            StreamFiles.trim()
            val attributes = NSFileManager.defaultManager.attributesOfFileSystemForPath(Dirs.files, null)
            Triple(tracks.sumOf { Downloads.localPath(it)?.let(StreamFiles::sizeOf) ?: 0L },
                StreamFiles.cacheFiles().sumOf(StreamFiles::sizeOf) + (imageCache.diskCache?.size ?: 0L),
                (attributes?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue ?: 0L)
        }
    }
    Panel {
        Hint(stringResource(Res.string.storage_downloaded) + " · " + sizeLabel(sizes.first))
        Hint(stringResource(Res.string.storage_cache) + " · " + sizeLabel(sizes.second))
        Hint(stringResource(Res.string.storage_free) + " · " + sizeLabel(sizes.third))
        Label(stringResource(Res.string.cache_limit))
        OptionMenu(CacheLimits.sizes.associateWith { if (it == 0L) stringResource(Res.string.cache_unlimited) else stringResource(Res.string.cache_gb, (it shr 30).toInt()) }, Prefs.cacheLimit, Prefs::changeCacheLimit)
        Label(stringResource(Res.string.cache_keep))
        OptionMenu(CacheLimits.days.associateWith { if (it == 0) stringResource(Res.string.cache_forever) else stringResource(Res.string.cache_days, it) }, Prefs.cacheDays, Prefs::changeCacheDays)
        Toggle(stringResource(Res.string.auto_cache_likes), "", Prefs.autoCacheLikes) {
            Prefs.changeAutoCacheLikes(it)
            if (it) IosLikesAutoCache.run() else Downloads.cancelList(PlatformContext.INSTANCE, IosLikesAutoCache.KEY)
        }
        Toggle(stringResource(Res.string.prefetch_wifi_only), "", Prefs.prefetchWifiOnly) {
            Prefs.changePrefetchWifiOnly(it)
            if (!it) IosLikesAutoCache.run()
        }
        if (busy) CircularProgressIndicator(Modifier.padding(12.dp)) else {
            IosRow(Res.drawable.ic_clear, stringResource(Res.string.storage_cache_clear)) {
                scope.launch {
                    busy = true
                    try {
                        imageCache.memoryCache?.clear()
                        withContext(IoDispatcher) { imageCache.diskCache?.clear(); StreamFiles.trim(clear = true) }
                    } finally { revision++; busy = false }
                }
            }
            if (tracks.isNotEmpty() && !Prefs.offline && !PlatformHooks.meteredBlocked()) {
                IosRow(Res.drawable.ic_download, stringResource(Res.string.ios_restore_art)) {
                    scope.launch {
                        busy = true
                        try { restored = Downloads.restoreArtwork() } finally { busy = false }
                    }
                }
            }
            restored?.let { Hint(stringResource(Res.string.ios_restore_art) + " · ${it.first}/${it.second}") }
            if (tracks.isNotEmpty()) IosRow(Res.drawable.ic_trash, stringResource(Res.string.clear_downloads)) { wipe = tracks.toList() }
        }
    }
    wipe?.let { selected ->
        AlertDialog(onDismissRequest = { wipe = null }, text = { Text(stringResource(Res.string.clear_downloads_q, selected.size)) },
            confirmButton = { TextButton(onClick = {
                wipe = null
                scope.launch {
                    busy = true
                    try { Downloads.removeAll(selected) } finally { revision++; busy = false }
                }
            }) { Text(stringResource(Res.string.delete)) } },
            dismissButton = { TextButton(onClick = { wipe = null }) { Text(stringResource(Res.string.cancel)) } })
    }
}

private fun sizeLabel(bytes: Long): String = NSByteCountFormatter.stringFromByteCount(bytes, NSByteCountFormatterCountStyleFile)

@Composable
internal fun <T> OptionMenu(values: Map<T, String>, selected: T, change: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(values[selected].orEmpty()) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            values.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; change(value) })
            }
        }
    }
}

@Composable
internal fun AppearanceOptions() {
    val failed = stringResource(Res.string.theme_failed)
    val app = UIApplication.sharedApplication
    var icon by remember { mutableStateOf(app.alternateIconName ?: "AppIcon") }
    val languageNames = mapOf("system" to stringResource(Res.string.theme_system), "en" to "English", "ru" to "Русский",
        "be" to "Беларуская", "de" to "Deutsch", "es" to "Español", "fr" to "Français", "ko" to "한국어", "tr" to "Türkçe", "zh" to "中文")
    Panel {
        Label(stringResource(Res.string.language))
        OptionMenu(languageNames, Prefs.language) { code ->
            val defaults = NSUserDefaults.standardUserDefaults
            if (code == "system") defaults.removeObjectForKey("AppleLanguages")
            else defaults.setObject(listOf(if (code == "zh") "zh-Hans" else code), forKey = "AppleLanguages")
            Prefs.changeLanguage(code)
        }
        if (app.supportsAlternateIcons) {
            Label(stringResource(Res.string.icon_title))
            OptionMenu(mapOf("AppIcon" to stringResource(Res.string.theme_font_default), "Blue" to stringResource(Res.string.icon_blue),
                "Orange" to stringResource(Res.string.icon_orange)), icon) { name ->
                app.setAlternateIconName(name.takeUnless { it == "AppIcon" }) { error ->
                    kotlinx.coroutines.MainScope().launch {
                        if (error == null) icon = name else Toasts.show(error.localizedDescription)
                    }
                }
            }
        }
    }
    Panel {
        ColorField(stringResource(Res.string.accent_color), Prefs.accent, AccentPalette.DEFAULT, Prefs::changeAccent)
        ColorField(stringResource(Res.string.background_color), Prefs.bgColor1) { Prefs.changeBackgroundColors(it, Prefs.bgColor2) }
        ColorField(stringResource(Res.string.text_color), Prefs.textColor, change = Prefs::changeTextColor)
        Label(stringResource(Res.string.header_alpha))
        Slider(Prefs.headerAlpha, Prefs::changeHeaderAlpha, Modifier.padding(horizontal = 16.dp))
        Label(stringResource(Res.string.footer_alpha))
        Slider(Prefs.footerAlpha, Prefs::changeFooterAlpha, Modifier.padding(horizontal = 16.dp))
        IosRow(Res.drawable.ic_music, stringResource(Res.string.theme_font), Prefs.themeFontName) {
            IosFiles.pick { bytes, name -> if (!ThemeFonts.save(bytes, name)) Toasts.show(failed) }
        }
        if (Prefs.themeFontPath != null) Choice(stringResource(Res.string.theme_font_default), false) { ThemeFonts.clear() }
        Label(stringResource(Res.string.theme_share))
        IosRow(Res.drawable.ic_import, stringResource(Res.string.theme_import)) {
            IosFiles.pick { bytes, name -> if (!ThemeFiles.import(bytes, name)) Toasts.show(failed) }
        }
        IosRow(Res.drawable.ic_share, stringResource(Res.string.theme_export)) {
            runCatching { IosFiles.share("SoundCloud-theme.json", ThemeFiles.export()) }.onFailure { Toasts.show(failed) }
        }
    }
}

@Composable
private fun ColorField(title: String, selected: Int, default: Int = 0, change: (Int) -> Unit) {
    var hex by remember(selected) { mutableStateOf(if (selected == 0) "" else (selected and 0xffffff).toString(16).padStart(6, '0').uppercase()) }
    OutlinedTextField(hex, { raw ->
        val value = raw.removePrefix("#").uppercase()
        if (value.length <= 6 && value.all { it in "0123456789ABCDEF" }) {
            hex = value
            if (value.length == 6) change(value.toInt(16) or 0xff000000.toInt())
            if (value.isEmpty()) change(default)
        }
    }, label = { Text(title) }, placeholder = { Text("#RRGGBB") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(12.dp))
}

@Composable
internal fun DiagnosticsOptions() {
    val context = LocalAppContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    val copied = stringResource(Res.string.support_copied)
    val failed = stringResource(Res.string.error_network)
    Panel {
        IosRow(Res.drawable.ic_share, stringResource(Res.string.ios_metrics)) {
            val device = UIDevice.currentDevice
            val report = buildJsonObject {
                put("app", NSBundle.mainBundle.bundleIdentifier.orEmpty())
                put("version", appVersionName(context).orEmpty())
                put("ios", device.systemVersion)
                put("device", device.model)
                put("language", Prefs.language)
                put("user", Prefs.userUrn)
                put("username", Prefs.username)
                put("star", Prefs.star)
                put("offline", Prefs.offline)
                put("api", Api.API_BASE)
                put("stream", Api.STREAM_BASE)
                put("node", Endpoints.activeNode)
                put("downloads", Downloads.downloaded.size)
            }
            copyText(context, report.toString())
            Toasts.show(copied)
        }
        if (checking) CircularProgressIndicator(Modifier.padding(12.dp)) else IosRow(Res.drawable.ic_refresh, stringResource(Res.string.ios_check_update)) {
            scope.launch {
                checking = true
                try { release = Api.latestRelease(ios = true); if (release == null) Toasts.show(failed) }
                finally { checking = false }
            }
        }
        release?.let { info ->
            if (isNewerVersion(info.tag, appVersionName(context).orEmpty())) {
                IosRow(Res.drawable.ic_download, stringResource(Res.string.new_version, info.tag)) {
                    openLink(context, info.assets.firstOrNull()?.url ?: info.url)
                }
            } else Hint(stringResource(Res.string.ios_current_version))
        }
    }
}
