package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

object IosIntegrity {
    var running by mutableStateOf(false)
        private set
    var done by mutableStateOf(0)
        private set
    var total by mutableStateOf(0)
        private set
    var report by mutableStateOf<RepairProgress?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    private var job: Job? = null

    fun start() {
        if (running) return
        running = true
        done = 0
        total = 0
        failed = false
        report = null
        job = AppScope.scope.launch(Dispatchers.Main) {
            var progress = RepairProgress()
            try {
                val downloads = Downloads.tracks()
                val cached = withContext(IoDispatcher) { StreamFiles.cachedTracks() }
                total = downloads.size + cached.size
                suspend fun check(track: Track, saved: Boolean) {
                    currentCoroutineContext().ensureActive()
                    val outcome = try {
                        if (track.urn == NowPlaying.urn || track.urn in Downloads.inProgress) RepairOutcome.REMAINING
                        else if (saved) {
                            var repaired = false
                            val path = Downloads.playablePath(track, forceCheck = true) { repaired = true }
                            when { path == null -> RepairOutcome.REMAINING; repaired -> RepairOutcome.REPAIRED; else -> RepairOutcome.CLEAN }
                        } else StreamFiles.repairCached(track)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logs.add("cache", "check ${track.urn}: ${e::class.simpleName}")
                        RepairOutcome.REMAINING
                    }
                    progress = progress.add(outcome)
                    done = progress.checked
                }
                downloads.forEach { check(it, true) }
                cached.forEach { check(it, false) }
                report = progress
                Logs.add("cache", "checked ${progress.checked}, repaired ${progress.repaired}, remaining ${progress.remaining}")
            } catch (e: CancellationException) {
                Logs.add("cache", "check cancelled after $done of $total")
                throw e
            } catch (e: Exception) {
                failed = true
                Logs.add("cache", "check failed: ${e::class.simpleName}")
            } finally { running = false }
        }
    }

    fun cancel() { job?.cancel() }
}
