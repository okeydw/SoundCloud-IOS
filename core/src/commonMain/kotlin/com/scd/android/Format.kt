package com.scd.android

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:" + s.toString().padStart(2, '0')
}

expect fun oneDecimal(v: Double): String

fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> oneDecimal(n / 1_000_000.0) + "M"
    n >= 1_000 -> oneDecimal(n / 1_000.0) + "K"
    else -> n.toString()
}

private val authPrefixes = listOf(
    "/signin", "/login", "/logout", "/connect", "/authorize", "/oauth", "/signup",
    "/register", "/password", "/you/", "/settings", "/sso", "/google", "/apple", "/facebook",
)

fun isAuthPage(host: String, path: String): Boolean {
    if (host != "soundcloud.com" && host != "www.soundcloud.com" && host != "m.soundcloud.com" &&
        host != "on.soundcloud.com"
    ) return true
    val p = path.lowercase()
    return authPrefixes.any { p == it.trimEnd('/') || p.startsWith(it) }
}

fun isNewerVersion(latest: String, current: String): Boolean {
    fun parts(s: String) = s.trimStart('v', 'V').split('.', '-', '+').mapNotNull { it.toIntOrNull() }
    val a = parts(latest)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

fun Track.artModel(size: String = "t500x500"): Any? =
    Downloads.artPath(urn)?.let { "file://$it" } ?: Api.artworkUrl(artwork_url, size)

val Track.displayDuration: Long
    get() = maxOf(duration, full_duration ?: 0L, DurationCache.get(urn))
