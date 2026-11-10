package com.scd.android

import kotlinx.coroutines.delay
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.concurrent.Volatile


sealed interface YtRef {
    data class Video(val id: String) : YtRef
    data class Playlist(val id: String) : YtRef
}

data class YtVideo(
    val id: String,
    val title: String,
    val author: String,
    val durationMs: Long,
    val thumbnail: String?,
)

data class YtAudio(
    val url: String,
    val mime: String,
    val bitrate: Int,
    val contentLength: Long,
    val userAgent: String,
    val client: String,
)

data class YtAccount(val name: String, val avatar: String?)

data class YtPlaylist(val id: String, val title: String, val items: List<YtVideo>)

class YtException(message: String) : IOException(message)

class YouTubeClient(
    private val cookies: () -> String = { "" },
    private val log: (String) -> Unit = {},
) {
    private class Client(
        val key: String,
        val name: String,
        val id: Int,
        val version: String,
        val userAgent: String,
        val extra: Map<String, Any> = emptyMap(),
    )

    private val clients = listOf(
        Client(
            "visionos", "VISIONOS", 101, "1.02",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
            mapOf("deviceMake" to "Apple", "deviceModel" to "RealityDevice17,1", "osName" to "visionOS", "osVersion" to "26.5.23O471"),
        ),
        Client(
            "android_vr", "ANDROID_VR", 28, "1.65.10",
            "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            mapOf("deviceMake" to "Oculus", "deviceModel" to "Quest 3", "androidSdkVersion" to 32, "osName" to "Android", "osVersion" to "12L"),
        ),
        Client(
            "ios", "IOS", 5, "21.26.4",
            "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            mapOf("deviceMake" to "Apple", "deviceModel" to "iPhone16,2", "osName" to "iPhone", "osVersion" to "18.3.2.22D82"),
        ),
        Client(
            "android", "ANDROID", 3, "21.26.364",
            "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",
            mapOf("androidSdkVersion" to 30, "osName" to "Android", "osVersion" to "11"),
        ),
    )

    private val web = Client(
        "web", "WEB", 1, "2.20260708.00.00",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36",
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var visitorData: String? = null

    companion object {
        private val ID = Regex("^[A-Za-z0-9_-]{11}$")
        private val LIST = Regex("^[A-Za-z0-9_-]{10,}$")

        fun parse(input: String): YtRef? {
            val raw = input.trim()
            if (ID.matches(raw)) return YtRef.Video(raw)
            val uri = WebUrl.parse(raw) ?: return null
            val host = uri.host.removePrefix("www.").removePrefix("m.")
            if (host != "youtube.com" && host != "music.youtube.com" && host != "youtu.be" && host != "youtube-nocookie.com") return null
            val query = uri.query()
            val path = uri.path.trim('/')
            val signedIn = Prefs.ytCookies.isNotBlank()
            val list = query["list"]?.takeIf { !it.startsWith("RD") && if (it == "WL" || it == "LL") signedIn else LIST.matches(it) }
            val video = when {
                host == "youtu.be" -> path.substringBefore('/')
                path.startsWith("shorts/") || path.startsWith("embed/") || path.startsWith("live/") -> path.substringAfter('/').substringBefore('/')
                else -> query["v"]
            }?.takeIf { ID.matches(it) }
            return when {
                path == "playlist" && list != null -> YtRef.Playlist(list)
                path.startsWith("browse/") && path.removePrefix("browse/").startsWith("VL") -> YtRef.Playlist(path.removePrefix("browse/VL"))
                list != null && video == null -> YtRef.Playlist(list)
                list != null && list.startsWith("OLAK") -> YtRef.Playlist(list)
                video != null -> YtRef.Video(video)
                list != null -> YtRef.Playlist(list)
                else -> null
            }
        }

        fun cleanAuthor(author: String): String = author.removeSuffix(" - Topic").removeSuffix(" – Topic").trim()

        fun sapisidHash(sapisid: String, seconds: Long): String {
            val md = Sha1()
            md.update("$seconds $sapisid https://www.youtube.com".encodeToByteArray())
            return "SAPISIDHASH ${seconds}_${hexOf(md.digest())}"
        }
    }

    private fun context(c: Client) = buildJsonObject {
        putJsonObject("client") {
            put("clientName", c.name)
            put("clientVersion", c.version)
            put("hl", "en")
            put("gl", "US")
            if (c != web) put("userAgent", c.userAgent)
            for ((k, v) in c.extra) {
                when (v) {
                    is Int -> put(k, v)
                    else -> put(k, v.toString())
                }
            }
        }
    }

    private fun signInHeaders(): List<Pair<String, String>> {
        val cookie = cookies().takeIf { it.isNotBlank() } ?: return emptyList()
        val out = mutableListOf("Cookie" to cookie, "X-Origin" to "https://www.youtube.com", "X-Goog-AuthUser" to "0")
        val sapisid = cookie.split(';').map { it.trim() }.firstNotNullOfOrNull { part ->
            listOf("SAPISID=", "__Secure-3PAPISID=").firstOrNull { part.startsWith(it) }?.let { part.removePrefix(it) }
        }
        if (sapisid != null) out += "Authorization" to sapisidHash(sapisid, nowMillis() / 1000)
        return out
    }

    private suspend fun call(c: Client, endpoint: String, body: JsonObject): JsonObject {
        val payload = buildJsonObject {
            put("context", context(c))
            for ((k, v) in body) put(k, v)
        }.toString()
        val headers = buildList {
            add("User-Agent" to c.userAgent)
            add("X-YouTube-Client-Name" to c.id.toString())
            add("X-YouTube-Client-Version" to c.version)
            add("Origin" to "https://www.youtube.com")
            visitorData?.let { add("X-Goog-Visitor-Id" to it) }
            if (c == web) addAll(signInHeaders())
        }
        val res = Http.execute(
            HttpRequest(
                "https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false",
                method = "POST",
                headers = headers,
                body = HttpBody.json(payload),
                profile = HttpProfile.SLOW,
                forceNetwork = true,
            ),
        )
        if (!res.isSuccessful) throw YtException("${c.key} $endpoint HTTP ${res.code}")
        val obj = runCatching { json.parseToJsonElement(res.text()) as? JsonObject }.getOrNull()
            ?: throw YtException("${c.key} $endpoint: bad json")
        if (visitorData == null) str(obj, "responseContext", "visitorData")?.let { visitorData = it }
        return obj
    }

    private suspend fun ensureVisitor() {
        if (visitorData != null) return
        runCatching { call(web, "visitor_id", buildJsonObject {}) }.onFailure { log("visitor: ${it.message}") }
    }

    private fun el(e: JsonElement?, vararg path: Any): JsonElement? {
        var cur = e
        for (p in path) {
            cur = when (p) {
                is String -> (cur as? JsonObject)?.get(p)
                is Int -> (cur as? JsonArray)?.getOrNull(p)
                else -> null
            } ?: return null
        }
        return cur
    }

    private fun str(e: JsonElement?, vararg path: Any): String? = (el(e, *path) as? JsonPrimitive)?.contentOrNull

    private fun text(e: JsonElement?): String? {
        if (e == null) return null
        str(e, "simpleText")?.let { return it }
        str(e, "content")?.let { return it }
        val runs = el(e, "runs") as? JsonArray ?: return null
        return runs.mapNotNull { str(it, "text") }.joinToString("").takeIf { it.isNotEmpty() }
    }

    private fun bestThumb(e: JsonElement?): String? {
        val arr = el(e, "thumbnails") as? JsonArray ?: return null
        return arr.lastOrNull()?.let { str(it, "url") }?.substringBefore('?')
    }

    private fun find(e: JsonElement?, key: String, depth: Int = 0): JsonElement? {
        if (e == null || depth > 24) return null
        return when (e) {
            is JsonObject -> e[key] ?: e.values.firstNotNullOfOrNull { find(it, key, depth + 1) }
            is JsonArray -> e.firstNotNullOfOrNull { find(it, key, depth + 1) }
            else -> null
        }
    }

    suspend fun account(): YtAccount? {
        if (cookies().isBlank()) return null
        val res = call(web, "account/account_menu", buildJsonObject {})
        val header = find(res, "activeAccountHeaderRenderer") ?: return null
        val name = text(el(header, "accountName")) ?: text(el(header, "channelHandle")) ?: return null
        return YtAccount(name, bestThumb(el(header, "accountPhoto")))
    }

    private fun thumbFor(id: String) = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

    suspend fun video(id: String): Pair<YtVideo, List<YtAudio>> {
        var lastReason = "no client answered"
        for (attempt in 0..1) {
            if (attempt == 1) ensureVisitor()
            for (c in clients) {
                val res = runCatching {
                    call(
                        c, "player",
                        buildJsonObject {
                            put("videoId", id)
                            put("contentCheckOk", true)
                            put("racyCheckOk", true)
                            putJsonObject("playbackContext") {
                                putJsonObject("contentPlaybackContext") { put("html5Preference", "HTML5_PREF_WANTS") }
                            }
                        },
                    )
                }.getOrElse {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    lastReason = it.message ?: "error"
                    log("player ${c.key}: $lastReason")
                    null
                } ?: continue
                val status = str(res, "playabilityStatus", "status")
                if (status != "OK") {
                    lastReason = "${c.key}: $status ${str(res, "playabilityStatus", "reason") ?: ""}".trim()
                    log("player $lastReason")
                    continue
                }
                val details = el(res, "videoDetails")
                val formats = (el(res, "streamingData", "adaptiveFormats") as? JsonArray).orEmpty() +
                    (el(res, "streamingData", "formats") as? JsonArray).orEmpty()
                val audio = formats.mapNotNull { f ->
                    val mime = str(f, "mimeType") ?: return@mapNotNull null
                    if (!mime.startsWith("audio/mp4")) return@mapNotNull null
                    val url = str(f, "url") ?: return@mapNotNull null
                    if (str(f, "audioTrack", "audioIsDefault") == "false") return@mapNotNull null
                    YtAudio(
                        url = url,
                        mime = mime,
                        bitrate = str(f, "bitrate")?.toIntOrNull() ?: 0,
                        contentLength = str(f, "contentLength")?.toLongOrNull() ?: 0L,
                        userAgent = c.userAgent,
                        client = c.key,
                    )
                }
                if (audio.isEmpty()) {
                    lastReason = "${c.key}: no direct m4a audio"
                    log("player $lastReason")
                    continue
                }
                val v = YtVideo(
                    id = id,
                    title = str(details, "title").orEmpty(),
                    author = cleanAuthor(str(details, "author").orEmpty()),
                    durationMs = (str(details, "lengthSeconds")?.toLongOrNull() ?: 0L) * 1000L,
                    thumbnail = bestThumb(el(details, "thumbnail")) ?: thumbFor(id),
                )
                return v to audio.sortedByDescending { it.bitrate }
            }
        }
        throw YtException(lastReason)
    }

    private fun collectVideos(root: JsonElement, out: LinkedHashMap<String, YtVideo>, tokens: MutableList<String>) {
        when (root) {
            is JsonObject -> {
                (root["playlistVideoRenderer"] as? JsonObject)?.let { r ->
                    val id = str(r, "videoId")
                    if (id != null && str(r, "isPlayable") != "false") {
                        out.getOrPut(id) {
                            YtVideo(
                                id = id,
                                title = text(r["title"]).orEmpty(),
                                author = cleanAuthor(text(r["shortBylineText"]).orEmpty()),
                                durationMs = (str(r, "lengthSeconds")?.toLongOrNull() ?: 0L) * 1000L,
                                thumbnail = bestThumb(r["thumbnail"]) ?: thumbFor(id),
                            )
                        }
                    }
                }
                (root["playlistPanelVideoRenderer"] as? JsonObject)?.let { r ->
                    val id = str(r, "videoId")
                    if (id != null) {
                        out.getOrPut(id) {
                            val len = text(r["lengthText"]).orEmpty().split(':').mapNotNull { it.toLongOrNull() }
                                .fold(0L) { acc, n -> acc * 60 + n }
                            YtVideo(
                                id = id,
                                title = text(r["title"]).orEmpty(),
                                author = cleanAuthor(text(r["longBylineText"]) ?: text(r["shortBylineText"]).orEmpty()),
                                durationMs = len * 1000L,
                                thumbnail = bestThumb(r["thumbnail"]) ?: thumbFor(id),
                            )
                        }
                    }
                }
                str(root, "continuationCommand", "token")?.let { tokens += it }
                for ((k, v) in root) {
                    if (k == "playlistVideoRenderer" || k == "playlistPanelVideoRenderer") continue
                    collectVideos(v, out, tokens)
                }
            }
            is JsonArray -> root.forEach { collectVideos(it, out, tokens) }
            else -> Unit
        }
    }

    suspend fun playlist(id: String, limit: Int = 1000, onProgress: (Int) -> Unit = {}): YtPlaylist {
        val out = LinkedHashMap<String, YtVideo>()
        var title: String? = null
        val first = runCatching { call(web, "browse", buildJsonObject { put("browseId", "VL$id") }) }
            .onFailure { log("browse: ${it.message}") }
            .getOrNull()
        if (first != null) {
            title = str(first, "metadata", "playlistMetadataRenderer", "title")
                ?: text(el(first, "header", "playlistHeaderRenderer", "title"))
                ?: str(first, "header", "pageHeaderRenderer", "pageTitle")
            var tokens = mutableListOf<String>()
            collectVideos(first, out, tokens)
            onProgress(out.size)
            var guard = 0
            while (tokens.isNotEmpty() && out.size < limit && guard < 60) {
                guard++
                val token = tokens.last()
                tokens = mutableListOf()
                val next = runCatching { call(web, "browse", buildJsonObject { put("continuation", token) }) }
                    .onFailure { log("browse more: ${it.message}") }
                    .getOrNull() ?: break
                val before = out.size
                collectVideos(next, out, tokens)
                onProgress(out.size)
                if (out.size == before) break
            }
        }
        if (out.isEmpty()) {
            val next = runCatching { call(web, "next", buildJsonObject { put("playlistId", id) }) }
                .onFailure { log("next: ${it.message}") }
                .getOrNull()
            if (next != null) {
                collectVideos(next, out, mutableListOf())
                title = title ?: str(next, "contents", "twoColumnWatchNextResults", "playlist", "playlist", "title")
            }
        }
        if (out.isEmpty()) throw YtException("playlist $id: empty or private")
        return YtPlaylist(id, title ?: "YouTube", out.values.take(limit))
    }

    suspend fun download(audio: YtAudio, onProgress: (Long, Long) -> Unit = { _, _ -> }): ByteArray {
        val total = audio.contentLength
        if (total <= 0L) {
            val res = Http.execute(HttpRequest(audio.url, headers = listOf("User-Agent" to audio.userAgent), profile = HttpProfile.SLOW, forceNetwork = true))
            if (!res.isSuccessful) throw YtException("media HTTP ${res.code}")
            if (res.body.isEmpty()) throw YtException("media: empty body")
            return res.body
        }
        if (total > 200L * 1024 * 1024) throw YtException("media too large")
        val chunk = 2L * 1024 * 1024
        val out = ByteArray(total.toInt())
        var done = 0L
        val sep = if (audio.url.contains('?')) '&' else '?'
        while (done < total) {
            val end = minOf(done + chunk, total) - 1
            var attempt = 0
            while (true) {
                val got = runCatching {
                    val res = Http.execute(
                        HttpRequest("${audio.url}${sep}range=$done-$end", headers = listOf("User-Agent" to audio.userAgent), profile = HttpProfile.SLOW, forceNetwork = true),
                    )
                    if (!res.isSuccessful) throw YtException("media HTTP ${res.code}")
                    if (res.body.isEmpty()) throw YtException("media: empty chunk")
                    res.body
                }
                val bytes = got.getOrNull()
                if (bytes != null) {
                    val n = minOf(bytes.size.toLong(), total - done).toInt()
                    bytes.copyInto(out, done.toInt(), 0, n)
                    done += n
                    onProgress(done, total)
                    break
                }
                val err = got.exceptionOrNull()
                if (err is kotlinx.coroutines.CancellationException) throw err
                attempt++
                if (attempt >= 3 || (err is YtException && err.message?.contains("HTTP 403") == true)) throw err ?: YtException("media failed")
                delay(800L * attempt)
            }
        }
        return out
    }
}

object YouTubeImport {
    const val PREFIX = YouTubeUrn.PREFIX
    const val SOURCE = YouTubeUrn.SOURCE
    const val SINGLES = YouTubeUrn.SINGLES
    const val LIKES_URL = "https://www.youtube.com/playlist?list=LL"

    fun isYouTube(urn: String?) = YouTubeUrn.matches(urn)

    private val noise = Regex("(?i)\\s*[(\\[【][^)\\]】]*\\b(official|video|audio|lyrics?|visuali[sz]er|mv|m/v|hd|hq|4k|clip|клип|премьера|текст|lyric video)\\b[^)\\]】]*[)\\]】]")
    private val trailer = Regex("\\s*[|｜].*$")
    private val channel = Regex("(?i)(\\s*-\\s*topic|\\s*vevo|\\s*official)$")
    private val dash = Regex("\\s+[-–—]\\s+")

    fun asExt(v: YtVideo): ExtTrack {
        val author = v.author.replace(channel, "").trim()
        val cleaned = v.title.replace(noise, "").replace(trailer, "").trim()
        val split = dash.find(cleaned)
        val artist = split?.let { cleaned.substring(0, it.range.first).trim() }?.takeIf { it.isNotEmpty() } ?: author
        val title = (split?.let { cleaned.substring(it.range.last + 1) } ?: cleaned).trim().trim('"', '\'', '«', '»', ' ')
        return ExtTrack(title = title.ifBlank { v.title }, artist = artist, durationMs = v.durationMs)
    }

    val client by lazy { YouTubeClient(cookies = { Prefs.ytCookies }) { Logs.add("youtube", it.take(160)) } }

    fun track(v: YtVideo) = Track(
        urn = PREFIX + v.id,
        title = v.title,
        duration = v.durationMs,
        artwork_url = v.thumbnail,
        permalink_url = "https://youtu.be/${v.id}",
        user = TrackUser(username = v.author),
    )

    suspend fun fetch(track: Track): ByteArray? {
        val id = track.urn.removePrefix(PREFIX)
        val (_, audio) = client.video(id)
        var last: Throwable? = null
        for (a in audio.take(3)) {
            val bytes = runCatching { client.download(a) }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                last = it
            }.getOrNull() ?: continue
            if (!StreamFiles.looksLikeAudio(bytes)) continue
            Logs.add("youtube", "${track.title.take(24)}: ${a.client} ${a.mime.substringBefore(';')} ${a.bitrate / 1000}k")
            return bytes
        }
        last?.let { throw it }
        return null
    }
}
