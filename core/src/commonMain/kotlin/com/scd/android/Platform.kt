package com.scd.android

expect fun nowMillis(): Long

expect fun clockText(ms: Long): String

expect fun fileExists(path: String): Boolean

expect fun <T> locked(lock: Any, block: () -> T): T

expect fun monoNanos(): Long

expect fun utcOffsetMinutes(): Int

expect fun isUnreachable(e: Throwable): Boolean

expect val IoDispatcher: kotlinx.coroutines.CoroutineDispatcher

expect fun isNetworkFailure(e: Throwable): Boolean
