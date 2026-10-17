package com.scd.android

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

const val LOCAL_RADIO_CURSOR = "local-radio"

interface WaveBackup {
    suspend fun batch(limit: Int): List<Track>
    fun reset()
    fun likeSeed(): String?
}

object Api {
    val API_BASE get() = if (Endpoints.starApiActive) Endpoints.API_STAR else Endpoints.apiBase
    val CONTROL_BASE get() = Endpoints.apiBase
    val STREAM_BASE get() = Endpoints.streamBase
    val IMAGES_BASE get() = Endpoints.imageBase

    private const val KEY_SESSION = "session_id"

    private var store: KvStore? = null

    var waveBackup: WaveBackup? = null

    var onSessionCleared: () -> Unit = {}

    @Volatile
    var sessionId: String? = null
        private set

    fun attachStore(kv: KvStore) {
        if (store == null) store = kv
    }

    fun loadSession() {
        sessionId = store?.getString(KEY_SESSION, null)
    }

    fun storeSession(id: String?) {
        sessionId = id
        store?.edit()?.putString(KEY_SESSION, id)?.apply()
        if (id == null) onSessionCleared()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun neverReachedServer(e: Throwable): Boolean = isUnreachable(e)

    private fun sessionHeader(): List<Pair<String, String>> = sessionId?.let { listOf("x-session-id" to it) }.orEmpty()

    private suspend fun mutate(method: String, path: String, body: HttpBody?): String =
        withContext(IoDispatcher) {
            val bases = if (Endpoints.starApiActive) listOf(Endpoints.API_STAR, Endpoints.apiBase) else listOf(Endpoints.apiBase)
            var last: Exception? = null
            for ((i, base) in bases.withIndex()) {
                val hasNext = i < bases.lastIndex
                val host = base.removePrefix("https://")
                val req = HttpRequest(base + path, method, sessionHeader(), body, HttpProfile.MUTATE)
                try {
                    Http.execute(req).let { res ->
                        val text = res.text()
                        if (res.isSuccessful) return@withContext text
                        Logs.add("api", "$method $path → ${res.code} @$host ${text.take(120).replace('\n', ' ')}")
                        val html = res.header("content-type")?.contains("text/html", ignoreCase = true) == true
                        val infra = res.code in 502..504 && html
                        val starGate = base == Endpoints.API_STAR && res.code == 403
                        if (hasNext && ((infra && method != "POST") || starGate)) {
                            last = ApiHttpException(res.code, text.take(300))
                            return@let
                        }
                        if (res.code == 401) SessionState.markExpired()
                        throw ApiHttpException(res.code, text.take(300))
                    }
                } catch (e: ApiHttpException) {
                    throw e
                } catch (e: IOException) {
                    Logs.add("api", "$method $path fail ${e::class.simpleName} @$host")
                    last = e
                    if (!hasNext || !neverReachedServer(e)) throw e
                }
            }
            throw last ?: IOException("mutation failed")
        }

    suspend fun vibeSearch(q: String, limit: Int = 48): VibeSearchResponse =
        getJson("$API_BASE/search/vibe?q=${enc(q)}&limit=$limit", VibeSearchResponse.serializer())

    suspend fun searchTracks(q: String, page: Int = 0, limit: Int = 20): PagedTracks =
        getJson("$API_BASE/tracks?limit=$limit&page=$page&q=${enc(q)}", PagedTracks.serializer())

    suspend fun searchPlaylists(q: String, page: Int = 0, limit: Int = 20): PagedPlaylists =
        getJson("$API_BASE/playlists?limit=$limit&page=$page&q=${enc(q)}", PagedPlaylists.serializer())

    suspend fun searchUsers(q: String, page: Int = 0, limit: Int = 20): PagedUsers =
        getJson("$API_BASE/users?limit=$limit&page=$page&q=${enc(q)}", PagedUsers.serializer())

    suspend fun user(urn: String): Artist =
        getJson("$API_BASE/users/${enc(urn)}", Artist.serializer())

    suspend fun userTracks(urn: String, page: Int = 0, limit: Int = 30): PagedTracks =
        getJson("$API_BASE/users/${enc(urn)}/tracks?limit=$limit&page=$page", PagedTracks.serializer())

    suspend fun userPlaylists(urn: String, page: Int = 0, limit: Int = 30): PagedPlaylists =
        getJson("$API_BASE/users/${enc(urn)}/playlists?limit=$limit&page=$page", PagedPlaylists.serializer())

    suspend fun authLogin(): LoginResponse =
        getJson("$API_BASE/auth/login", LoginResponse.serializer())

    suspend fun authLoginStatus(id: String): LoginStatus =
        getJson("$API_BASE/auth/login/status?id=${enc(id)}", LoginStatus.serializer(), fresh = true)

    suspend fun playlistInfo(urn: String): Playlist =
        getJson("$API_BASE/playlists/${enc(urn)}", Playlist.serializer())

    suspend fun trackByUrn(urn: String): Track =
        getJson("$API_BASE/tracks/${enc(urn)}", Track.serializer()).also {
            DurationCache.record(it.urn, maxOf(it.duration, it.full_duration ?: 0L))
        }

    @Volatile
    var lastResolvedTrack: Track? = null
        private set

    private suspend fun expandShortLink(url: String): String {
        val host = url.substringAfter("://").substringBefore('/').lowercase()
        if (host != "on.soundcloud.com") return url
        return runCatching {
            Http.execute(
                HttpRequest(url, headers = listOf("User-Agent" to "Mozilla/5.0"), profile = HttpProfile.SHORT_LINK, discardBody = true),
            ).finalUrl
        }.getOrDefault(url).also { Logs.add("link", "short → ${it.substringAfter("://").substringBefore('?')}") }
    }

    private val profileTabs = setOf(
        "tracks", "albums", "sets", "reposts", "likes", "popular-tracks", "followers", "following",
        "comments", "spotlight", "playlists", "toptracks",
    )

    fun normalizeLink(url: String): String {
        val scheme = url.substringBefore("://", "https")
        val rest = url.substringAfter("://")
        val host = rest.substringBefore('/').lowercase().removePrefix("www.").removePrefix("m.")
        val query = rest.substringAfter('?', "").let { if (it.isEmpty()) "" else "?$it" }
        val segs = rest.substringAfter('/', "").substringBefore('?').split('/').filter { it.isNotEmpty() }
        val path = if (segs.size == 2 && segs[1].lowercase() in profileTabs) listOf(segs[0]) else segs
        return "$scheme://$host/" + path.joinToString("/") + query
    }

    private fun looksLikeTrack(url: String): Boolean {
        val segs = url.substringAfter("://").substringAfter('/', "").substringBefore('?')
            .split('/').filter { it.isNotEmpty() }
        return segs.size == 2 && segs[1].lowercase() !in profileTabs
    }

    private suspend fun resolveOnStream(clean: String): ResolveResult? {
        val bases = listOf(Endpoints.STREAM_STAR, STREAM_BASE) + Endpoints.streamHosts
        for (base in bases.distinct()) {
            val body = runCatching {
                val res = Http.execute(HttpRequest("$base/resolve?url=${enc(clean)}", profile = HttpProfile.PROBE, forceNetwork = true))
                if (res.isSuccessful) res.text() else {
                    Logs.add("link", "resolve HTTP ${res.code} @${base.removePrefix("https://")}")
                    null
                }
            }.getOrNull() ?: continue
            val parsed = runCatching { json.decodeFromString(ResolveResult.serializer(), body) }.getOrNull()
                ?.takeIf { it.urn.isNotEmpty() } ?: continue
            if (parsed.type == "track") {
                lastResolvedTrack = runCatching { json.decodeFromString(Track.serializer(), body) }.getOrNull()
            }
            Logs.add("link", "resolved ${parsed.type} via ${base.removePrefix("https://")}")
            return parsed
        }
        return null
    }

    suspend fun resolve(url: String): ResolveResult? = withContext(IoDispatcher) {
        val clean = normalizeLink(shareUrl(expandShortLink(url)))
        val track = looksLikeTrack(clean)
        if (track) resolveOnStream(clean)?.let { return@withContext it }
        val viaApi = runCatching { getJson("$API_BASE/resolve?url=${enc(clean)}", ResolveResult.serializer()) }
            .onFailure { Logs.add("link", "resolve failed: ${it.message?.take(120)}") }
            .getOrNull()
            ?.takeIf { it.urn.isNotEmpty() }
        if (viaApi != null) {
            Logs.add("link", "resolved ${viaApi.type} via api")
            return@withContext viaApi
        }
        if (!track) resolveOnStream(clean) else null
    }

    fun shareUrl(url: String): String {
        val trimmed = url.trim().substringBefore('#')
        val q = trimmed.indexOf('?')
        if (q < 0) return trimmed
        val base = trimmed.substring(0, q)
        val kept = trimmed.substring(q + 1)
            .split('&')
            .filter { part ->
                part.isNotEmpty() &&
                    !part.startsWith("utm_") &&
                    !part.startsWith("si=") &&
                    !part.startsWith("ref=") &&
                    !part.startsWith("in=")
            }
        return if (kept.isEmpty()) base else base + "?" + kept.joinToString("&")
    }

    suspend fun relatedTracks(urn: String, limit: Int = 40, page: Int = 0): PagedTracks =
        getJson("$API_BASE/tracks/${enc(urn)}/related?limit=$limit&page=$page", PagedTracks.serializer())

    suspend fun tracksByIds(ids: List<String>): List<Track>? {
        if (ids.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, Track>()
        for (chunk in ids.distinct().chunked(50)) {
            val body = runCatching {
                getJson("$API_BASE/tracks?ids=${chunk.joinToString(",")}", kotlinx.serialization.json.JsonElement.serializer(), fresh = true)
            }.getOrNull() ?: return null
            val list = when (body) {
                is kotlinx.serialization.json.JsonArray -> body
                is kotlinx.serialization.json.JsonObject -> body["collection"] as? kotlinx.serialization.json.JsonArray
                else -> null
            } ?: return null
            list.forEach { el ->
                runCatching { json.decodeFromJsonElement(Track.serializer(), el) }.getOrNull()?.let {
                    DurationCache.record(it.urn, maxOf(it.duration, it.full_duration ?: 0L))
                    out[it.urn.substringAfterLast(':')] = it
                }
            }
        }
        val result = ids.mapNotNull { out[it] }
        if (result.isEmpty()) return null
        if (result.size < ids.size) Logs.add("wave", "batch: ${result.size}/${ids.size} tracks")
        return result
    }

    @Volatile
    private var waveSeed: String? = null

    private suspend fun waveBatch(
        cursor: String?,
        limit: Int,
        seedTrack: String?,
        hideListened: Boolean?,
    ): Pair<List<Track>, String> {
        val url = buildString {
            if (seedTrack != null) {
                append("$API_BASE/recommendations/wave/from-track/${enc(seedTrack.substringAfterLast(':'))}?limit=$limit")
            } else {
                append("$API_BASE/recommendations/wave?limit=$limit")
            }
            cursor?.takeIf { it.isNotEmpty() }?.let { append("&cursor=${enc(it)}") }
            hideListened?.let { append("&hide_listened=${if (it) 1 else 0}") }
        }
        val payload = getJson(url, WavePayload.serializer(), fresh = true)
        val ids = payload.tracks.map { it.id.content.removePrefix("soundcloud:tracks:") }
        val tracks = tracksByIds(ids) ?: coroutineScope {
            ids.map { id ->
                async {
                    kotlinx.coroutines.withTimeoutOrNull(20_000) {
                        runCatching { trackByUrn("soundcloud:tracks:$id") }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
        val label = if (seedTrack != null) "from-track" else "user"
        Logs.add("wave", "$label ids=${payload.tracks.size} → tracks=${tracks.size}" + if (hideListened == false) " (+listened)" else "")
        return tracks to payload.cursor
    }

    suspend fun lyrics(urn: String): LyricsResponse? = withContext(IoDispatcher) {
        for (id in listOf(urn, urn.substringAfterLast(':')).distinct()) {
            val req = HttpRequest("$API_BASE/lyrics/${enc(id)}", headers = sessionHeader(), profile = HttpProfile.SLOW, queued = true)
            val result = runCatching {
                val res = Http.execute(req)
                if (!res.isSuccessful) null else json.decodeFromString(LyricsResponse.serializer(), res.text())
            }.getOrNull()
            if (result != null) return@withContext result
        }
        null
    }

    suspend fun recommendations(hideListened: Boolean): kotlinx.serialization.json.JsonElement =
        getJson(
            "$API_BASE/recommendations?hide_listened=${if (hideListened) 1 else 0}",
            kotlinx.serialization.json.JsonElement.serializer(),
            fresh = true,
        )

    suspend fun waveFeedback(cursor: String, negatives: Int, positives: Int): String? = withContext(IoDispatcher) {
        val payload = buildJsonObject {
            put("cursor", cursor)
            put("negatives", negatives)
            put("positives", positives)
        }.toString().let { HttpBody.json(it) }
        val res = Http.execute(HttpRequest("$API_BASE/recommendations/wave/feedback", "POST", sessionHeader(), payload, HttpProfile.API))
        if (!res.isSuccessful) null else runCatching {
            json.parseToJsonElement(res.text()).jsonObject["cursor"]
                ?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    suspend fun waveFromArtist(artistUrn: String, limit: Int = 30): List<Track> = runCatching {
        val id = enc(artistUrn.substringAfterLast(':'))
        val payload = getJson("$API_BASE/recommendations/wave/from-artist/$id?limit=$limit", WavePayload.serializer(), fresh = true)
        val ids = payload.tracks.map { it.id.content.removePrefix("soundcloud:tracks:") }
        Logs.add("wave", "from-artist ids=${ids.size}")
        tracksByIds(ids) ?: emptyList()
    }.getOrDefault(emptyList())

    suspend fun waveFromTrack(urn: String, limit: Int = 20, budgetMs: Long = 15_000): List<Track> {
        val res = runCatching {
            kotlinx.coroutines.withTimeoutOrNull(budgetMs) { waveBatch(null, limit, urn, null).first }
        }
        res.exceptionOrNull()?.let {
            if (it is kotlinx.coroutines.CancellationException) throw it
            Logs.add("wave", "from-track failed: ${it::class.simpleName} ${it.message?.take(80) ?: ""}")
        }
        val list = res.getOrNull()
        if (res.isSuccess && list == null) Logs.add("wave", "from-track: no answer in ${budgetMs / 1000}s")
        return list ?: emptyList()
    }

    suspend fun waveTracks(cursor: String? = null, limit: Int = 20): Pair<List<Track>, String> {
        if (cursor == LOCAL_RADIO_CURSOR) return localBatch(limit) to LOCAL_RADIO_CURSOR
        if (!cursor.isNullOrEmpty()) {
            val next = waveBatch(cursor, limit, waveSeed, null)
            if (next.first.isNotEmpty()) return next
            Logs.add("wave", "server wave ran out → local radio")
            return localBatch(limit) to LOCAL_RADIO_CURSOR
        }

        waveSeed = null
        val fresh = if (Prefs.waveFresh) null else false
        val first = runCatching { waveBatch(null, limit, null, fresh) }
        first.exceptionOrNull()?.let {
            if (it is kotlinx.coroutines.CancellationException) throw it
            Logs.add("wave", "failed: ${it::class.simpleName}: ${it.message?.take(120)} → local radio")
            waveBackup?.reset()
            val local = localBatch(limit)
            if (local.isNotEmpty()) return local to LOCAL_RADIO_CURSOR
            throw it
        }
        first.getOrNull()?.takeIf { it.first.isNotEmpty() }?.let { return it }

        if (nowMillis() - SessionState.lastRefreshAt > 10 * 60_000L) {
            val code = refreshSession()
            if (code in 200..299) {
                SessionState.lastRefreshAt = nowMillis()
                Logs.add("wave", "empty → session refreshed, retrying")
                runCatching { waveBatch(null, limit, null, fresh) }.getOrNull()
                    ?.takeIf { it.first.isNotEmpty() }?.let { return it }
            }
        }

        val withListened = runCatching { waveBatch(null, limit, null, false) }
        withListened.getOrNull()?.takeIf { it.first.isNotEmpty() }?.let { return it }

        val seed = waveBackup?.likeSeed()
        if (seed != null) {
            val fromTrack = runCatching { waveBatch(null, limit, seed, false) }
            fromTrack.getOrNull()?.takeIf { it.first.isNotEmpty() }?.let {
                waveSeed = seed
                return it
            }
        }

        Logs.add("wave", "server wave empty → local radio")
        waveBackup?.reset()
        return localBatch(limit) to LOCAL_RADIO_CURSOR
    }

    private suspend fun localBatch(limit: Int): List<Track> = waveBackup?.batch(limit) ?: emptyList()

    suspend fun history(offset: Int = 0, limit: Int = 50, fresh: Boolean = false): HistoryPage =
        getJson("$API_BASE/history?limit=$limit&offset=$offset", HistoryPage.serializer(), fresh)

    suspend fun clearHistory() {
        mutate("DELETE", "/history", null)
    }

    suspend fun logout(sid: String): Unit = withContext(IoDispatcher) {
        runCatching {
            val res = Http.execute(
                HttpRequest("$CONTROL_BASE/auth/logout", "POST", listOf("x-session-id" to sid), HttpBody.EMPTY, HttpProfile.LOGOUT, forceNetwork = true),
            )
            Logs.add("auth", "logout → ${res.code}")
        }.onFailure { Logs.add("auth", "logout failed: ${it::class.simpleName}") }
    }

    suspend fun likedTracks(page: Int = 0, limit: Int = 50, fresh: Boolean = false): PagedTracks =
        getJson("$API_BASE/me/likes/tracks?limit=$limit&page=$page", PagedTracks.serializer(), fresh)

    private fun jsonBody(text: String) = HttpBody.json(text)

    private val emptyBody get() = HttpBody.EMPTY

    suspend fun likeTrack(track: Track) {
        mutate("POST", "/likes/tracks/${enc(track.urn)}", jsonBody(json.encodeToString(Track.serializer(), track)))
    }

    suspend fun unlikeTrack(urn: String) {
        mutate("DELETE", "/likes/tracks/${enc(urn)}", null)
    }

    suspend fun myPlaylists(page: Int = 0, limit: Int = 50, fresh: Boolean = false): PagedPlaylists =
        getJson("$API_BASE/me/playlists?limit=$limit&page=$page", PagedPlaylists.serializer(), fresh)

    suspend fun playlistTracks(urn: String, page: Int = 0, limit: Int = 50, fresh: Boolean = false): PagedTracks =
        getJson("$API_BASE/playlists/${enc(urn)}/tracks?limit=$limit&page=$page", PagedTracks.serializer(), fresh)

    suspend fun likedPlaylists(page: Int = 0, limit: Int = 50, fresh: Boolean = false): PagedPlaylists =
        getJson("$API_BASE/me/likes/playlists?limit=$limit&page=$page", PagedPlaylists.serializer(), fresh)

    suspend fun likePlaylist(urn: String) {
        mutate("POST", "/likes/playlists/${enc(urn)}", emptyBody)
    }

    suspend fun unlikePlaylist(urn: String) {
        mutate("DELETE", "/likes/playlists/${enc(urn)}", null)
    }

    suspend fun authStatus(): AuthStatus =
        getJson("$API_BASE/auth/session", AuthStatus.serializer(), fresh = true)

    suspend fun me(fresh: Boolean = false): MeProfile =
        getJson("$API_BASE/me", MeProfile.serializer(), fresh)

    suspend fun latestRelease(): ReleaseInfo? = withContext(IoDispatcher) {
        runCatching {
            val res = Http.execute(
                HttpRequest(
                    "https://api.github.com/repos/okeydw/SoundCloud-Android/releases/latest",
                    headers = listOf("Accept" to "application/vnd.github+json"),
                    forceNetwork = true,
                ),
            )
            run {
                if (!res.isSuccessful) return@run null
                val obj = json.parseToJsonElement(res.text()).jsonObject
                val tag = obj["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@run null
                val url = obj["html_url"]?.jsonPrimitive?.contentOrNull ?: "https://github.com/okeydw/SoundCloud-Android/releases"
                val assets = (obj["assets"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { el ->
                    val a = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    val name = a["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val link = a["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val size = a["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                    ApkAsset(name, link, size)
                }.filter { it.name.endsWith(".apk", ignoreCase = true) }
                ReleaseInfo(tag, url, assets)
            }
        }.getOrNull()
    }

    suspend fun linkClaim(claimToken: String, pull: Boolean): LinkClaim = withContext(IoDispatcher) {
        val sid = sessionId
        if (pull && sid == null) throw ApiHttpException(401, "not signed in")
        val payload = HttpBody.json(buildJsonObject { put("claimToken", claimToken) }.toString())
        val headers = if (pull && sid != null) listOf("x-session-id" to sid) else emptyList()
        Http.execute(HttpRequest("$CONTROL_BASE/auth/link/claim", "POST", headers, payload, forceNetwork = true)).let { res ->
            val body = res.text()
            if (!res.isSuccessful) throw ApiHttpException(res.code, body.take(200))
            val claim = json.decodeFromString(LinkClaim.serializer(), body)
            val expected = if (pull) "pull" else "push"
            if (claim.mode.isNotEmpty() && claim.mode != expected) {
                throw ApiHttpException(400, "link mode ${claim.mode}, expected $expected")
            }
            claim
        }
    }

    suspend fun linkCreate(push: Boolean): LinkCreate = withContext(IoDispatcher) {
        val sid = sessionId
        if (push && sid == null) throw ApiHttpException(401, "not signed in")
        val payload = HttpBody.json(buildJsonObject { put("mode", if (push) "push" else "pull") }.toString())
        val headers = if (push && sid != null) listOf("x-session-id" to sid) else emptyList()
        Http.execute(HttpRequest("$CONTROL_BASE/auth/link/create", "POST", headers, payload, forceNetwork = true)).let { res ->
            val body = res.text()
            if (!res.isSuccessful) throw ApiHttpException(res.code, body.take(200))
            json.decodeFromString(LinkCreate.serializer(), body)
        }
    }

    suspend fun linkStatus(id: String): LinkStatus = withContext(IoDispatcher) {
        Http.execute(HttpRequest("$CONTROL_BASE/auth/link/status?id=${enc(id)}", forceNetwork = true)).let { res ->
            val body = res.text()
            if (!res.isSuccessful) throw ApiHttpException(res.code, body.take(200))
            json.decodeFromString(LinkStatus.serializer(), body)
        }
    }

    suspend fun refreshSession(): Int = withContext(IoDispatcher) {
        val sid = sessionId ?: return@withContext 401
        runCatching {
            val res = Http.execute(
                HttpRequest("$CONTROL_BASE/auth/refresh", "POST", listOf("x-session-id" to sid), HttpBody.EMPTY, HttpProfile.REFRESH, forceNetwork = true),
            )
            if (!res.isSuccessful) {
                Logs.add("auth", "refresh HTTP ${res.code}: ${res.text().take(120)}")
            }
            res.code
        }.getOrElse {
            Logs.add("auth", "refresh failed: ${it::class.simpleName}")
            -1
        }
    }

    suspend fun sessionValid(): Boolean? = withContext(IoDispatcher) {
        val sid = sessionId ?: return@withContext false
        runCatching {
            Http.execute(
                HttpRequest("$CONTROL_BASE/auth/session", headers = listOf("x-session-id" to sid), profile = HttpProfile.API, forceNetwork = true),
            ).let { res ->
                when {
                    res.code == 401 || res.code == 403 -> false
                    !res.isSuccessful -> null
                    else -> runCatching {
                        json.decodeFromString(AuthStatus.serializer(), res.text()).authenticated
                    }.getOrNull()
                }
            }
        }.getOrNull()
    }

    suspend fun subscription(): Subscription =
        getJson("$API_BASE/me/subscription", Subscription.serializer())

    suspend fun healthOk(): Boolean = withContext(IoDispatcher) {
        runCatching {
            Http.execute(HttpRequest("$CONTROL_BASE/health", headers = listOf(PROBE_HEADER to "1"), profile = HttpProfile.PROBE, forceNetwork = true, discardBody = true)).isSuccessful
        }.getOrDefault(false)
    }

    suspend fun catalogArtist(id: String): CatalogArtist =
        getJson("$API_BASE/artists/${enc(id)}", CatalogArtist.serializer())

    suspend fun catalogArtistTracks(id: String, role: String, sort: String, page: Int = 0, limit: Int = 30): PagedTracks =
        getJson("$API_BASE/artists/${enc(id)}/tracks?role=$role&sort=$sort&limit=$limit&page=$page", PagedTracks.serializer())

    suspend fun catalogArtistAlbums(id: String): List<ArtistAlbum> =
        getJson("$API_BASE/artists/${enc(id)}/albums", kotlinx.serialization.builtins.ListSerializer(ArtistAlbum.serializer()))

    suspend fun catalogAlbum(id: String): CatalogAlbum =
        getJson("$API_BASE/albums/${enc(id)}", CatalogAlbum.serializer())

    suspend fun listeningStats(period: String, fresh: Boolean = false): ListeningStats {
        val offset = utcOffsetMinutes()
        return getJson("$API_BASE/history/stats?period=$period&utcOffset=$offset", ListeningStats.serializer(), fresh)
    }

    suspend fun userAura(urn: String): AuraInfo =
        getJson("$API_BASE/users/${enc(urn)}/aura", AuraInfo.serializer(), fresh = true)

    suspend fun setAura(id: String, hex: String?) {
        val body = buildJsonObject {
            put("aura_id", id)
            if (hex != null) put("custom_hex", hex)
        }
        mutate("PUT", "/me/aura", jsonBody(body.toString()))
    }

    suspend fun blockedArtists(): List<BlockedArtist> =
        getJson("$API_BASE/blocked-artists", BlockedList.serializer(), fresh = true).collection

    suspend fun blockArtist(entry: BlockedArtist) {
        val input = BlockInput(entry.name, entry.avatar_url, entry.sc_user_ids)
        mutate("PUT", "/blocked-artists/${entry.kind}/${enc(entry.id)}", jsonBody(json.encodeToString(BlockInput.serializer(), input)))
    }

    suspend fun unblockArtist(kind: String, id: String) {
        mutate("DELETE", "/blocked-artists/$kind/${enc(id)}", null)
    }

    suspend fun paySubscription(): PaySubscription = withContext(IoDispatcher) {
        Http.execute(
            HttpRequest("https://pay.scnative.space/api/me/subscription", headers = sessionHeader(), profile = HttpProfile.MUTATE, forceNetwork = true),
        ).let { res ->
            val body = res.text()
            if (!res.isSuccessful) throw ApiHttpException(res.code, body.take(200))
            json.decodeFromString(PaySubscription.serializer(), body)
        }
    }

    suspend fun webProfiles(urn: String): List<WebProfile> {
        val root = getJson("$API_BASE/users/${enc(urn)}/web-profiles", kotlinx.serialization.json.JsonElement.serializer())
        val arr = when (root) {
            is kotlinx.serialization.json.JsonArray -> root
            is kotlinx.serialization.json.JsonObject -> root["collection"] as? kotlinx.serialization.json.JsonArray
            else -> null
        } ?: return emptyList()
        return arr.mapNotNull { runCatching { json.decodeFromJsonElement(WebProfile.serializer(), it) }.getOrNull() }
    }

    suspend fun isFollowing(me: String, urn: String): Boolean =
        getJson("$API_BASE/users/${enc(me)}/followings/${enc(urn)}", kotlinx.serialization.serializer<Boolean>(), fresh = true)

    suspend fun follow(urn: String) {
        mutate("PUT", "/me/followings/${enc(urn)}", emptyBody)
    }

    suspend fun unfollow(urn: String) {
        mutate("DELETE", "/me/followings/${enc(urn)}", null)
    }

    suspend fun dislikedIds(): List<String> =
        getJson("$API_BASE/dislikes/ids", DislikeIds.serializer()).ids

    suspend fun dislike(track: Track) {
        mutate("POST", "/dislikes/${enc(track.urn)}", jsonBody(json.encodeToString(Track.serializer(), track)))
    }

    suspend fun undislike(urn: String) {
        mutate("DELETE", "/dislikes/${enc(urn)}", null)
    }

    suspend fun createPlaylist(title: String, sharing: String, trackUrns: List<String>): CreatedPlaylist {
        val payload = buildJsonObject {
            put("playlist", buildJsonObject {
                put("title", title)
                put("sharing", sharing)
                if (trackUrns.isNotEmpty()) {
                    put("tracks", kotlinx.serialization.json.buildJsonArray {
                        trackUrns.forEach { urn -> add(buildJsonObject { put("urn", urn) }) }
                    })
                }
            })
        }.toString()
        val body = mutate("POST", "/playlists", jsonBody(payload))
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val urn = obj?.get("urn")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
        val status = obj?.get("status")?.jsonPrimitive?.contentOrNull
        Logs.add("api", "playlist created: ${urn ?: status ?: "no urn in reply"}")
        return CreatedPlaylist(urn, status == "queued")
    }

    suspend fun setPlaylistSharing(urn: String, sharing: String) {
        val payload = buildJsonObject { put("sharing", sharing) }.toString()
        mutate("PUT", "/playlists/${enc(urn)}/sharing", jsonBody(payload))
    }

    suspend fun removeFromPlaylist(playlistUrn: String, trackUrn: String) {
        val payload = buildJsonObject { put("remove", trackUrn) }.toString()
        mutate("POST", "/playlists/${enc(playlistUrn)}/tracks", jsonBody(payload))
    }

    suspend fun deletePlaylist(urn: String) {
        mutate("DELETE", "/playlists/${enc(urn)}", null)
    }

    suspend fun reorderPlaylist(playlistUrn: String, order: List<String>) {
        val payload = buildJsonObject {
            put("order", kotlinx.serialization.json.JsonArray(order.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        }.toString()
        mutate("POST", "/playlists/${enc(playlistUrn)}/tracks", jsonBody(payload))
    }

    suspend fun addToPlaylist(playlistUrn: String, trackUrn: String) {
        val payload = buildJsonObject { put("add", trackUrn) }.toString()
        mutate("POST", "/playlists/${enc(playlistUrn)}/tracks", jsonBody(payload))
    }

    suspend fun sendEvent(userUrn: String, trackUrn: String, type: String, positionPct: Double?) {
        val payload = buildJsonObject {
            put("scUserId", userUrn)
            put("scTrackId", trackUrn)
            put("eventType", type)
            if (positionPct != null) put("positionPct", positionPct)
        }.toString()
        mutate("POST", "/events", jsonBody(payload))
    }

    suspend fun renamePlaylist(urn: String, title: String) {
        val payload = buildJsonObject {
            put("playlist", buildJsonObject { put("title", title) })
        }.toString()
        mutate("PUT", "/playlists/${enc(urn)}", jsonBody(payload))
    }

    suspend fun waveform(rawUrl: String, bars: Int = 96): List<Float> = withContext(IoDispatcher) {
        val url = rawUrl
            .replace(Regex("\\.png(\\?.*)?$"), ".json$1")
            .replaceFirst("http://", "https://")
        val proxied = runCatching {
            val (n, v) = imageProxyTarget(url)
            val res = Http.execute(HttpRequest("$IMAGES_BASE/?t=${enc(v)}", headers = listOf(n to v), profile = HttpProfile.PROBE))
            if (res.isSuccessful) res.text() else null
        }.getOrNull()
        val body = proxied ?: runCatching {
            val res = Http.execute(HttpRequest(url, profile = HttpProfile.PROBE))
            if (res.isSuccessful) res.text() else null
        }.getOrNull() ?: throw IOException("waveform fetch failed")

        val wf = json.decodeFromString(WaveformJson.serializer(), body)
        val samples = wf.samples
        if (samples.isEmpty()) throw IOException("empty waveform")
        val max = samples.max().coerceAtLeast(1)
        (0 until bars).map { i ->
            val start = i * samples.size / bars
            val end = (((i + 1) * samples.size) / bars).coerceAtLeast(start + 1).coerceAtMost(samples.size)
            val avg = samples.subList(start, end).average().toFloat()
            (avg / max).coerceIn(0.08f, 1f)
        }
    }

    suspend fun postHistory(
        urn: String,
        title: String,
        artistName: String,
        artistUrn: String?,
        artworkUrl: String?,
        duration: Long,
    ): Unit = withContext(IoDispatcher) {
        val body = buildJsonObject {
            put("scTrackId", urn)
            put("title", title)
            put("artistName", artistName)
            put("artistUrn", artistUrn)
            put("artworkUrl", artworkUrl)
            put("duration", duration)
        }.toString().let { HttpBody.json(it) }
        Http.execute(HttpRequest("$API_BASE/history", "POST", sessionHeader(), body, discardBody = true))
        Unit
    }

    fun streamUrl(urn: String, hq: Boolean = false, goPlus: Boolean = false): String {
        val base = if (goPlus && Prefs.star) Endpoints.STREAM_STAR else STREAM_BASE
        val qs = if (hq) "?hq=true" else ""
        return "$base/stream/${enc(urn)}$qs"
    }

    fun streamAuth(url: String): Pair<String, String>? {
        val sid = sessionId ?: return null
        if (!Endpoints.trustedUrl(url)) return null
        val path = "/" + url.substringAfter("://").substringAfter('/', "")
        if (path.startsWith("/stream/") && !url.contains("ticket=")) return ticketedStream(url, path) to sid
        if (!path.startsWith("/download/") && !path.startsWith("/tracks/")) return null
        if (url.contains("session_id=")) return url to sid
        val sep = if (url.contains('?')) '&' else '?'
        return "$url${sep}session_id=${enc(sid)}" to sid
    }

    private fun ticketedStream(url: String, path: String): String {
        val host = url.substringAfter("://").substringBefore('/')
        val apiHost = if (host.startsWith("stream")) "api" + host.removePrefix("stream") else host
        val urn = path.removePrefix("/stream/").substringBefore('?')
        val query = path.substringAfter('?', "").split('&').filter { it.isNotEmpty() && !it.startsWith("session_id=") }
        return "https://$apiHost/tracks/$urn/stream" + if (query.isEmpty()) "" else "?" + query.joinToString("&")
    }

    fun authorizedHeaders(url: String): Pair<String, List<Pair<String, String>>> {
        val auth = streamAuth(url) ?: return url to emptyList()
        return auth.first to listOf("x-session-id" to auth.second)
    }

    fun parseDownload(body: String): DownloadResponse? =
        runCatching { json.decodeFromString(DownloadResponse.serializer(), body) }.getOrNull()

    suspend fun streamProbe(urn: String): String = withContext(IoDispatcher) {
        runCatching {
            val (authUrl, headers) = authorizedHeaders(streamUrl(urn))
            val res = Http.execute(
                HttpRequest(authUrl, headers = headers + ("Range" to "bytes=0-15"), profile = HttpProfile.PROBE, forceNetwork = true),
            )
            val ct = res.header("content-type") ?: "?"
            val cl = res.header("content-length") ?: "?"
            val bytes = res.body.take(16).toByteArray()
            val hex = bytes.toHex()
            val ascii = bytes.map { b -> if (b in 32..126) b.toInt().toChar() else '.' }.joinToString("")
            "HTTP ${res.code} @${res.host} ct=$ct len=$cl\n$hex\n$ascii"
        }.getOrElse { "probe fail: ${it::class.simpleName}: ${it.message}" }
    }

    fun artworkUrl(raw: String?, size: String = "t500x500"): String? =
        raw?.replace("-large", "-$size")

    @OptIn(ExperimentalEncodingApi::class)
    fun imageProxyTarget(artworkUrl: String): Pair<String, String> =
        "X-Target" to Base64.Default.encode(artworkUrl.encodeToByteArray())

    private fun pathOf(url: String): String {
        val afterScheme = url.removePrefix("https://").removePrefix("http://")
        val slash = afterScheme.indexOf('/')
        return if (slash >= 0) afterScheme.substring(slash) else "/"
    }

    private suspend fun <T> getJson(url: String, strategy: DeserializationStrategy<T>, fresh: Boolean = false): T =
        withContext(IoDispatcher) {
            val path = pathOf(url)
            val hosts = Endpoints.hostsFor(path)
            val firstMain = hosts.firstOrNull { it != Endpoints.API_STAR }
            var lastError: Exception? = null
            for (base in hosts) {
                val isStar = base == Endpoints.API_STAR
                val full = base + path
                try {
                    val req = HttpRequest(full, headers = sessionHeader(), profile = HttpProfile.API, forceNetwork = fresh, queued = true)
                    return@withContext Http.execute(req).let { res ->
                        val body = res.text()
                        if (!res.isSuccessful) {
                            if (isStar && (res.code == 403 || res.code >= 500)) {
                                throw IOException("star ${res.code}")
                            }
                            if (res.code == 401) SessionState.markExpired()
                            if (res.code in intArrayOf(400, 401, 403, 404)) {
                                throw ApiHttpException(res.code, body.take(300))
                            }
                            throw IOException("API host ${res.code}")
                        }
                        if (!isStar && base != firstMain) {
                            Endpoints.commitApi(Endpoints.apiHosts.indexOf(base))
                        }
                        json.decodeFromString(strategy, body)
                    }
                } catch (e: ApiHttpException) {
                    throw e
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ensureActive()
                    lastError = e
                }
            }
            throw lastError ?: IOException("all API hosts unavailable")
        }

    private fun enc(s: String) = formEncode(s)
}
