package com.scd.android

object ImageRoute {
    private const val COOL_MS = 60_000L
    private const val MAX_PROXIES = 3
    private val down = HashMap<String, Long>()
    private val lock = Any()
    private var reported = 0

    fun report(what: String) {
        val go = locked(lock) { reported++ < 20 }
        if (go) Logs.add("img", what)
    }

    fun base(url: String) = url.substringBefore("/?t=")

    fun proxied(url: String): Boolean = url.substringAfter("://").substringBefore('/').substringBefore(':').endsWith("sndcdn.com")

    fun candidates(target: String, encoded: String): List<String> {
        val now = nowMillis()
        val hosts = (listOf(Endpoints.imageBase) + Endpoints.imageHosts).distinct()
        val (live, cooling) = locked(lock) { hosts.partition { now - (down[it] ?: 0L) >= COOL_MS } }
        return (live.take(MAX_PROXIES) + cooling.take(1)).map { "$it/?t=$encoded" } + target
    }

    fun failover(code: Int) = code == 403 || code == 408 || code == 429 || code >= 500

    fun failed(url: String, why: String) {
        locked(lock) { down[base(url)] = nowMillis() }
        report("proxy $why @${base(url).substringAfter("://")}")
    }

    fun ok(url: String) {
        locked(lock) { down.remove(base(url)) }
    }
}
