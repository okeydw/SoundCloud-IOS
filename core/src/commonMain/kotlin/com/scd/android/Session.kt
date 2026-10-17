package com.scd.android

import kotlin.concurrent.Volatile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SessionGuard {

    var signedIn by mutableStateOf(false)
        private set

    fun init() {
        signedIn = Api.sessionId != null
    }

    fun signIn(context: PlatformContext, sessionId: String) {
        Api.storeSession(sessionId)
        signedIn = true
    }

    fun signOut(context: PlatformContext, controller: QueuePlayer?) {
        val sid = Api.sessionId
        if (sid != null) AppScope.scope.launch { Api.logout(sid) }
        PlatformHooks.wiper.wipe(context, controller)
        signedIn = false
    }

    fun dropLocal(context: PlatformContext, controller: QueuePlayer?) {
        PlatformHooks.wiper.wipe(context, controller)
        signedIn = false
    }

    suspend fun handleExpired(context: PlatformContext, controller: QueuePlayer?) {
        if (!SessionState.expired) return
        val stream = SessionState.fromStream
        val recentlyRefreshed = nowMillis() - SessionState.lastRefreshAt < 10 * 60_000L
        if (stream && !recentlyRefreshed) {
            val code = Api.refreshSession()
            if (code in 200..299) {
                SessionState.lastRefreshAt = nowMillis()
                SessionState.consume()
                Logs.add("auth", "stream 401 → session refreshed")
                PlatformHooks.forgetResolvedStreams()
                withContext(Dispatchers.Main) {
                    controller?.let { c ->
                        if (c.failed || c.idle) {
                            c.prepare()
                            c.play()
                        }
                    }
                }
                return
            }
            if (code != 401) {
                SessionState.consume()
                Logs.add("auth", "refresh unavailable (code $code), keeping session")
                return
            }
        }
        val valid = Api.sessionValid()
        SessionState.consume()
        when {
            valid == false -> {
                Logs.add("auth", "session not authenticated${if (stream) " (stream 401)" else ""} → sign in again")
                withContext(Dispatchers.Main) { dropLocal(context, controller) }
            }
            stream && valid == true -> Logs.add("auth", "stream 401 while session is valid")
            else -> Logs.add("auth", "401 ignored, session check: $valid")
        }
    }
}

object BrowserLogin {
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    suspend fun run(context: PlatformContext) {
        if (busy) return
        busy = true
        status = null
        try {
            val login = Api.authLogin()
            openInBrowser(context, login.url)
            status = Strings.get("login_waiting")
            val deadline = nowMillis() + 5 * 60_000
            while (nowMillis() < deadline) {
                delay(2_000)
                val st = runCatching { Api.authLoginStatus(login.loginRequestId) }.getOrNull() ?: continue
                when (st.status) {
                    "completed" -> st.sessionId?.let {
                        SessionGuard.signIn(context, it)
                        status = null
                        return
                    }
                    "failed", "expired" -> {
                        status = Strings.get("login_failed") + (st.error?.let { ": $it" } ?: "")
                        return
                    }
                }
            }
            status = Strings.get("login_timeout")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            status = Strings.get("error_network") + "\n" + (e.message ?: "")
        } finally {
            busy = false
        }
    }
}

object LinkClaims {

    suspend fun accountName(pull: Boolean): String? {
        if (!pull) return null
        return runCatching { Api.authStatus() }.getOrNull()?.username?.takeIf { it.isNotBlank() }
    }

    suspend fun claim(context: PlatformContext, token: String, pull: Boolean, controller: QueuePlayer?) {
        val res = runCatching { Api.linkClaim(token, pull = pull) }
        val sid = res.getOrNull()?.sessionId
        withContext(Dispatchers.Main) {
            when {
                pull && res.isSuccess -> {
                    Logs.add("qr", "pull link: other device signed in")
                    showToast(context, Strings.get("link_pull_done"))
                }
                !pull && sid != null -> {
                    if (SessionGuard.signedIn) PlatformHooks.wiper.wipe(context, controller)
                    SessionGuard.signIn(context, sid)
                }
                else -> {
                    val err = res.exceptionOrNull()
                    Logs.add("qr", "link claim failed (${if (pull) "pull" else "push"}): " + (err?.message ?: "no session"))
                    val code = (err as? ApiHttpException)?.code
                    showToast(context, Strings.get(if (code == 400 || code == 404) "link_expired" else "login_failed"))
                }
            }
            PendingLink.clear()
        }
    }
}

object Startup {
    @Volatile
    private var done = false

    fun reset() {
        done = false
    }

    fun run(context: PlatformContext) {
        if (done) return
        done = true
        WaveState.restore()
        Tiles.restore()
        val app = context
        AppScope.scope.launch { Dislikes.seed() }
        AppScope.scope.launch { BlockedArtists.refresh() }
        AppScope.scope.launch { Auras.loadMine() }
        if (Prefs.offline) return
        AppScope.scope.launch {
            launch { runCatching { Api.subscription() }.onSuccess { Prefs.saveStar(it.premium) } }
            launch {
                runCatching { Api.me() }.onSuccess { me ->
                    me.urn?.takeIf { it.isNotEmpty() }?.let { Prefs.saveUserUrn(it) }
                }
            }
            launch {
                delay(600)
                val since = Likes.snapshot()
                runCatching { Api.likedTracks(0, 100, fresh = true) }.onSuccess { res ->
                    if (res.has_more) Likes.seed(res.collection) else Likes.replaceAll(res.collection, since)
                }
                delay(5_000)
                PlatformHooks.afterLikesSynced(app)
            }
            launch {
                delay(1_500)
                LocalPlaylists.sync()
                runCatching { Api.likedPlaylists() }
                delay(400)
                runCatching { Api.likedTracks() }
                runCatching { Api.history() }
                delay(15_000)
                PlaylistPrefetch.run()
            }
            launch {
                delay(8_000)
                Downloads.backfillArt()
            }
        }
    }
}

object Health {
    private const val GRACE_MS = 15_000L

    private val kicks = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    fun kick() {
        kicks.trySend(Unit)
    }

    var onAutoOffline: (Boolean) -> Unit = {}

    var noInternet by mutableStateOf(false)
        private set
    var serverDown by mutableStateOf(false)
        private set

    suspend fun watch(context: PlatformContext) {
        var netLostAt = 0L
        var serverLostAt = 0L
        while (true) {
            val now = monoNanos() / 1_000_000
            val net = hasNetwork(context)
            if (net) {
                netLostAt = 0L
                noInternet = false
            } else {
                if (netLostAt == 0L) netLostAt = now
                noInternet = now - netLostAt >= GRACE_MS
            }
            val auto = Prefs.autoOffline && !Prefs.offlineManual
            val ok = if (net && (!Prefs.offline || auto)) Api.healthOk() else true
            val after = monoNanos() / 1_000_000
            if (ok) {
                serverLostAt = 0L
                serverDown = false
            } else {
                if (serverLostAt == 0L) serverLostAt = now
                serverDown = after - serverLostAt >= GRACE_MS
            }
            if (auto) {
                if (!Prefs.offline && (!net || serverDown)) {
                    Prefs.setOfflineAuto(true)
                    Logs.add("net", if (!net) "no network → offline mode" else "server unreachable → offline mode")
                    runCatching { onAutoOffline(true) }
                } else if (Prefs.offline && net && ok) {
                    Prefs.setOfflineAuto(false)
                    Logs.add("net", "network is back → online")
                    runCatching { onAutoOffline(false) }
                    PlaylistPrefetch.run()
                }
            }
            kotlinx.coroutines.withTimeoutOrNull(if (netLostAt != 0L || serverLostAt != 0L || (auto && Prefs.offline)) 4_000L else 12_000L) { kicks.receive() }
        }
    }
}

object Updates {
    var available by mutableStateOf<Pair<String, String>?>(null)
        private set
    @Volatile
    private var checked = false

    suspend fun check(context: PlatformContext) {
        if (checked || Prefs.offline) return
        checked = true
        val current = appVersionName(context) ?: return
        val latest = Api.latestRelease() ?: return
        if (isNewerVersion(latest.tag, current)) {
            Updater.asset = Updater.pick(context, latest.assets)
            available = latest.tag to latest.url
        }
    }
}

object PositionTicker {
    suspend fun run(controller: QueuePlayer) {
        while (true) {
            NowPlaying.position = controller.positionMs
            NowPlaying.duration = controller.durationMs
            WaveState.remember(controller.currentUrn, NowPlaying.position)
            delay(if (controller.isPlaying) 250 else 1000)
        }
    }
}
