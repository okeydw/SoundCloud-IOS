package com.scd.android

import kotlin.concurrent.Volatile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

object Likes {
    private var sp: KvStore? = null

    var urns by mutableStateOf(setOf<String>())
        private set

    var onLiked: (Track) -> Unit = {}

    fun init(store: KvStore) {
        sp = store
        LibraryPages.store = sp
        urns = sp?.getStringSet("urns", emptySet())?.toSet() ?: emptySet()
        changes = runCatching { json.decodeFromString(serializer, sp?.getString("changes", null) ?: "[]") }
            .getOrDefault(emptyList()).associateBy { it.track.urn }
        manualOrder = LikedTrackOrder.decode(sp?.getString("manual_order", null))
    }

    var manualOrder by mutableStateOf(LikedTrackOrder())
        private set

    fun ordered(tracks: List<Track>): List<Track> = manualOrder.apply(tracks)

    fun reorder(order: List<String>) {
        manualOrder = manualOrder.updated(order)
        sp?.edit()?.putString("manual_order", manualOrder.encode())?.apply()
    }

    @kotlinx.serialization.Serializable
    private data class Change(val track: Track, val liked: Boolean, val sent: Boolean = false)

    private val lock = Any()
    private val syncGate = kotlinx.coroutines.sync.Mutex()
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val serializer = kotlinx.serialization.builtins.ListSerializer(Change.serializer())
    private var changes by mutableStateOf(emptyMap<String, Change>())
    private var version = 0

    private fun persist() {
        sp?.edit()?.putStringSet("urns", urns)
            ?.putString("changes", json.encodeToString(serializer, changes.values.toList()))?.apply()
    }

    fun visible(tracks: List<Track>): List<Track> {
        val edits = changes
        if (edits.isEmpty()) return tracks
        return (edits.values.toList().asReversed().filter { it.liked }.map { it.track } + tracks)
            .filter { edits[it.urn]?.liked != false }.distinctBy { it.urn }
    }

    private fun overlay(ids: Set<String>): Set<String> =
        (ids + changes.filterValues { it.liked }.keys) - changes.filterValues { !it.liked }.keys

    fun seed(tracks: List<Track>, freshSince: Int? = null) = locked(lock) {
        val ids = tracks.map { it.urn }.toSet()
        val old = changes
        if (freshSince == version) changes = changes.filterValues { !it.sent || it.track.urn !in ids }
        val next = overlay(urns + ids)
        if (next != urns || changes != old) { urns = next; persist() }
    }

    fun snapshot(): Int = locked(lock) { version }

    fun replaceAll(tracks: List<Track>, since: Int) = locked(lock) {
        val ids = tracks.map { it.urn }.toSet()
        val old = changes
        if (since == version) changes = changes.filterValues { !it.sent }
        val next = overlay(if (since == version) ids else urns + ids)
        if (next != urns || changes != old) { urns = next; persist() }
    }

    fun reset() = locked(lock) {
        version++
        manualOrder = LikedTrackOrder()
        changes = emptyMap()
        urns = emptySet()
        LibraryPages.clear()
        Unit
    }

    fun isLiked(urn: String) = urn in urns

    private fun change(track: Track, liked: Boolean) {
        version++
        changes = (changes - track.urn) + (track.urn to Change(track, liked))
        urns = if (liked) urns + track.urn else urns - track.urn
        persist()
    }

    fun clear(urn: String) {
        locked(lock) {
            if (urn in urns) change(Track(urn = urn), false)
        }
        sync()
    }

    suspend fun toggle(track: Track): Boolean {
        if (track.external) return isLiked(track.urn)
        val liked = locked(lock) { (!isLiked(track.urn)).also { change(track, it) } }
        if (liked) {
            WaveFeedback.positive(track.urn)
            Events.like(track.urn)
            onLiked(track)
            if (Dislikes.isDisliked(track.urn)) Dislikes.clear(track.urn)
        }
        sync()
        return liked
    }

    fun sync() {
        val sid = Api.sessionId ?: return
        if (Prefs.offline || changes.values.none { !it.sent }) return
        AppScope.scope.launch {
            if (!syncGate.tryLock()) return@launch
            try {
                val attempted = mutableSetOf<Change>()
                while (!Prefs.offline && sid == Api.sessionId) {
                    val edit = locked(lock) { changes.values.firstOrNull { !it.sent && it !in attempted } } ?: break
                    attempted += edit
                    try {
                        if (edit.liked) Api.likeTrack(edit.track, sid) else Api.unlikeTrack(edit.track.urn, sid)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (e !is ApiHttpException || e.code !in listOf(400, 404, 422)) break
                        if (edit.liked || e.code != 404) {
                            Logs.add("likes", "sync rejected ${edit.track.urn} (HTTP ${e.code})")
                            locked(lock) {
                                if (sid == Api.sessionId && changes[edit.track.urn] === edit) {
                                    changes = changes - edit.track.urn
                                    urns = if (edit.liked) urns - edit.track.urn else urns + edit.track.urn
                                    persist()
                                }
                            }
                            continue
                        }
                    }
                    locked(lock) {
                        if (sid == Api.sessionId && changes[edit.track.urn] === edit) {
                            changes = changes + (edit.track.urn to edit.copy(sent = true))
                            version++
                            persist()
                        }
                    }
                }
            } finally { syncGate.unlock() }
        }
    }
}
