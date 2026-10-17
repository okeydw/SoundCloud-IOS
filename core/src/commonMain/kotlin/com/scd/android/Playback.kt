package com.scd.android

import kotlin.concurrent.Volatile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object Playback {

    fun playable(t: Track) = isPlayable(t)

    var source by mutableStateOf<String?>(null)
        private set

    fun shuffledFrom(key: String): Boolean = source == key && NowPlaying.shuffle && NowPlaying.urn != null

    fun offlineReady(t: Track): Boolean = PlatformHooks.offlineReady(t.urn)

    private fun queueOf(list: List<Track>): List<Track> {
        val offline = Prefs.offline
        return list.filter { playable(it) && (!offline || offlineReady(it)) }.distinctBy { it.urn }
    }

    private var generation = 0L
    private var extendJob: Job? = null

    fun play(
        player: QueuePlayer?,
        list: List<Track>,
        track: Track,
        autoContinue: Boolean = false,
        startMs: Long = 0L,
        source: String? = null,
        more: (suspend () -> List<Track>)? = null,
    ) {
        if (!playable(track)) return
        if (Prefs.offline && !offlineReady(track)) return
        val p = player ?: return
        val queue = queueOf(list)
        this.source = source
        val gen = ++generation
        extendJob?.cancel()
        val index = queue.indexOfFirst { it.urn == track.urn }.coerceAtLeast(0)
        NowPlaying.autoContinue = autoContinue
        LocalRadio.stopTrackRadio()
        val (arranged, start) = ShuffleQueue.arrange(queue, index)
        p.setTracks(arranged, start, if (arranged.getOrNull(start)?.urn == track.urn) startMs else 0L)
        p.prepare()
        p.play()
        if (more != null) extend(p, gen, more)
    }

    fun playShuffled(player: QueuePlayer?, list: List<Track>, autoContinue: Boolean = false, source: String? = null, more: (suspend () -> List<Track>)? = null) {
        val queue = queueOf(list)
        if (queue.isEmpty() || player == null) return
        ShuffleQueue.enable()
        play(player, queue, queue.random(), autoContinue, source = source, more = more)
    }

    fun toggleShuffle(player: QueuePlayer?, key: String, list: List<Track>, autoContinue: Boolean = false, more: (suspend () -> List<Track>)? = null) {
        if (player != null && shuffledFrom(key)) {
            ShuffleQueue.set(player, false)
        } else {
            playShuffled(player, list, autoContinue, key, more)
        }
    }

    private fun extend(p: QueuePlayer, gen: Long, more: suspend () -> List<Track>) {
        extendJob = AppScope.scope.launch(Dispatchers.Main) {
            val all = try {
                more()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.add("queue", "full list unavailable: ${e::class.simpleName}")
                return@launch
            }
            if (gen != generation) return@launch
            val full = queueOf(all)
            val current = p.currentUrn ?: return@launch
            if (full.none { it.urn == current }) return@launch
            val present = (0 until p.itemCount).mapNotNullTo(HashSet()) { p.urnAt(it) }
            val extra = full.filter { it.urn !in present }
            if (extra.isEmpty()) return@launch
            if (ShuffleQueue.active(p)) {
                val from = p.currentIndex + 1
                val upcoming = (from until p.itemCount).mapNotNull { p.trackAt(it) }
                val mixed = (upcoming + extra).shuffled()
                if (from < p.itemCount) p.removeRange(from, p.itemCount)
                p.addTracks(mixed)
                ShuffleQueue.widen(full)
            } else {
                p.addTracks(extra)
            }
            Logs.add("queue", "queue extended: +${extra.size} tracks")
        }
    }

    suspend fun playSingle(player: () -> QueuePlayer?, track: Track): Boolean = withContext(Dispatchers.Main) {
        var p = player()
        var waited = 0
        while (p == null && waited < 100) {
            delay(100)
            waited++
            p = player()
        }
        if (p == null) return@withContext false
        source = null
        generation++
        extendJob?.cancel()
        NowPlaying.autoContinue = false
        LocalRadio.stopTrackRadio()
        p.setTracks(listOf(track), 0, 0)
        p.prepare()
        p.play()
        true
    }

    @Volatile
    private var radioJob: Job? = null

    var radioBusy by mutableStateOf(false)
        private set

    fun startTrackRadio(player: QueuePlayer, seed: Track, onDone: (Int) -> Unit = {}) {
        radioJob?.cancel()
        radioBusy = true
        source = null
        generation++
        extendJob?.cancel()
        radioJob = AppScope.scope.launch {
            val added = runCatching { buildRadio(player, seed) }.getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                Logs.add("radio", "failed: ${it::class.simpleName} ${it.message?.take(80) ?: ""}")
                0
            }
            withContext(Dispatchers.Main) {
                radioBusy = false
                onDone(added)
            }
        }
    }

    private suspend fun buildRadio(player: QueuePlayer, seed: Track): Int {
        fun usable(list: List<Track>) = list.distinctBy { it.urn }.filter {
            it.urn != seed.urn && playable(it) && !it.hidden
        }
        LocalRadio.startFrom(seed, listOf(seed.urn))
        val started = nowMillis()
        val (add, source) = coroutineScope {
            val server = async { usable(Api.waveFromTrack(seed.urn, budgetMs = 12_000)) }
            val local = async { withTimeoutOrNull(25_000) { usable(LocalRadio.trackBatch(20)) } ?: emptyList() }
            val fromServer = server.await()
            Logs.add("radio", "server: ${fromServer.size} in ${(nowMillis() - started) / 1000}s")
            if (fromServer.isNotEmpty()) {
                local.cancel()
                fromServer to "server"
            } else {
                val fromLocal = local.await()
                Logs.add("radio", "local: ${fromLocal.size} in ${(nowMillis() - started) / 1000}s")
                fromLocal to "local"
            }
        }
        if (add.isEmpty()) {
            Logs.add("radio", "nothing found for ${seed.title.take(30)}")
            LocalRadio.stopTrackRadio()
            return 0
        }
        withContext(Dispatchers.Main) {
            NowPlaying.autoContinue = true
            val cur = player.currentIndex
            if (player.currentUrn != seed.urn) {
                player.setTracks(listOf(seed) + add, 0, 0)
                player.prepare()
                player.play()
            } else {
                if (player.itemCount > cur + 1) player.removeRange(cur + 1, player.itemCount)
                player.addTracks(add)
            }
        }
        Logs.add("radio", "+${add.size} from $source")
        return add.size
    }

    fun playNext(player: QueuePlayer?, track: Track): Boolean {
        val p = player ?: return false
        if (!playable(track)) return false
        if (p.itemCount == 0 || p.currentUrn == null) {
            play(p, listOf(track), track)
            return true
        }
        val current = p.currentIndex
        if (p.currentUrn == track.urn) return true
        val existing = (0 until p.itemCount).firstOrNull { it != current && p.urnAt(it) == track.urn }
        val target = current + 1
        if (existing != null) {
            p.move(existing, if (existing < target) target - 1 else target)
        } else {
            p.insert(target, track)
        }
        return true
    }

    fun radioNow(player: QueuePlayer, seed: Track, onDone: (Int) -> Unit = {}) {
        if (!playable(seed)) return
        if (player.currentUrn != seed.urn) {
            NowPlaying.autoContinue = true
            player.setTracks(listOf(seed), 0, 0)
            player.prepare()
            player.play()
        }
        startTrackRadio(player, seed, onDone)
    }

    suspend fun artistRadio(urn: String, fallback: List<Track>): List<Track> {
        fun usable(list: List<Track>) = list.filter { playable(it) && !it.hidden }.distinctBy { it.urn }
        val own = usable(fallback)
        val catalogId = CatalogRepo.findCatalogFor(urn, fallback)?.id
        if (catalogId != null) {
            usable(Api.waveFromArtist(catalogId)).takeIf { it.isNotEmpty() }?.let { return it }
        }
        val seed = own.firstOrNull()
        if (seed != null) {
            val wave = usable(Api.waveFromTrack(seed.urn, budgetMs = 12_000)).filter { it.urn != seed.urn }
            if (wave.isNotEmpty()) {
                Logs.add("wave", "artist radio from ${seed.title.take(24)}: ${wave.size}")
                return listOf(seed) + wave
            }
        }
        return own.shuffled()
    }
}
