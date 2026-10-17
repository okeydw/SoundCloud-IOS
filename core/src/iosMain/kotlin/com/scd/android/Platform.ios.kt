package com.scd.android

import platform.Foundation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.native.identityHashCode

actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

private val clockFormat = NSDateFormatter().apply { dateFormat = "HH:mm:ss" }

actual fun clockText(ms: Long): String =
    clockFormat.stringFromDate(NSDate.dateWithTimeIntervalSince1970(ms / 1000.0))

actual fun fileExists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

private val stripes = Array(256) { NSRecursiveLock() }

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
actual fun <T> locked(lock: Any, block: () -> T): T {
    val l = stripes[lock.identityHashCode() and 255]
    l.lock()
    try {
        return block()
    } finally {
        l.unlock()
    }
}

actual fun monoNanos(): Long = (NSProcessInfo.processInfo.systemUptime * 1_000_000_000.0).toLong()

actual fun utcOffsetMinutes(): Int = (NSTimeZone.localTimeZone.secondsFromGMT / 60).toInt()

actual fun isUnreachable(e: Throwable): Boolean = e is UnreachableException

actual val IoDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO

actual fun isNetworkFailure(e: Throwable): Boolean = e is kotlinx.io.IOException

actual fun oneDecimal(v: Double): String {
    val scaled = kotlin.math.round(kotlin.math.abs(v) * 10).toLong()
    return (if (v < 0 && scaled != 0L) "-" else "") + "${scaled / 10}.${scaled % 10}"
}
