package com.scd.android

internal object AudioCompleteness {
    fun rejection(actualMs: Long?, bytes: Int, expectedMs: Long): String? {
        if (expectedMs <= 45_000L) return null
        val tolerance = maxOf(4_000L, (expectedMs * 0.04).toLong())
        if (actualMs != null && actualMs > 0L && actualMs < expectedMs - tolerance) {
            return "short ${actualMs / 1000}s of ${expectedMs / 1000}s"
        }
        return if (bytes > 0 && bytes.toDouble() * 8000 / expectedMs < 24_000) "too little audio for ${expectedMs / 1000}s" else null
    }
}
