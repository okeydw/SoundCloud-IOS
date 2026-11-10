package com.scd.android

enum class Quality(val label: String, val rank: Int) {
    SQ("sq", 0),
    HQ("hq", 1),
    UQ("uq", 2);

    val badge: String get() = label.uppercase()

    companion object {
        fun parse(value: String?): Quality? = when (value?.trim()?.lowercase()) {
            "sq" -> SQ
            "hq" -> HQ
            "uq" -> UQ
            else -> null
        }

        fun of(value: String?): Quality = parse(value) ?: SQ

        fun fromLabel(label: String?): Quality = parse(label?.trim()?.substringBefore(' ')) ?: SQ
    }
}

object QualityLadder {
    const val BACKUP_MS = 15_000L
    const val REFUSAL_TTL_MS = 600_000L

    fun wanted(): Quality = when (Prefs.streamQuality) {
        Prefs.QUALITY_SQ -> Quality.SQ
        Prefs.QUALITY_HQ -> Quality.HQ
        else -> if (Prefs.star) Quality.UQ else Quality.HQ
    }

    fun wantsUpgrade(): Boolean =
        Prefs.streamQuality == Prefs.QUALITY_HQ || Prefs.streamQuality == Prefs.QUALITY_UQ

    fun upgradeWanted(star: Boolean = Prefs.star): Quality = if (star) Quality.UQ else Quality.HQ

    fun downloadWanted(): Quality = DownloadQuality.wanted(Prefs.downloadQuality, Prefs.star)

    fun upperTiers(wanted: Quality, above: Quality = Quality.SQ): List<Quality> =
        listOf(Quality.UQ, Quality.HQ).filter { it.rank <= wanted.rank && it.rank > above.rank }

    fun tierUrl(url: String, tier: Quality): String {
        if (HlsSource.isHls(url) || EncHls.isEnc(url)) return url
        val q = url.indexOf('?')
        val base = if (q < 0) url else url.substring(0, q)
        val kept = if (q < 0) {
            emptyList()
        } else {
            url.substring(q + 1).split('&').filter {
                it.isNotEmpty() && !it.startsWith("hq=") && !it.startsWith("quality=")
            }
        }
        val params = kept + (if (tier.rank > Quality.SQ.rank) listOf("hq=true") else emptyList()) +
            "quality=${tier.label}"
        return base + "?" + params.joinToString("&")
    }


    private class Refusal(val session: String?, val tier: Quality, val until: Long) {
        fun holds(session: String?): Boolean =
            this.session == session && nowMillis() < until
    }

    private val lock = Any()
    private var refusal: Refusal? = null

    private fun below(tier: Quality): Quality = if (tier == Quality.UQ) Quality.HQ else Quality.SQ

    fun allowed(wanted: Quality, session: String? = Api.sessionId): Quality {
        val r = locked(lock) { refusal } ?: return wanted
        return if (r.holds(session) && wanted.rank >= r.tier.rank) below(r.tier) else wanted
    }

    fun refuses(tier: Quality, session: String? = Api.sessionId): Boolean =
        allowed(tier, session).rank < tier.rank

    fun noteRefused(tier: Quality, holdMs: Long, session: String? = Api.sessionId) {
        if (tier == Quality.SQ || holdMs <= 0L) return
        val hold = minOf(holdMs, REFUSAL_TTL_MS)
        val stored = locked(lock) {
            val current = refusal
            if (current != null && current.holds(session) && current.tier.rank <= tier.rank) return@locked false
            refusal = Refusal(session, tier, nowMillis() + hold)
            true
        }
        if (!stored) return
        Logs.add(
            "stream",
            "server refused ${tier.label} for this session, asking at most ${below(tier).label} for ${hold / 1000} s",
        )
    }

    fun forgetRefusal() {
        locked(lock) { refusal = null }
    }

    fun refusalHold(code: Int, retryAfter: String?): Long? = when (code) {
        403 -> REFUSAL_TTL_MS
        429 -> retryAfter?.trim()?.toLongOrNull()?.takeIf { it > 0L }?.times(1000L)
        else -> null
    }

    class Verdict {
        private val holds = HashMap<Quality, Long?>()
        private val pending = HashMap<Quality, Int>()
        private val served = HashSet<Quality>()
        private val guard = Any()

        fun asked(tier: Quality) {
            if (tier == Quality.SQ) return
            locked(guard) {
                pending[tier] = (pending[tier] ?: 0) + 1
                if (!holds.containsKey(tier)) holds[tier] = 0L
            }
        }

        fun answered(tier: Quality, refusedFor: Long?) {
            if (tier == Quality.SQ) return
            locked(guard) {
                pending[tier] = ((pending[tier] ?: 1) - 1).coerceAtLeast(0)
                val prev = if (holds.containsKey(tier)) holds[tier] else 0L
                holds[tier] = if (prev == null || refusedFor == null) null else maxOf(prev, refusedFor)
            }
        }

        fun served(tier: Quality) {
            locked(guard) { served += tier }
        }

        fun settle() {
            val refused = locked(guard) {
                holds.filter { (tier, hold) -> tier !in served && (pending[tier] ?: 0) == 0 && hold != null && hold > 0L }
            }
            for ((tier, hold) in refused) QualityLadder.noteRefused(tier, hold ?: continue)
        }
    }
}

object ServedQuality {
    private var store: KvStore? = null

    fun init(kv: KvStore) {
        store = kv
    }

    fun of(urn: String): Quality? = Quality.parse(store?.getString(urn, null))

    fun record(urn: String, tier: Quality) {
        val kv = store ?: return
        if (kv.getString(urn, null) == tier.label) return
        kv.edit().putString(urn, tier.label).apply()
    }

    fun forget(urn: String) {
        store?.edit()?.remove(urn)?.apply()
    }
}
