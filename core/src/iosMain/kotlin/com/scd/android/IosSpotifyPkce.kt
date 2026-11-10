package com.scd.android

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object SpotifyPkce {
    const val HOST = "spotify-auth"
    const val REDIRECT = "scd://$HOST"
    private const val AUTHORIZE = "https://accounts.spotify.com/authorize"
    private const val TOKEN = "https://accounts.spotify.com/api/token"
    private const val SCOPES = "user-library-read playlist-read-private playlist-read-collaborative"

    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalEncodingApi::class)
    private fun b64(bytes: ByteArray): String = Base64.UrlSafe.encode(bytes).trimEnd('=')

    @OptIn(ExperimentalForeignApi::class)
    private fun randomBytes(size: Int): ByteArray {
        val out = ByteArray(size)
        val ok = out.usePinned { SecRandomCopyBytes(kSecRandomDefault, size.convert(), it.addressOf(0)) } == 0
        if (!ok) kotlin.random.Random.nextBytes(out)
        return out
    }

    private fun randomToken(size: Int): String = b64(randomBytes(size))

    fun authorizeUrl(): String? {
        val clientId = Prefs.spotifyClientId.trim()
        if (clientId.isEmpty()) return null
        val verifier = randomToken(64)
        val state = randomToken(16)
        val challenge = b64(Sha256().apply { update(verifier.encodeToByteArray()) }.digest())
        Prefs.setSpotifyPkce(verifier, state)
        val q = listOf(
            "client_id" to clientId,
            "response_type" to "code",
            "redirect_uri" to REDIRECT,
            "code_challenge_method" to "S256",
            "code_challenge" to challenge,
            "scope" to SCOPES,
            "state" to state,
        ).joinToString("&") { (k, v) -> "$k=${formEncode(v)}" }
        return "$AUTHORIZE?$q"
    }

    fun isCallback(url: String): Boolean = url.startsWith("$REDIRECT?") || url == REDIRECT

    suspend fun complete(url: String): Boolean {
        val (verifier, state) = Prefs.spotifyPkce()
        Prefs.setSpotifyPkce("", "")
        val query = WebUrl.parse(url)?.query().orEmpty()
        query["error"]?.let {
            Logs.add("import", "spotify login refused: ${it.take(60)}")
            return false
        }
        val code = query["code"]
        if (code.isNullOrEmpty() || verifier.isEmpty() || query["state"] != state) {
            Logs.add("import", "spotify login: bad callback")
            return false
        }
        val ok = exchange(
            listOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT,
                "client_id" to Prefs.spotifyClientId.trim(),
                "code_verifier" to verifier,
            ),
        )
        if (ok) ExternalAccounts.refreshSpotify()
        return ok
    }

    suspend fun refresh(): String? {
        val refresh = Prefs.spotifyRefresh
        val clientId = Prefs.spotifyClientId.trim()
        if (refresh.isEmpty() || clientId.isEmpty()) return null
        val ok = exchange(listOf("grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to clientId))
        return if (ok) Prefs.spotifyTokenOrNull() else null
    }

    suspend fun token(): String? = Prefs.spotifyTokenOrNull() ?: refresh()

    private suspend fun exchange(form: List<Pair<String, String>>): Boolean = runCatching {
        val body = form.joinToString("&") { (k, v) -> "${formEncode(k)}=${formEncode(v)}" }
        val res = Http.execute(
            HttpRequest(
                TOKEN,
                method = "POST",
                body = HttpBody(body.encodeToByteArray(), "application/x-www-form-urlencoded"),
                profile = HttpProfile.SLOW,
                forceNetwork = true,
            ),
        )
        val text = res.text()
        if (!res.isSuccessful) {
            Logs.add("import", "spotify token HTTP ${res.code}: ${text.take(120)}")
            if (text.contains("invalid_client")) ExternalAccounts.rejectClient(Prefs.spotifyClientId)
            return@runCatching false
        }
        val o = json.parseToJsonElement(text) as? JsonObject ?: return@runCatching false
        val access = (o["access_token"] as? JsonPrimitive)?.contentOrNull ?: return@runCatching false
        val expires = (o["expires_in"] as? JsonPrimitive)?.longOrNull ?: 3600L
        (o["refresh_token"] as? JsonPrimitive)?.contentOrNull?.let { Prefs.changeSpotifyRefresh(it) }
        Prefs.changeSpotifyToken(access, nowMillis() + expires * 1000L)
        Logs.add("import", "spotify token received (${(o["scope"] as? JsonPrimitive)?.contentOrNull ?: "no scope"})")
        true
    }.getOrElse {
        if (it is kotlinx.coroutines.CancellationException) throw it
        Logs.add("import", "spotify token failed: ${it.message?.take(60)}")
        false
    }
}
