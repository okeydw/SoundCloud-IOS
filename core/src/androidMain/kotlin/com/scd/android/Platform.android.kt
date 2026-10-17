package com.scd.android

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun clockText(ms: Long): String = android.text.format.DateFormat.format("HH:mm:ss", ms).toString()

actual fun fileExists(path: String): Boolean = java.io.File(path).exists()

actual fun <T> locked(lock: Any, block: () -> T): T = synchronized(lock) { block() }

actual fun monoNanos(): Long = System.nanoTime()

actual fun utcOffsetMinutes(): Int = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000

actual fun isUnreachable(e: Throwable): Boolean =
    e is UnreachableException || e is java.net.ConnectException || e is java.net.UnknownHostException ||
        e is javax.net.ssl.SSLHandshakeException || e is java.net.NoRouteToHostException

actual val IoDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO

actual fun isNetworkFailure(e: Throwable): Boolean = when (e) {
    is UnreachableException -> true
    is java.net.UnknownHostException, is java.net.ConnectException,
    is java.net.SocketTimeoutException, is javax.net.ssl.SSLException -> true
    is java.io.InterruptedIOException -> e.message == "timeout"
    else -> false
}

actual fun oneDecimal(v: Double): String = "%.1f".format(v)
