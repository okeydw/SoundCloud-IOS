package com.scd.android

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object CacheUpgrade {
    private val attempts = HashSet<String>()
    private val lock = Any()

    fun afterPlayed(track: Track) {
        val urn = track.urn
        if (Prefs.offline || !QualityLadder.wantsUpgrade() || !isScUrn(urn)) return
        if (Downloads.isDownloaded(urn) || StreamFiles.cached(urn) == null) return
        val current = ServedQuality.of(urn) ?: Quality.SQ
        val wanted = QualityLadder.allowed(QualityLadder.wanted())
        if (current.rank >= wanted.rank) return
        if (!locked(lock) { attempts.add(urn) }) return
        AppScope.scope.launch {
            val got = runCatching {
                TrackTurns.withTurn(TrackTurns.Priority.BACKGROUND) { StreamFiles.fetchFor(track, above = current, wanted = wanted) }
            }.getOrNull()
            if (got == null || got.tier.rank <= current.rank) {
                if (QualityLadder.refuses(wanted)) locked(lock) { attempts.remove(urn) }
                Logs.add("stream", "nothing above ${current.label} for $urn")
                return@launch
            }
            var waited = 0
            while (PlayerHub.player?.currentUrn == urn) {
                if (waited >= 1_800) {
                    locked(lock) { attempts.remove(urn) }
                    return@launch
                }
                delay(2_000L)
                waited += 2
            }
            StreamFiles.replace(urn, got.bytes, got.tier)
            Logs.add("stream", "upgraded cached $urn ${current.label} → ${got.tier.label}")
        }
    }
}
