package com.scd.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scd.android.res.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages
import platform.UIKit.UIPasteboard


private val ErrorRed = Color(0xFFFF5A5F)

@Composable
private fun ErrorText(text: String) {
    Text(text, style = GlassType.small, color = ErrorRed, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
private fun Title(text: String) {
    val p = LocalGlass.current
    Text(text, style = GlassType.rowTitle.copy(fontWeight = FontWeight.Bold), color = p.text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
}

@Composable
private fun WideButton(text: String, enabled: Boolean = true, primary: Boolean = true, onClick: () -> Unit) {
    val p = LocalGlass.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .then(
                if (primary) Modifier.clip(shape).background(p.accentFill())
                else Modifier.glass(shape, p, shadow = 0.dp, sheen = false),
            )
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = GlassType.rowTitle.copy(fontWeight = FontWeight.Bold),
            color = if (primary) p.onAccent else p.text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    val p = LocalGlass.current
    Text(
        text,
        style = GlassType.small.copy(fontWeight = FontWeight.Bold),
        color = p.accent,
        modifier = Modifier.padding(horizontal = 8.dp).pressable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

@Composable
private fun ImportField(value: String, onChange: (String) -> Unit, placeholder: String, secret: Boolean = false, error: Boolean = false) {
    val p = LocalGlass.current
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .glass(RoundedCornerShape(16.dp), p, shadow = 0.dp, sheen = false, borderColor = if (error) ErrorRed else Color.Unspecified)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = GlassType.body, color = p.text3, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = GlassType.body.copy(color = p.text),
            cursorBrush = SolidColor(p.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun importError(code: String?): String = when (code) {
    null -> ""
    "cancelled" -> stringResource(Res.string.import_cancelled)
    "token" -> stringResource(Res.string.import_err_token)
    "sp_token" -> stringResource(Res.string.import_err_sp_token)
    "sp_not_owned" -> stringResource(Res.string.import_err_sp_not_owned)
    "sp_forbidden" -> stringResource(Res.string.import_err_sp_forbidden)
    "nothing" -> stringResource(Res.string.import_err_nothing)
    "search" -> stringResource(Res.string.import_err_search)
    "unavailable", "parse", "empty" -> stringResource(Res.string.import_err_source)
    else -> code
}

@Composable
private fun ImportProgress() {
    val st = Importer.state ?: return
    val p = LocalGlass.current
    Panel {
        Title(st.title.ifBlank { st.source })
        val line = when (st.stage) {
            ImportStage.Reading -> stringResource(Res.string.import_reading, st.done)
            ImportStage.Matching -> stringResource(Res.string.import_matching, st.done, st.total, st.found)
            ImportStage.Saving -> stringResource(Res.string.import_saving)
            ImportStage.Done -> stringResource(Res.string.import_done, st.found, st.total)
            ImportStage.Failed -> importError(st.error)
        }
        if (st.stage == ImportStage.Failed) ErrorText(line) else Hint(line)
        if (Importer.busy) {
            val bar = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(CircleShape)
            if (st.total > 0 && st.stage == ImportStage.Matching) {
                LinearProgressIndicator(
                    progress = { (st.done.toFloat() / st.total).coerceIn(0f, 1f) },
                    modifier = bar,
                    color = p.accent,
                    trackColor = p.text.copy(alpha = 0.12f),
                )
            } else {
                LinearProgressIndicator(modifier = bar, color = p.accent, trackColor = p.text.copy(alpha = 0.12f))
            }
            WideButton(stringResource(Res.string.cancel), primary = false) { Importer.cancel() }
        } else {
            if (st.stage == ImportStage.Done && st.partial) Hint(stringResource(Res.string.import_sp_partial))
            if (st.stage == ImportStage.Done && st.missed.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                LinkText(stringResource(Res.string.import_missed, st.missed.size)) { open = !open }
                if (open) st.missed.take(60).forEach { Hint(it) }
            }
            WideButton(stringResource(Res.string.cd_close), primary = false) { Importer.clear() }
        }
        Spacer(Modifier.padding(bottom = 6.dp))
    }
}

private const val GUIDE_BASE = "https://github.com/okeydw/SoundCloud-Android/blob/main/docs/import-lang/"
private val guideLangs = setOf("en", "be", "de", "es", "fr", "ko", "tr", "zh")

private fun guideUrl(): String {
    val lang = (NSLocale.preferredLanguages.firstOrNull() as? String)?.substringBefore('-')?.lowercase().orEmpty()
    val file = when {
        lang == "ru" -> "import.md"
        lang in guideLangs -> "import.$lang.md"
        else -> "import.en.md"
    }
    return GUIDE_BASE + file
}

@Composable
private fun GuideLink(anchor: String? = null) {
    val context = LocalAppContext.current
    LinkText(stringResource(Res.string.import_guide)) {
        openInBrowser(context, guideUrl() + (anchor?.let { "#$it" } ?: ""))
    }
}

@Composable
private fun AccountCard(account: ExtAccount?) {
    val p = LocalGlass.current
    val name = account?.name ?: stringResource(Res.string.import_acc_connected)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(p.text.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), style = GlassType.rowTitle.copy(fontWeight = FontWeight.Bold), color = p.text2)
            account?.avatar?.let { Artwork(it, Modifier.size(44.dp).clip(CircleShape), corner = 22.dp) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = GlassType.rowTitle.copy(fontWeight = FontWeight.Bold), color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (account != null) Text(stringResource(Res.string.import_acc_connected), style = GlassType.small, color = p.text2)
        }
    }
}

@Composable
internal fun IosImportScreen(onSpotify: () -> Unit, onYouTube: () -> Unit, onYandex: () -> Unit) {
    val context = LocalAppContext.current
    var link by remember { mutableStateOf("") }
    var asPlaylist by remember { mutableStateOf(true) }
    var ytSimilar by remember { mutableStateOf(false) }
    var download by remember { mutableStateOf(true) }
    val kind = Importer.detect(link)
    val busy = Importer.busy
    var spotifyLogin by remember { mutableStateOf(false) }
    var pendingSpotify by remember { mutableStateOf<String?>(null) }
    if (spotifyLogin) {
        SpotifyLoginDialog(
            refresh = true,
            onDismiss = {
                spotifyLogin = false
                pendingSpotify?.let { Importer.spotify(context, it, asPlaylist, download) }
                pendingSpotify = null
            },
        ) { t, until ->
            Prefs.changeSpotifyToken(t, until)
            spotifyLogin = false
            pendingSpotify?.let { Importer.spotify(context, it, asPlaylist, download) }
            pendingSpotify = null
        }
    }

    Panel {
        Title(stringResource(Res.string.import_link_title))
        Hint(stringResource(Res.string.import_link_hint))
        GuideLink()
        ImportField(link, { link = it.trim() }, "youtube.com/… · open.spotify.com/… · music.yandex.ru/…")
        val collection = kind != null && Importer.isCollection(link)
        val ytDirect = kind == Importer.YOUTUBE && !ytSimilar
        if (link.isNotBlank()) {
            if (kind != null) {
                val name = when (kind) {
                    Importer.YOUTUBE -> "YouTube"
                    Importer.SPOTIFY -> "Spotify"
                    else -> stringResource(Res.string.import_src_yandex)
                }
                Title(stringResource(if (collection) Res.string.import_detected_playlist else Res.string.import_detected_track, name))
            }
            when (kind) {
                null -> ErrorText(stringResource(Res.string.import_kind_unknown))
                Importer.YANDEX -> Hint(stringResource(Res.string.import_kind_yandex))
            }
        }
        if (kind == Importer.YOUTUBE) {
            Label(stringResource(Res.string.import_yt_mode))
            Choice(stringResource(if (collection) Res.string.import_yt_exact_many else Res.string.import_yt_exact), !ytSimilar) { ytSimilar = false }
            Choice(stringResource(if (collection) Res.string.import_yt_similar_many else Res.string.import_yt_similar), ytSimilar) { ytSimilar = true }
        }
        if (kind != null) {
            if (!ytDirect) Hint(stringResource(Res.string.import_match_hint))
            if (collection) {
                Label(stringResource(Res.string.import_dest_title))
                if (ytDirect) {
                    Choice(stringResource(Res.string.import_dest_local), asPlaylist) { asPlaylist = true }
                    Choice(stringResource(Res.string.import_dest_singles), !asPlaylist) { asPlaylist = false }
                } else {
                    Choice(stringResource(Res.string.import_dest_playlist_sc), asPlaylist) { asPlaylist = true }
                    Choice(stringResource(Res.string.import_as_downloads), !asPlaylist) { asPlaylist = false }
                    if (asPlaylist) Toggle(stringResource(Res.string.import_download_all), "", download) { download = it }
                }
            } else {
                Hint(stringResource(if (ytDirect) Res.string.import_dest_track_singles else Res.string.import_dest_track_downloads))
            }
            if (ytDirect) Hint(stringResource(Res.string.import_youtube_note))
        }
        WideButton(stringResource(Res.string.import_start), enabled = kind != null && !busy) {
            when (kind) {
                Importer.YOUTUBE -> Importer.youtube(context, link, asPlaylist, ytSimilar, download)
                Importer.SPOTIFY -> {
                    if (Prefs.spotifyLinked && Prefs.spotifyTokenOrNull() == null && Prefs.spotifyRefresh.isEmpty()) {
                        pendingSpotify = link
                        spotifyLogin = true
                    } else {
                        Importer.spotify(context, link, asPlaylist, download)
                    }
                }
                Importer.YANDEX -> Importer.yandex(context, link, Prefs.ymToken.ifBlank { null }, asPlaylist, download)
            }
        }
        Spacer(Modifier.padding(bottom = 6.dp))
    }
    ImportProgress()
    val none = stringResource(Res.string.import_acc_none)
    val connected = stringResource(Res.string.import_acc_connected)
    Panel {
        Label(stringResource(Res.string.import_accounts))
        IosRow(
            Res.drawable.ic_user,
            stringResource(Res.string.import_sp_title),
            if (Prefs.spotifyLinked) ExternalAccounts.spotify?.name ?: connected else none,
            onClick = onSpotify,
        )
        IosRow(
            Res.drawable.ic_user,
            stringResource(Res.string.import_yt_section),
            if (Prefs.ytCookies.isNotBlank()) ExternalAccounts.youtube?.name ?: connected else none,
            onClick = onYouTube,
        )
        IosRow(
            Res.drawable.ic_user,
            stringResource(Res.string.import_src_yandex),
            if (Prefs.ymToken.isNotBlank()) ExternalAccounts.yandex?.name ?: connected else none,
            onClick = onYandex,
        )
    }
    Panel { Hint(stringResource(Res.string.import_limit_hint)) }
}

@Composable
internal fun IosSpotifyAccount() {
    val context = LocalAppContext.current
    val scope = rememberCoroutineScope()
    val busy = Importer.busy
    val linked = Prefs.spotifyLinked
    var download by remember { mutableStateOf(true) }
    var clientId by remember { mutableStateOf(Prefs.spotifyClientId) }
    var webLogin by remember { mutableStateOf<String?>(null) }
    var pendingLikes by remember { mutableStateOf(false) }
    val doneText = stringResource(Res.string.import_sp_login_done)

    LaunchedEffect(linked) { if (linked) ExternalAccounts.refreshSpotify() }

    webLogin?.let { mode ->
        SpotifyLoginDialog(refresh = mode == "refresh", onDismiss = {
            webLogin = null
            pendingLikes = false
        }) { t, until ->
            Prefs.changeSpotifyToken(t, until)
            webLogin = null
            showToast(context, doneText)
            if (pendingLikes) Importer.spotifyLikes(context, download)
            pendingLikes = false
            scope.launch { ExternalAccounts.refreshSpotify() }
        }
    }

    Panel {
        if (linked) AccountCard(ExternalAccounts.spotify) else Hint(stringResource(Res.string.import_sp_hint))
        GuideLink("spotify")
        Label(stringResource(Res.string.import_sp_client_id))
        ImportField(
            clientId,
            {
                clientId = it.trim()
                Prefs.changeSpotifyClientId(clientId)
            },
            "0123456789abcdef…",
            error = clientId.isNotBlank() && !ExternalAccounts.clientIdValid(clientId),
        )
        val valid = ExternalAccounts.clientIdValid(clientId)
        when {
            clientId.isBlank() -> Hint(stringResource(Res.string.import_sp_client_hint))
            clientId == ExternalAccounts.spotifyRejectedClient -> ErrorText(stringResource(Res.string.import_sp_client_rejected))
            !valid -> ErrorText(stringResource(Res.string.import_sp_client_bad))
            else -> Hint("Redirect URI: ${SpotifyPkce.REDIRECT}")
        }
        LinkText(stringResource(Res.string.import_sp_dashboard)) { openInBrowser(context, "https://developer.spotify.com/dashboard") }
        if (!linked) {
            WideButton(stringResource(if (valid) Res.string.import_sp_authorize else Res.string.import_sp_login), enabled = !busy) {
                val url = if (valid) SpotifyPkce.authorizeUrl() else null
                if (url != null) openInBrowser(context, url) else webLogin = "login"
            }
        } else {
            Toggle(stringResource(Res.string.import_download_all), "", download) { download = it }
            WideButton(stringResource(Res.string.import_sp_likes), enabled = !busy) {
                if (Prefs.spotifyTokenOrNull() == null && Prefs.spotifyRefresh.isEmpty()) {
                    pendingLikes = true
                    webLogin = "refresh"
                } else {
                    Importer.spotifyLikes(context, download)
                }
            }
            WideButton(stringResource(Res.string.import_acc_forget), enabled = !busy, primary = false) {
                SpotifyAuth.forget()
                ExternalAccounts.forget(ExternalAccounts.SPOTIFY)
            }
        }
        Spacer(Modifier.padding(bottom = 6.dp))
    }
    ImportProgress()
}

@Composable
internal fun IosYouTubeAccount() {
    val context = LocalAppContext.current
    val scope = rememberCoroutineScope()
    val busy = Importer.busy
    val linked = Prefs.ytCookies.isNotBlank()
    var download by remember { mutableStateOf(true) }
    var likesSimilar by remember { mutableStateOf(true) }
    var googleLogin by remember { mutableStateOf(false) }
    val doneText = stringResource(Res.string.import_g_login_done)

    LaunchedEffect(linked) { if (linked) ExternalAccounts.refreshYoutube() }

    if (googleLogin) {
        GoogleLoginDialog(onDismiss = { googleLogin = false }) { cookie ->
            Prefs.changeYtCookies(cookie)
            googleLogin = false
            showToast(context, doneText)
            scope.launch { ExternalAccounts.refreshYoutube() }
        }
    }

    Panel {
        GuideLink("youtube")
        if (!linked) {
            Hint(stringResource(Res.string.import_g_hint))
            WideButton(stringResource(Res.string.import_acc_add), enabled = !busy) { googleLogin = true }
        } else {
            AccountCard(ExternalAccounts.youtube)
            Label(stringResource(Res.string.import_yt_mode))
            Choice(stringResource(Res.string.import_yt_exact_many), !likesSimilar) { likesSimilar = false }
            Choice(stringResource(Res.string.import_yt_similar_many), likesSimilar) { likesSimilar = true }
            if (likesSimilar) {
                Hint(stringResource(Res.string.import_g_likes_hint))
                Toggle(stringResource(Res.string.import_download_all), "", download) { download = it }
            } else {
                Hint(stringResource(Res.string.import_youtube_note))
            }
            WideButton(stringResource(Res.string.import_ym_likes), enabled = !busy) {
                Importer.youtube(context, YouTubeImport.LIKES_URL, asPlaylist = true, similar = likesSimilar, download = download)
            }
            WideButton(stringResource(Res.string.import_acc_forget), enabled = !busy, primary = false) {
                GoogleAuth.forget()
                Prefs.changeYtCookies("")
                ExternalAccounts.forget(ExternalAccounts.YOUTUBE)
            }
        }
        Spacer(Modifier.padding(bottom = 6.dp))
    }
    ImportProgress()
}

@Composable
internal fun IosYandexAccount() {
    val context = LocalAppContext.current
    val scope = rememberCoroutineScope()
    val busy = Importer.busy
    val linked = Prefs.ymToken.isNotBlank()
    var download by remember { mutableStateOf(true) }
    var token by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var rejected by remember { mutableStateOf(false) }
    var awaitingYandex by remember { mutableStateOf(false) }
    var webLogin by remember { mutableStateOf(false) }
    val doneText = stringResource(Res.string.import_ym_login_done)
    val pasteFail = stringResource(Res.string.import_ym_paste_fail)

    LaunchedEffect(linked) {
        if (linked) rejected = ExternalAccounts.checkYandex(Prefs.ymToken) == false
    }

    fun accept(t: String) {
        if (checking) return
        checking = true
        rejected = false
        scope.launch {
            when (ExternalAccounts.checkYandex(t)) {
                false -> rejected = true
                else -> {
                    Prefs.changeYmToken(t)
                    token = ""
                    awaitingYandex = false
                    showToast(context, doneText)
                }
            }
            checking = false
        }
    }

    if (webLogin) {
        YandexLoginDialog(onDismiss = { webLogin = false }) { t ->
            webLogin = false
            token = t
            accept(t)
        }
    }

    Panel {
        GuideLink("yandex-music")
        if (!linked) {
            Hint(stringResource(Res.string.import_ym_steps))
            ImportField(
                token,
                {
                    token = YandexAuth.tokenFrom(it.trim()) ?: it.trim()
                    rejected = false
                },
                stringResource(Res.string.import_ym_token),
                secret = true,
                error = rejected,
            )
            if (rejected) ErrorText(stringResource(Res.string.import_err_token))
            WideButton(
                when {
                    checking -> stringResource(Res.string.import_acc_checking)
                    token.isNotBlank() -> stringResource(Res.string.import_ym_use_token)
                    else -> stringResource(Res.string.import_ym_auto)
                },
                enabled = !busy && !checking,
            ) {
                if (token.isNotBlank()) {
                    accept(token)
                } else {
                    awaitingYandex = false
                    webLogin = true
                }
            }
            WideButton(stringResource(Res.string.import_ym_login), enabled = !busy && !checking, primary = false) {
                awaitingYandex = true
                openInBrowser(context, YandexAuth.AUTH_URL)
            }
            if (awaitingYandex) {
                Hint(stringResource(Res.string.import_ym_browser_hint))
                LinkText(stringResource(Res.string.import_ym_paste)) {
                    val t = YandexAuth.tokenFrom(UIPasteboard.generalPasteboard.string)
                    if (t == null) {
                        showToast(context, pasteFail)
                    } else {
                        token = t
                        accept(t)
                    }
                }
            }
        } else {
            AccountCard(ExternalAccounts.yandex)
            if (rejected) ErrorText(stringResource(Res.string.import_err_token))
            Toggle(stringResource(Res.string.import_download_all), "", download) { download = it }
            WideButton(stringResource(Res.string.import_ym_likes), enabled = !busy) {
                Importer.yandex(context, null, Prefs.ymToken, true, download)
            }
            WideButton(stringResource(Res.string.import_acc_forget), enabled = !busy, primary = false) {
                Prefs.changeYmToken("")
                YandexAuth.forget()
                ExternalAccounts.forget(ExternalAccounts.YANDEX)
                rejected = false
            }
        }
        Hint(stringResource(Res.string.import_ym_note))
        Spacer(Modifier.padding(bottom = 6.dp))
    }
    ImportProgress()
}
