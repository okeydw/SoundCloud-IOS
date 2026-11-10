package com.scd.android

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore

object TrackTurns {
    enum class Priority { PLAYBACK, BACKGROUND, BULK }

    private val hq = Semaphore(1)
    private val lock = Any()
    private var waiting = 0

    private fun waiting(): Int = locked(lock) { waiting }

    suspend fun <T> withTurn(priority: Priority, block: suspend () -> T): T {
        if (priority == Priority.PLAYBACK || Prefs.star) return block()
        if (priority == Priority.BACKGROUND) {
            locked(lock) { waiting++ }
            try {
                hq.acquire()
            } finally {
                locked(lock) { waiting-- }
            }
        } else {
            while (true) {
                while (waiting() > 0) delay(250L)
                hq.acquire()
                if (waiting() == 0) break
                hq.release()
                delay(50L)
            }
        }
        try {
            return block()
        } finally {
            hq.release()
        }
    }
}
