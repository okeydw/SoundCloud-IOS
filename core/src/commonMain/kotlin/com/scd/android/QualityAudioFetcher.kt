package com.scd.android

import kotlinx.coroutines.CancellationException

data class QualityAudio(val bytes: ByteArray, val tier: Quality)

class QualityAudioFetcher(
    private val request: suspend (String) -> HttpResponse?,
    private val decodeSource: suspend (String) -> ByteArray?,
    private val isAudio: (ByteArray) -> Boolean,
) {
    suspend fun fetch(
        streams: List<String>,
        storage: List<String>,
        stored: Quality?,
        wanted: Quality,
        above: Quality? = null,
    ): QualityAudio? {
        val verdict = QualityLadder.Verdict()
        var fallback: QualityAudio? = null
        var storageTried = false
        fun accept(audio: QualityAudio?, tier: Quality): QualityAudio? {
            if (audio == null || (above != null && audio.tier.rank <= above.rank)) return null
            if (audio.tier.rank >= tier.rank) return audio
            if (fallback == null || audio.tier.rank > fallback!!.tier.rank) fallback = audio
            return null
        }
        suspend fun fromStorage(tier: Quality): QualityAudio? {
            storageTried = true
            for (url in storage) accept(direct(url, null, stored, verdict), tier)?.let { return it }
            return null
        }
        try {
            val upper = QualityLadder.upperTiers(QualityLadder.allowed(wanted), above ?: Quality.SQ)
            val tiers = if (above == null) upper + Quality.SQ else upper
            for (tier in tiers) {
                fallback?.takeIf { it.tier.rank >= tier.rank }?.let { return it }
                if (!storageTried && (tier == Quality.SQ || (stored != null && stored.rank >= tier.rank))) {
                    fromStorage(tier)?.let { return it }
                }
                if (QualityLadder.refuses(tier)) continue
                for (base in streams.distinct()) {
                    val url = QualityLadder.tierUrl(base, tier)
                    accept(direct(url, tier, null, verdict), tier)?.let { return it }
                }
                if (tier != Quality.UQ) {
                    for (base in streams.distinct()) {
                        val url = QualityLadder.tierUrl(base.replaceFirst("/stream/", "/download/"), tier)
                        accept(download(url, tier, verdict), tier)?.let { return it }
                    }
                }
            }
            return fallback
        } finally {
            verdict.settle()
        }
    }

    private suspend fun response(url: String, asked: Quality?, verdict: QualityLadder.Verdict): HttpResponse? {
        asked?.let(verdict::asked)
        val response = try {
            request(url)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { null }
        asked?.let { tier ->
            verdict.answered(tier, response?.let { QualityLadder.refusalHold(it.code, it.header("retry-after")) })
        }
        return response?.takeIf { it.isSuccessful }
    }

    private suspend fun direct(url: String, asked: Quality?, stored: Quality?, verdict: QualityLadder.Verdict): QualityAudio? {
        val res = response(url, asked, verdict) ?: return null
        if (!isAudio(res.body)) return null
        val actual = AudioQualityPolicy.served(res.header("x-audio-quality"), stored?.label, asked)
        verdict.served(actual)
        return QualityAudio(res.body, actual)
    }

    private suspend fun download(url: String, asked: Quality, verdict: QualityLadder.Verdict): QualityAudio? {
        val res = response(url, asked, verdict) ?: return null
        val candidates = Api.parseDownload(res.text())?.candidates.orEmpty()
            .filter { it.quality != "lq" && Quality.of(it.quality).rank >= asked.rank }
            .sortedBy { if (asked == Quality.SQ) Quality.of(it.quality).rank else -Quality.of(it.quality).rank }
        for (candidate in candidates) {
            val target = when (candidate.kind) {
                "progressive" -> candidate.url
                "hls" -> candidate.manifest_url?.let(HlsSource::wrap)
                "encrypted-hls" -> EncHls.wrap(candidate.init_base64, candidate.segments, candidate.key_base64)
                else -> null
            }?.takeIf { it.isNotBlank() } ?: continue
            val bytes = try { decodeSource(target) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
            if (bytes != null && isAudio(bytes)) {
                val actual = Quality.of(candidate.quality)
                verdict.served(actual)
                return QualityAudio(bytes, actual)
            }
        }
        return null
    }
}
