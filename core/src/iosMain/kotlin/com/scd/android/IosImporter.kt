package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

actual object Importer {
    actual val YOUTUBE: String = "youtube"
    actual val SPOTIFY: String = "spotify"
    actual val YANDEX: String = "yandex"
    private const val CHUNK = 500

    actual var state by mutableStateOf<ImportState?>(null)

    private var job: Job? = null

    actual val busy: Boolean
        get() = state?.stage.let { it == ImportStage.Reading || it == ImportStage.Matching || it == ImportStage.Saving }

    actual fun detect(link: String): String? = when {
        YouTubeClient.parse(link) != null -> YOUTUBE
        SpotifySource.parse(link) != null -> SPOTIFY
        YandexSource.parse(link) != null -> YANDEX
        else -> null
    }

    actual fun cancel() {
        job?.cancel()
        job = null
        state = state?.copy(stage = ImportStage.Failed, error = "cancelled")
    }

    actual fun clear() {
        if (!busy) state = null
    }

    private suspend fun set(block: (ImportState) -> ImportState) = withContext(Dispatchers.Main) {
        state = state?.let(block)
    }

    private fun progress(n: Int, total: Int? = null) {
        AppScope.scope.launch(Dispatchers.Main) { state = state?.copy(done = n, total = total ?: state?.total ?: 0) }
    }

    private fun run(source: String, body: suspend () -> Unit) {
        if (busy) return
        state = ImportState(source)
        job = AppScope.scope.launch {
            try {
                body()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val code = (e as? ImportException)?.code ?: e.message?.take(120) ?: "error"
                Logs.add("import", "$source failed: ${e.message?.take(120)}")
                set { it.copy(stage = ImportStage.Failed, error = code) }
            }
        }
    }

    actual fun youtube(context: PlatformContext, link: String, asPlaylist: Boolean, similar: Boolean, download: Boolean) {
        val ref = YouTubeClient.parse(link) ?: return
        run(YOUTUBE) {
            if (similar) {
                val list = withContext(IoDispatcher) {
                    when (ref) {
                        is YtRef.Video -> {
                            val (v, _) = YouTubeImport.client.video(ref.id)
                            ExtList(v.title, listOf(YouTubeImport.asExt(v)))
                        }
                        is YtRef.Playlist -> {
                            val pl = YouTubeImport.client.playlist(ref.id) { n -> progress(n) }
                            ExtList(pl.title, pl.items.map { YouTubeImport.asExt(it) })
                        }
                    }
                }
                val toPlaylist = ref is YtRef.Playlist && asPlaylist
                matchAndSave(context, list, if (toPlaylist) list.title else null, if (toPlaylist) download else true)
                return@run
            }
            when (ref) {
                is YtRef.Video -> {
                    val (v, _) = withContext(IoDispatcher) { YouTubeImport.client.video(ref.id) }
                    val t = YouTubeImport.track(v)
                    set { it.copy(title = v.title, total = 1, done = 1, found = 1, stage = ImportStage.Saving) }
                    withContext(Dispatchers.Main) {
                        val singles = singlesPlaylist()
                        LocalPlaylists.add(singles.id, t)
                        Downloads.enqueue(context, listOf(t), singles.id, singles.title)
                    }
                    set { it.copy(stage = ImportStage.Done) }
                }
                is YtRef.Playlist -> {
                    val pl = withContext(IoDispatcher) { YouTubeImport.client.playlist(ref.id) { n -> progress(n, n) } }
                    val tracks = pl.items.map { YouTubeImport.track(it) }
                    set { it.copy(title = pl.title, total = tracks.size, done = tracks.size, found = tracks.size, stage = ImportStage.Saving) }
                    withContext(Dispatchers.Main) {
                        if (asPlaylist) {
                            val p = LocalPlaylists.create(pl.title, true, tracks, localOnly = true, source = YouTubeImport.SOURCE)
                            Downloads.enqueue(context, tracks, p.id, p.title)
                        } else {
                            val singles = singlesPlaylist()
                            tracks.forEach { LocalPlaylists.add(singles.id, it) }
                            Downloads.enqueue(context, tracks, singles.id, singles.title)
                        }
                    }
                    set { it.copy(stage = ImportStage.Done, playlists = 1) }
                }
            }
        }
    }

    actual fun spotify(context: PlatformContext, link: String, asPlaylist: Boolean, download: Boolean) {
        val ref = SpotifySource.parse(link) ?: return
        run(SPOTIFY) {
            val list = withContext(IoDispatcher) {
                SpotifySource(SpotifyPkce.token()).fetch(ref.first, ref.second) { n, _ -> progress(n) }
            }
            val toPlaylist = ref.first != "track" && asPlaylist
            matchAndSave(context, list, if (toPlaylist) list.title else null, if (toPlaylist) download else true)
        }
    }

    fun spotifyLikes(context: PlatformContext, download: Boolean) {
        if (Prefs.spotifyTokenOrNull() == null && Prefs.spotifyRefresh.isEmpty()) return
        run(SPOTIFY) {
            val token = SpotifyPkce.token() ?: throw ImportException("sp_token", "spotify token expired")
            val list = withContext(IoDispatcher) { SpotifySource(token).likes { n, _ -> progress(n) } }
            matchAndSave(context, list, list.title, download)
        }
    }

    actual fun yandex(context: PlatformContext, link: String?, token: String?, asPlaylist: Boolean, download: Boolean) {
        val ref = link?.let { YandexSource.parse(it) }
        if (link != null && ref == null) return
        run(YANDEX) {
            val source = YandexSource(token)
            val list = withContext(IoDispatcher) {
                val onProgress: (Int, Int) -> Unit = { n, total -> progress(n, total) }
                if (ref == null) source.likes(onProgress) else source.fetch(ref, onProgress)
            }
            val toPlaylist = ref !is YmRef.Track && asPlaylist
            matchAndSave(context, list, if (toPlaylist) list.title else null, if (toPlaylist) download else true)
        }
    }

    private fun singlesPlaylist(): LocalPlaylist =
        LocalPlaylists.visible.firstOrNull { it.source == YouTubeImport.SINGLES }
            ?: LocalPlaylists.create(Strings.get("import_yt_singles"), true, emptyList(), localOnly = true, source = YouTubeImport.SINGLES)

    fun isCollection(link: String): Boolean {
        YouTubeClient.parse(link)?.let { return it is YtRef.Playlist }
        SpotifySource.parse(link)?.let { return it.first != "track" }
        YandexSource.parse(link)?.let { return it !is YmRef.Track }
        return false
    }

    private fun simplify(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()

    private fun uploadedBy(t: Track, artist: String): Boolean {
        val a = simplify(artist.substringBefore(',').substringBefore('&'))
        val u = simplify(t.user?.username.orEmpty())
        return a.isNotEmpty() && (u == a || (a.length >= 3 && u.contains(a)))
    }

    private fun closeDuration(t: Track, want: Long): Boolean {
        if (want <= 0L) return true
        val have = t.displayDuration
        if (have <= 0L) return true
        val diff = kotlin.math.abs(have - want)
        return diff <= 15_000L || diff <= want / 5
    }

    private suspend fun match(t: ExtTrack): Track? {
        val artist = t.artist.substringBefore(',').trim()
        val q = "$artist ${t.title}".trim()
        val combined = runCatching { Api.searchTracks(q, 0, 3).collection }.getOrElse {
            if (it is CancellationException) throw it
            delay(1_000)
            Api.searchTracks(q, 0, 3).collection
        }.filter { !it.hidden }
        combined.firstOrNull { closeDuration(it, t.durationMs) }?.let { return it }
        if (artist.isBlank() || t.title.isBlank()) return combined.firstOrNull()
        val byTitle = runCatching { Api.searchTracks(t.title, 0, 20).collection }.getOrDefault(emptyList())
        return byTitle.firstOrNull { uploadedBy(it, artist) && closeDuration(it, t.durationMs) } ?: combined.firstOrNull()
    }

    private suspend fun matchAndSave(context: PlatformContext, list: ExtList, playlistTitle: String?, download: Boolean) {
        set { it.copy(title = list.title, total = list.tracks.size, done = 0, stage = ImportStage.Matching) }
        val found = mutableListOf<Track>()
        val missed = mutableListOf<String>()
        var errorsInRow = 0
        for ((i, t) in list.tracks.withIndex()) {
            currentCoroutineContext().ensureActive()
            val m = runCatching { match(t) }
                .onFailure {
                    if (it is CancellationException) throw it
                    errorsInRow++
                }
                .onSuccess { errorsInRow = 0 }
                .getOrNull()
            if (errorsInRow >= 15) throw ImportException("search", "search unavailable")
            if (m != null && found.none { it.urn == m.urn }) found += m else if (m == null) missed += "${t.artist} — ${t.title}"
            set { it.copy(done = i + 1, found = found.size, missed = missed.takeLast(200)) }
            delay(120)
        }
        if (found.isEmpty()) throw ImportException("nothing", "nothing found")
        set { it.copy(stage = ImportStage.Saving) }
        var playlists = 0
        withContext(Dispatchers.Main) {
            if (playlistTitle == null) {
                if (download) Downloads.enqueue(context, found)
            } else {
                found.chunked(CHUNK).forEachIndexed { idx, chunk ->
                    val name = if (idx == 0) playlistTitle else "$playlistTitle ${idx + 1}"
                    val p = LocalPlaylists.create(name, true, chunk)
                    playlists++
                    if (download) Downloads.enqueue(context, chunk, p.id, p.title)
                }
            }
        }
        Logs.add("import", "${list.title.take(30)}: ${found.size}/${list.tracks.size} found")
        set { it.copy(stage = ImportStage.Done, playlists = playlists, partial = list.partial) }
    }
}
