package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.concurrent.Volatile

object SessionState {

    var expired by mutableStateOf(false)
        private set
    var fromStream by mutableStateOf(false)
        private set

    @Volatile
    private var lastStreamCheck = 0L

    @Volatile
    var lastRefreshAt = 0L

    fun markExpired() {
        if (Api.sessionId != null) expired = true
    }

    fun streamDenied() {
        if (Api.sessionId == null) return
        val now = nowMillis()
        if (now - lastStreamCheck < 60_000L) return
        lastStreamCheck = now
        fromStream = true
        expired = true
    }

    fun consume() {
        expired = false
        fromStream = false
    }
}
