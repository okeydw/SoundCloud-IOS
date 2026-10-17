package com.scd.android

object ShuffleQueue {
    private var original: List<Track> = emptyList()
    var onChanged: () -> Unit = {}

    val on: Boolean get() = Prefs.shuffleOn

    fun isWave(urn: String?): Boolean = WaveFeedback.isOwned(urn)

    fun active(player: QueuePlayer?): Boolean = on && !isWave(player?.currentUrn)

    fun arrange(queue: List<Track>, index: Int): Pair<List<Track>, Int> {
        val start = queue.getOrNull(index)
        if (!on || start == null || queue.size <= 1 || isWave(start.urn)) {
            original = emptyList()
            return queue to index
        }
        original = queue
        val rest = queue.filterIndexed { i, t -> i != index && t.urn != start.urn }.shuffled()
        return (listOf(start) + rest) to 0
    }

    fun widen(full: List<Track>) {
        if (original.isNotEmpty()) original = full.distinctBy { it.urn }
    }

    fun enable() {
        Prefs.changeShuffleOn(true)
        NowPlaying.shuffle = true
        onChanged()
    }

    fun set(player: QueuePlayer, value: Boolean) {
        if (value == on) return
        if (value && isWave(player.currentUrn)) return
        runCatching { if (value) shuffleOn(player) else shuffleOff(player) }
            .onFailure { Logs.add("queue", "shuffle failed: ${it::class.simpleName}") }
        Prefs.changeShuffleOn(value)
        NowPlaying.shuffle = value
        onChanged()
    }

    private fun queueTracks(p: QueuePlayer): List<Track> = (0 until p.itemCount).mapNotNull { p.trackAt(it) }

    private fun keepOnly(p: QueuePlayer) {
        val cur = p.currentIndex
        if (cur > 0) p.removeRange(0, cur)
        if (p.itemCount > 1) p.removeRange(1, p.itemCount)
    }

    private fun shuffleOn(p: QueuePlayer) {
        val all = queueTracks(p)
        val current = p.currentTrack ?: return
        original = all.distinctBy { it.urn }
        if (all.size <= 1) return
        val rest = original.filter { it.urn != current.urn }.shuffled()
        keepOnly(p)
        if (rest.isNotEmpty()) p.addTracks(rest)
        Logs.add("queue", "shuffle on: ${rest.size + 1} tracks")
    }

    private fun shuffleOff(p: QueuePlayer) {
        val current = p.currentTrack
        val inQueue = queueTracks(p)
        if (current == null || inQueue.size <= 1) {
            original = emptyList()
            return
        }
        val present = inQueue.map { it.urn }.toSet()
        val base = original.filter { it.urn in present }
        val known = base.map { it.urn }.toSet()
        val ordered = (base + inQueue.filter { it.urn !in known }).distinctBy { it.urn }
        original = emptyList()
        val pos = ordered.indexOfFirst { it.urn == current.urn }
        if (pos < 0) return
        keepOnly(p)
        if (pos > 0) p.insertTracks(0, ordered.subList(0, pos))
        if (pos + 1 < ordered.size) p.addTracks(ordered.subList(pos + 1, ordered.size))
        Logs.add("queue", "shuffle off: order restored")
    }
}
