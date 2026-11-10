package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ExtAccount(val name: String, val avatar: String? = null)

object ExternalAccounts {
    const val SPOTIFY = "sp"
    const val YOUTUBE = "yt"
    const val YANDEX = "ym"

    private var sp: KvStore? = null
    private val json = Json { ignoreUnknownKeys = true }

    var spotify by mutableStateOf<ExtAccount?>(null)
        private set
    var youtube by mutableStateOf<ExtAccount?>(null)
        private set
    var yandex by mutableStateOf<ExtAccount?>(null)
        private set
    var spotifyRejectedClient by mutableStateOf("")
        private set

    private val clientIdShape = Regex("^[0-9a-fA-F]{32}$")

    fun clientIdValid(id: String): Boolean = clientIdShape.matches(id.trim()) && id.trim() != spotifyRejectedClient

    fun init() {
        val prefs = IosKvStore("ext_accounts")
        sp = prefs
        spotify = load(SPOTIFY)
        youtube = load(YOUTUBE)
        yandex = load(YANDEX)
        spotifyRejectedClient = prefs.getString("sp_rejected", "") ?: ""
    }

    private fun load(key: String): ExtAccount? {
        val name = sp?.getString("${key}_name", null)?.takeIf { it.isNotBlank() } ?: return null
        return ExtAccount(name, sp?.getString("${key}_avatar", null))
    }

    private fun store(key: String, account: ExtAccount?) {
        when (key) {
            SPOTIFY -> spotify = account
            YOUTUBE -> youtube = account
            YANDEX -> yandex = account
        }
        sp?.edit()
            ?.putString("${key}_name", account?.name)
            ?.putString("${key}_avatar", account?.avatar)
            ?.apply()
    }

    fun forget(key: String) = store(key, null)

    fun rejectClient(id: String) {
        spotifyRejectedClient = id.trim()
        sp?.edit()?.putString("sp_rejected", spotifyRejectedClient)?.apply()
    }

    private suspend fun get(url: String, auth: String, extra: List<Pair<String, String>> = emptyList()): JsonObject? {
        val res = Http.execute(
            HttpRequest(
                url,
                headers = listOf("Authorization" to auth, "Accept" to "application/json") + extra,
                profile = HttpProfile.API,
                forceNetwork = true,
            ),
        )
        if (res.code == 401 || res.code == 403) return null
        if (!res.isSuccessful) throw ImportException("unavailable", "HTTP ${res.code}")
        return json.parseToJsonElement(res.text()) as? JsonObject
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    suspend fun refreshSpotify(): Boolean? {
        if (!Prefs.spotifyLinked) return false
        val token = runCatching { SpotifyPkce.token() }.getOrNull() ?: Prefs.spotifyToken.takeIf { it.isNotEmpty() } ?: return null
        val me = runCatching { get("https://api.spotify.com/v1/me", "Bearer $token") }.getOrElse {
            Logs.add("import", "spotify profile: ${it.message?.take(60)}")
            return null
        } ?: return false
        val name = me.str("display_name") ?: me.str("id") ?: return null
        val avatar = (me["images"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonObject)?.str("url") }
        store(SPOTIFY, ExtAccount(name, avatar))
        return true
    }

    suspend fun refreshYoutube(): Boolean? {
        if (Prefs.ytCookies.isBlank()) return false
        val account = runCatching { YouTubeImport.client.account() }.getOrElse {
            Logs.add("youtube", "account: ${it.message?.take(80)}")
            return null
        } ?: return false
        store(YOUTUBE, ExtAccount(account.name, account.avatar))
        return true
    }

    suspend fun checkYandex(token: String): Boolean? {
        if (token.isBlank()) return false
        val status = runCatching { get(YandexSource.API + "/account/status", "OAuth ${token.trim()}", YandexSource.HEADERS) }.getOrElse {
            Logs.add("import", "yandex profile: ${it.message?.take(60)}")
            return null
        } ?: return false
        val account = (status["result"] as? JsonObject)?.get("account") as? JsonObject ?: return false
        val uid = account.str("uid") ?: return false
        val name = account.str("displayName") ?: account.str("login") ?: account.str("fullName") ?: uid
        store(YANDEX, ExtAccount(name))
        return true
    }
}
