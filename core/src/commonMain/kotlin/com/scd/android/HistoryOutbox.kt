package com.scd.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal object HistoryOutbox {
    private val gate = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var started = false
    private val json = Json { ignoreUnknownKeys = true }

    private fun folder(owner: String): String {
        val key = Sha256().apply { update(owner.encodeToByteArray()) }.digest().joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2, '0')
        }
        return Dirs.sub("history-pending/$key")
    }

    fun start() {
        if (started) return
        started = true
        AppScope.scope.launch(IoDispatcher) {
            for (ignored in wake) {
                var backoff = 10_000L
                while (!Prefs.offline && Api.sessionId != null) {
                    try {
                        if (!sendNext()) break
                        backoff = 10_000L
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        Logs.add("history", "sync postponed")
                        delay(backoff)
                        backoff = (backoff * 2).coerceAtMost(300_000L)
                    }
                }
            }
        }
        kick()
    }

    fun kick() { if (started) wake.trySend(Unit) }

    @OptIn(ExperimentalUuidApi::class)
    fun record(track: Track) {
        val sid = Api.sessionId ?: return
        val dir = folder(Prefs.userUrn ?: sid)
        val entry = "${nowMillis()}-${Uuid.random()}.json"
        val compact = Track(urn = track.urn, title = track.title, duration = track.duration, user = track.user, artwork_url = track.artwork_url)
        Fs.writeAtomic("$dir/$entry", json.encodeToString(Track.serializer(), compact).encodeToByteArray())
        kick()
    }

    private fun folders(sid: String) = listOfNotNull(Prefs.userUrn, sid).distinct().map(::folder)

    private suspend fun sendNext(): Boolean = gate.withLock {
        val sid = Api.sessionId ?: return@withLock false
        val path = folders(sid).flatMap(Fs::list).filter { it.endsWith(".json") }.minByOrNull { it.substringAfterLast('/') } ?: return@withLock false
        val data = Fs.readAtomic(path) ?: return@withLock false
        val track = try { json.decodeFromString(Track.serializer(), data.decodeToString()) }
        catch (_: kotlinx.serialization.SerializationException) {
            Logs.add("history", "unreadable pending entry")
            return@withLock Fs.delete(path)
        }
        if (Prefs.offline || sid != Api.sessionId) return@withLock false
        try {
            Api.postHistory(track.urn, track.title, track.user?.username.orEmpty(), track.user?.urn, track.artwork_url, track.duration, sid)
        } catch (e: ApiHttpException) {
            if (e.code !in listOf(400, 404, 422)) throw e
            Logs.add("history", "entry rejected (HTTP ${e.code})")
        }
        Fs.delete(path)
    }

    suspend fun clear(remote: suspend () -> Unit) {
        if (!started) { remote(); return }
        withContext(IoDispatcher) {
            gate.withLock {
                val sid = Api.sessionId ?: return@withLock
                val pending = folders(sid).flatMap(Fs::list)
                remote()
                pending.forEach(Fs::delete)
            }
        }
    }
}
