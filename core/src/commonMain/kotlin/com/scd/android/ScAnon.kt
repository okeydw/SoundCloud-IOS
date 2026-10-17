package com.scd.android

import kotlin.concurrent.Volatile
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.io.IOException

object ScAnon {
    private const val SC_HOME = "https://soundcloud.com"
    private const val SC_API_V2 = "https://api-v2.soundcloud.com"
    const val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    private val PRESETS = listOf("mp3_1_0", "aac_160k", "mp3_0_0", "aac_1_0")
    private const val FAIL_THRESHOLD = 3
    private const val COOLDOWN_MS = 5 * 60_000L
    private const val CLIENT_ID_MIN_REFRESH_MS = 30_000L

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Serializable
    private data class Format(val protocol: String? = null, val mime_type: String? = null)

    @Serializable
    private data class Transcoding(
        val url: String = "",
        val preset: String? = null,
        val snipped: Boolean = false,
        val format: Format? = null,
    )

    @Serializable
    private data class Media(val transcodings: List<Transcoding> = emptyList())

    @Serializable
    private data class AnonTrack(
        val policy: String? = null,
        val track_authorization: String? = null,
        val media: Media? = null,
    )

    @Serializable
    private data class Resolved(val url: String = "")

    @Volatile
    private var clientId: String? = null

    @Volatile
    private var clientIdAt = 0L

    @Volatile
    private var failures = 0

    @Volatile
    private var cooldownUntil = 0L

    private val lock = Any()

    fun available(): Boolean = Prefs.anonFallback && nowMillis() >= cooldownUntil

    private fun get(url: String): String {
        val (code, body) = Http.engine.fetchBlocking(
            HttpRequest(url, headers = listOf("User-Agent" to USER_AGENT), profile = HttpProfile.ANON),
        )
        if (code !in 200..299) throw HttpError(code)
        return body.decodeToString()
    }

    private class HttpError(val code: Int) : IOException("HTTP $code")

    private const val CLIENT_ID_TTL_MS = 12 * 60 * 60_000L
    private var prefs: KvStore? = null

    fun init(sp: KvStore) {
        prefs = sp
        val saved = sp.getString("client_id", null)
        val at = sp.getLong("client_id_at", 0L)
        if (!saved.isNullOrEmpty() && nowMillis() - at < CLIENT_ID_TTL_MS) {
            clientId = saved
            clientIdAt = at
        }
    }

    private val warming = SyncFlag(false)

    @Volatile
    private var warmFailedAt = 0L

    fun warmUp() {
        if (!Prefs.anonFallback || clientId != null) return
        if (nowMillis() - warmFailedAt < 3 * 60_000L) return
        if (!warming.compareAndSet(false, true)) return
        try {
            val started = nowMillis()
            runCatching { locked(lock) { clientId ?: fetchClientId() } }
                .onSuccess { Logs.add("anon", "client_id ready in ${(nowMillis() - started) / 1000}s") }
                .onFailure {
                    warmFailedAt = nowMillis()
                    Logs.add("anon", "warm-up failed after ${(nowMillis() - started) / 1000}s: ${it::class.simpleName} ${it.message?.take(60) ?: ""}")
                }
        } finally {
            warming.set(false)
        }
    }

    private fun fetchClientId(): String {
        val (code, bytes) = Http.engine.fetchBlocking(
            HttpRequest(
                SC_HOME,
                headers = listOf("User-Agent" to USER_AGENT, "Accept" to "text/html", "Accept-Language" to "en-US,en;q=0.9"),
                profile = HttpProfile.ANON_HOME,
            ),
        )
        if (code !in 200..299) throw HttpError(code)
        val html = bytes.decodeToString()
        val re = Regex("\"hydratable\"\\s*:\\s*\"apiClient\"\\s*,\\s*\"data\"\\s*:\\s*\\{\\s*\"id\"\\s*:\\s*\"([^\"]+)\"")
        val id = re.find(html)?.groupValues?.get(1) ?: throw IOException("no client_id on soundcloud.com")
        clientId = id
        clientIdAt = nowMillis()
        prefs?.edit()?.putString("client_id", id)?.putLong("client_id_at", clientIdAt)?.apply()
        return id
    }

    private fun currentClientId(): String = clientId ?: locked(lock) { clientId ?: fetchClientId() }

    private fun refreshClientId(): String = locked(lock) {
        if (nowMillis() - clientIdAt < CLIENT_ID_MIN_REFRESH_MS) {
            clientId ?: fetchClientId()
        } else {
            fetchClientId()
        }
    }

    private fun trackById(id: String): AnonTrack {
        val first = runCatching { get("$SC_API_V2/tracks/$id?client_id=${currentClientId()}") }
        val body = first.getOrElse {
            if (it is HttpError && it.code == 404) throw it
            get("$SC_API_V2/tracks/$id?client_id=${refreshClientId()}")
        }
        return json.decodeFromString(AnonTrack.serializer(), body)
    }

    private fun isProgressive(t: Transcoding) = t.format?.protocol == "progressive"

    private fun ranked(list: List<Transcoding>): List<Transcoding> {
        val usable = list.filter {
            val protocol = it.format?.protocol.orEmpty()
            (protocol == "progressive" || protocol == "hls") && !it.snipped && !it.url.contains("/preview") && it.url.isNotEmpty()
        }
        fun ordered(group: List<Transcoding>): List<Transcoding> {
            val byPreset = PRESETS.mapNotNull { p -> group.firstOrNull { it.preset == p } }
            return byPreset + group.filter { it !in byPreset }
        }
        return ordered(usable.filter(::isProgressive)) + ordered(usable.filterNot(::isProgressive))
    }

    private fun resolveTranscoding(t: Transcoding, auth: String?): String {
        fun target(cid: String): String {
            val sep = if (t.url.contains('?')) '&' else '?'
            val a = auth?.takeIf { it.isNotEmpty() }?.let { "&track_authorization=$it" } ?: ""
            return "${t.url}${sep}client_id=$cid$a"
        }
        val body = runCatching { get(target(currentClientId())) }.getOrElse {
            if (it is HttpError && it.code == 404) throw it
            get(target(refreshClientId()))
        }
        return json.decodeFromString(Resolved.serializer(), body).url
    }

    private fun parseTracks(body: String): List<Track> {
        val root = json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject ?: return emptyList()
        val items = root["collection"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return items.mapNotNull { el ->
            val obj = (el as? kotlinx.serialization.json.JsonObject) ?: return@mapNotNull null
            val track = (obj["track"] as? kotlinx.serialization.json.JsonObject) ?: obj
            val urn = (track["urn"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: (track["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { "soundcloud:tracks:$it" }
                ?: return@mapNotNull null
            val fixed = kotlinx.serialization.json.JsonObject(
                track + ("urn" to kotlinx.serialization.json.JsonPrimitive(urn)),
            )
            runCatching { json.decodeFromJsonElement(Track.serializer(), fixed) }.getOrNull()
        }
    }

    fun related(urn: String, limit: Int = 20, offset: Int = 0): List<Track> {
        if (!available() || !urn.startsWith("soundcloud:tracks:")) return emptyList()
        if (clientId == null) {
            AppScope.scope.launch { warmUp() }
            return emptyList()
        }
        val id = urn.substringAfterLast(':')
        return try {
            val url = "$SC_API_V2/tracks/$id/related?limit=$limit&offset=$offset"
            val body = runCatching { get("$url&client_id=${currentClientId()}") }.getOrElse {
                if (it is HttpError && it.code == 404) throw it
                get("$url&client_id=${refreshClientId()}")
            }
            val list = parseTracks(body).filter { it.urn.startsWith("soundcloud:tracks:") }
            failures = 0
            Logs.add("anon", "related $id → ${list.size}")
            list
        } catch (e: HttpError) {
            Logs.add("anon", "related $id HTTP ${e.code}")
            emptyList()
        } catch (e: Exception) {
            if (isNetworkFailure(e)) {
                val n = ++failures
                if (n >= FAIL_THRESHOLD) {
                    cooldownUntil = nowMillis() + COOLDOWN_MS
                    failures = 0
                }
            }
            Logs.add("anon", "related $id fail ${e::class.simpleName}")
            emptyList()
        }
    }

    fun sourceUrl(urn: String): String? {
        if (!available()) return null
        if (!urn.startsWith("soundcloud:tracks:")) return null
        if (clientId == null) {
            AppScope.scope.launch { warmUp() }
            return null
        }
        val id = urn.substringAfterLast(':')
        return try {
            val track = trackById(id)
            if (track.policy == "SNIP" || track.policy == "BLOCK") {
                Logs.add("anon", "$id policy ${track.policy}, skip")
                failures = 0
                return null
            }
            val options = ranked(track.media?.transcodings.orEmpty())
            if (options.isEmpty()) {
                Logs.add("anon", "$id no usable stream")
                failures = 0
                return null
            }
            for (t in options) {
                val url = runCatching { resolveTranscoding(t, track.track_authorization) }.getOrNull()
                if (!url.isNullOrEmpty()) {
                    failures = 0
                    val hls = !isProgressive(t)
                    Logs.add("anon", "$id → ${t.preset ?: "?"}${if (hls) " (hls)" else ""}")
                    return if (hls) HlsSource.wrap(url) else url
                }
            }
            failures = 0
            null
        } catch (e: HttpError) {
            failures = 0
            Logs.add("anon", "$id HTTP ${e.code}")
            null
        } catch (e: Exception) {
            if (!isNetworkFailure(e)) {
                Logs.add("anon", "$id skipped: ${e::class.simpleName}")
                return null
            }
            val n = ++failures
            Logs.add("anon", "$id fail ${e::class.simpleName}")
            if (n >= FAIL_THRESHOLD) {
                cooldownUntil = nowMillis() + COOLDOWN_MS
                failures = 0
                Logs.add("anon", "soundcloud.com unreachable, pause 5 min")
            }
            null
        }
    }
}
