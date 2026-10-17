package com.scd.android

import com.scd.android.res.*
import org.jetbrains.compose.resources.StringResource
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

private enum class MeTab { Likes, Playlists, History, Offline }

private object MeMemory {
    var guest: Boolean? = null
    val tab = androidx.compose.runtime.mutableStateOf(MeTab.Likes)
    val scroll = HashMap<String, androidx.compose.foundation.lazy.LazyListState>()
}

@Composable
fun MeScreen(offline: Boolean, onSignOut: () -> Unit, guest: Boolean) {
    val nav = LocalNav.current
    val controller = LocalController.current
    val context = LocalAppContext.current
    val scope = rememberCoroutineScope()
    if (MeMemory.guest != guest) {
        MeMemory.guest = guest
        MeMemory.tab.value = if (guest) MeTab.Offline else MeTab.Likes
    }
    var tab by MeMemory.tab
    var showCreate by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(guest, offline) { if (!guest) Library.refreshProfile(offline) }
    LaunchedEffect(guest) { if (!guest && Library.soundprint.isEmpty()) Library.loadSoundprint() }
    LaunchedEffect(tab, guest) { if (tab == MeTab.Playlists && !guest) Library.refreshPlaylists(offline) }

    val saved = Downloads.downloaded
    val offlineTracks = remember(saved) { Downloads.tracks() }
    val pager = rememberPaged("me:${tab.name}:$offline:$guest") { page, fresh ->
        when (tab) {
            MeTab.Likes -> if (guest) offlineTracks.filter { Likes.isLiked(it.urn) } to false else Library.likedPage(page, fresh, offline)
            MeTab.History -> if (guest) emptyList<Track>() to false else Library.historyPage(page, fresh, offline)
            else -> emptyList<Track>() to false
        }
    }

    val filter = remember(tab) { LocalFilter() }
    var fullList by remember(tab, offline, guest) { mutableStateOf<List<Track>?>(null) }
    LaunchedEffect(filter.open) { if (!filter.open) fullList = null }
    LaunchedEffect(filter.active, tab) {
        if (filter.active && fullList == null && tab != MeTab.Offline && tab != MeTab.Playlists) {
            fullList = runCatching { pager.all() }.getOrNull()
        }
    }
    val list = when (tab) {
        MeTab.Offline -> offlineTracks
        MeTab.Playlists -> emptyList()
        else -> pager.items
    }

    fun playMix() {
        val pool = Library.soundprint.ifEmpty { pager.items }.ifEmpty { offlineTracks }
        Playback.playShuffled(controller, pool.filter(Playback::playable))
    }

    val listState = MeMemory.scroll.getOrPut("${tab.name}:$guest") { androidx.compose.foundation.lazy.LazyListState() }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = dockContentPadding()) {
        item(key = "hero", contentType = "hero") {
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 10.dp, bottom = 16.dp)) {
                MeHero(
                    guest = guest,
                    onPlay = { playMix() },
                    onMenu = { if (guest) showMenu = true else nav.push(Screen.SettingsS) },
                )
            }
        }
        item(key = "seg", contentType = "seg") {
            SegmentDock(
                labels = listOf(
                    stringResource(Res.string.me_tab_likes),
                    stringResource(Res.string.playlists),
                    stringResource(Res.string.history),
                    stringResource(Res.string.me_tab_offline),
                ),
                selected = tab.ordinal,
                onSelect = { tab = MeTab.entries[it] },
            )
        }
        when (tab) {
            MeTab.Playlists -> playlistsTab(
                guest = guest,
                offline = offline,
                onCreate = { showCreate = true },
                onOpen = { nav.push(Screen.PlaylistS(it)) },
                onArtist = { nav.push(Screen.ArtistS(it)) },
            )
            else -> {
                item(key = "toolbar:${tab.name}", contentType = "toolbar") {
                    val total = if (tab == MeTab.Likes && !guest) maxOf(Likes.urns.size, list.size) else list.size
                    ListToolbar(count = total, downloaded = list.count { Downloads.isDownloaded(it.urn) }) {
                        if (list.isNotEmpty() || filter.open) LocalSearchOrb(filter)
                        if (tab == MeTab.History && !offline && list.isNotEmpty()) {
                            IconOrb(Res.drawable.ic_trash, stringResource(Res.string.history_clear), size = 44.dp, iconSize = 18.dp) { confirmClear = true }
                        }
                        if (tab == MeTab.Likes && !offline && list.isNotEmpty()) {
                            DownloadAllOrb("liked", list, complete = !pager.hasMore) { pager.all() }
                        }
                    }
                }
                val source = if (tab == MeTab.Offline) list else fullList ?: list
                val shown = if (filter.active) filterTracks(source, filter.query) else list
                if (filter.open) {
                    item(key = "search:${tab.name}", contentType = "search") {
                        LocalSearchField(filter, if (filter.active) shown.size else null)
                    }
                }
                trackItems(
                    shown,
                    onPlay = { t ->
                        val paged = !filter.active && (tab == MeTab.Likes || tab == MeTab.History)
                        Playback.play(controller, shown, t, more = if (paged) suspend { pager.all() } else null)
                    },
                    dimUndownloaded = offline && tab != MeTab.Offline,
                )
                if (filter.active && shown.isEmpty() && list.isNotEmpty()) {
                    item(key = "nothing:${tab.name}") {
                        if (tab != MeTab.Offline && fullList == null) Spinner() else EmptyState(Res.drawable.ic_search, stringResource(Res.string.local_search_empty))
                    }
                }
                if (!filter.active && tab != MeTab.Offline && pager.hasMore && list.isNotEmpty()) {
                    item(key = "more:${tab.name}") {
                        LaunchedEffect(list.size) { pager.loadMore() }
                        Spinner()
                    }
                }
                if (list.isEmpty()) {
                    item(key = "empty:${tab.name}") {
                        when {
                            tab != MeTab.Offline && pager.loading -> SkeletonRows()
                            tab != MeTab.Offline && pager.error != null -> EmptyState(
                                Res.drawable.ic_no_wifi,
                                pager.error ?: "",
                                action = stringResource(Res.string.wave_refresh),
                                onAction = { pager.refresh() },
                            )
                            else -> EmptyState(
                                when (tab) {
                                    MeTab.Likes -> Res.drawable.ic_heart
                                    MeTab.History -> Res.drawable.ic_history
                                    else -> Res.drawable.ic_download
                                },
                                stringResource(
                                    when (tab) {
                                        MeTab.Likes -> Res.string.me_empty_likes
                                        MeTab.History -> Res.string.me_empty_history
                                        else -> Res.string.me_empty_offline
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(onDismiss = { showCreate = false }) { title, private ->
            LocalPlaylists.create(title, private)
            tab = MeTab.Playlists
        }
    }
    if (confirmClear) {
        GlassDialog(
            onDismiss = { confirmClear = false },
            title = stringResource(Res.string.history_clear),
            text = stringResource(Res.string.history_clear_q),
            confirm = stringResource(Res.string.delete),
            danger = true,
            onConfirm = {
                confirmClear = false
                scope.launch {
                    val ok = runCatching { Api.clearHistory() }.isSuccess
                    if (ok) pager.refresh() else showToast(context, text(networkErrorText()))
                }
            },
        )
    }
    if (showMenu) {
        GlassSheet({ showMenu = false }, Prefs.username ?: stringResource(Res.string.tab_me)) {
            SheetAction(Res.drawable.ic_settings, stringResource(Res.string.settings)) {
                showMenu = false
                nav.push(Screen.SettingsS)
            }
            if (guest) {
                SheetAction(Res.drawable.ic_user, stringResource(Res.string.login_button), tint = LocalGlass.current.accent) {
                    showMenu = false
                    onSignOut()
                }
            }
        }
    }
}

@Composable
private fun MeHero(guest: Boolean, onPlay: () -> Unit, onMenu: () -> Unit) {
    val p = LocalGlass.current
    val nav = LocalNav.current
    val mine = if (guest) null else userAuraColors(Auras.mine)
    val aura = remember(Library.soundprint, p.accent, mine) { mine ?: auraColors(Library.soundprint, p.accent) }
    val playlists = LocalPlaylists.visible.size + Library.likedPlaylists.distinctBy { it.urn }.size
    var auraOpen by remember { mutableStateOf(false) }
    if (auraOpen) AuraPickerDialog(onDismiss = { auraOpen = false })
    HeroPanel(glow = aura.first()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(84.dp).then(if (!guest && !Prefs.offline) Modifier.pressable { auraOpen = true } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (Prefs.star || mine != null) AuraRing(spinning = NowPlaying.isPlaying || mine != null, colors = if (mine != null) aura else listOf(p.accent) + aura, corner = 26.dp, modifier = Modifier.size(84.dp))
                    Box(
                        Modifier
                            .size(76.dp)
                            .shadow(18.dp, RoundedCornerShape(22.dp), clip = false, ambientColor = p.accent, spotColor = p.accent)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Brush.linearGradient(listOf(p.accent, aura.last()))),
                        contentAlignment = Alignment.Center,
                    ) {
                        val avatar = Prefs.avatarUrl
                        if (avatar != null && !guest) {
                            AsyncImage(
                                model = Api.artworkUrl(avatar, "t300x300"),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                        } else {
                            Icon(painterResource(Res.drawable.ic_user), null, tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (Prefs.star) StarBadgeGlass()
                        if (Prefs.star && Prefs.hqStreaming) {
                            Text(
                                "HQ",
                                style = GlassType.eyebrow.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
                                color = p.text2,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .border(0.75.dp, p.borderHi, CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        if (guest) GlassChip(stringResource(Res.string.me_guest), icon = Res.drawable.ic_no_wifi)
                    }
                    Spacer(Modifier.height(4.dp))
                    GradientText(
                        if (guest) stringResource(Res.string.downloads) else Prefs.username ?: "SoundCloud",
                        GlassType.title,
                        listOf(p.text, p.text, aura.getOrElse(1) { p.accentHover }),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    StatLine(
                        listOfNotNull(
                            if (!guest) formatCount(Likes.urns.size) to stringResource(Res.string.me_stat_likes) else null,
                            if (!guest) formatCount(playlists) to stringResource(Res.string.me_stat_playlists) else null,
                            formatCount(Downloads.downloaded.size) to stringResource(Res.string.me_stat_offline),
                        ),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WhiteButton(stringResource(Res.string.play_your_sound), Res.drawable.ic_play, Modifier.weight(1f), onClick = onPlay)
                Spacer(Modifier.width(10.dp))
                if (!guest && !Prefs.offline) {
                    IconOrb(Res.drawable.ic_stats, stringResource(Res.string.stats_title), size = 48.dp) { nav.push(Screen.StatsS) }
                    Spacer(Modifier.width(8.dp))
                }
                IconOrb(Res.drawable.ic_settings, stringResource(Res.string.settings), size = 48.dp, onClick = onMenu)
            }
        }
    }
}

private fun LazyListScope.playlistsTab(
    guest: Boolean,
    offline: Boolean,
    onCreate: () -> Unit,
    onOpen: (Playlist) -> Unit,
    onArtist: (String) -> Unit,
) {
    item(key = "pl:spacer") { Spacer(Modifier.height(10.dp)) }
    if (!guest) {
        item(key = "pl:create", contentType = "row") {
            val p = LocalGlass.current
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = !offline, onClick = onCreate)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).glass(RoundedCornerShape(14.dp), p, shadow = 0.dp, tint = p.glow), contentAlignment = Alignment.Center) {
                    Icon(painterResource(Res.drawable.ic_plus), null, tint = p.accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(Res.string.new_playlist), style = GlassType.rowTitle, color = p.text)
            }
        }
    }
    val own = LocalPlaylists.visible
    if (own.isNotEmpty()) {
        item(key = "pl:own:h") { MeSection(Res.string.me_your_playlists) }
        items(own, key = { "own:" + it.id }, contentType = { "playlist" }) { pl ->
            val count = (if (pl.localOnly) stringResource(Res.string.playlist_local) + " · " else "") + stringResource(Res.string.tracks_count, pl.count)
            PlaylistRowGlass(
                playlist = pl.asPlaylist(),
                title = pl.title,
                subtitle = if (pl.syncing) count + " · " + stringResource(Res.string.playlist_syncing) else count,
                syncing = pl.syncing,
                locked = pl.isPrivate && !pl.localOnly,
                onClick = { onOpen(pl.asPlaylist()) },
            )
        }
    }
    val liked = Library.likedPlaylists.distinctBy { it.urn }
    if (liked.isNotEmpty()) {
        item(key = "pl:liked:h") { MeSection(Res.string.liked_playlists) }
        items(liked, key = { "liked:" + it.urn }, contentType = { "playlist" }) { pl ->
            PlaylistRowGlass(
                playlist = pl,
                title = pl.title,
                subtitle = listOfNotNull(pl.user?.username, stringResource(Res.string.tracks_count, pl.track_count)).joinToString(" · "),
                onClick = { onOpen(pl) },
            )
        }
    }
    val artists = LikedArtists.artists.distinctBy { it.urn }
    if (artists.isNotEmpty()) {
        item(key = "pl:artists:h") { MeSection(Res.string.me_artists) }
        items(artists, key = { "artist:" + it.urn }, contentType = { "artist" }) { a ->
            ArtistRowGlass(a.username, a.avatar_url, stringResource(Res.string.artist_label)) { onArtist(a.urn) }
        }
    }
    if (own.isEmpty() && liked.isEmpty() && artists.isEmpty()) {
        item(key = "pl:empty") { EmptyState(Res.drawable.ic_music, stringResource(Res.string.me_empty_playlists)) }
    }
}

@Composable
private fun MeSection(title: StringResource) {
    val p = LocalGlass.current
    Eyebrow(stringResource(title), Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 8.dp), color = p.text3)
}

@Composable
fun DislikedDetail() {
    val nav = LocalNav.current
    val controller = LocalController.current
    val p = LocalGlass.current
    val pager = rememberPaged("disliked") { page, fresh -> if (fresh) emptyList<Track>() to false else Dislikes.page(page) }
    val shown = pager.items.filter { Dislikes.isDisliked(it.urn) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = dockContentPadding()) {
        item(key = "top") {
            ScreenTop(stringResource(Res.string.disliked), onBack = { nav.pop() })
            Text(
                stringResource(Res.string.disliked_sub),
                style = GlassType.small,
                color = p.text2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        trackItems(shown, onPlay = { t -> Playback.play(controller, listOf(t), t) }, trailing = { t ->
            val scope = rememberCoroutineScope()
            Spacer(Modifier.width(6.dp))
            GlassChip(stringResource(Res.string.undislike), onClick = { scope.launch { Dislikes.toggle(t) } })
        })
        if (pager.hasMore && shown.isNotEmpty()) {
            item(key = "more") {
                LaunchedEffect(pager.items.size) { pager.loadMore() }
                Spinner()
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                if (pager.loading) SkeletonRows() else EmptyState(Res.drawable.ic_thumb_down, stringResource(Res.string.disliked_empty))
            }
        }
    }
}
