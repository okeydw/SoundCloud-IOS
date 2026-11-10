package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.withContext
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

object TrackExport {
    class Outcome(val exported: Int, val skipped: Int, val failed: Int)

    var running by mutableStateOf(false)
        private set

    private fun folder(): String {
        val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String
        return "${docs ?: Dirs.files}/SoundCloud".also { Fs.mkdirs(it) }
    }

    private fun plainName(raw: String, urn: String): String {
        val cleaned = raw.map { c -> if (c.isISOControl() || c in "\\/:*?\"<>|") '_' else c }
            .joinToString("")
            .trim()
            .trim('.')
            .take(120)
        return cleaned.ifBlank { urn.substringAfterLast(':') }
    }

    suspend fun exportAll(): Outcome {
        if (running) return Outcome(0, 0, 0)
        running = true
        return try {
            withContext(IoDispatcher) {
                val dir = folder()
                var exported = 0
                var skipped = 0
                var failed = 0
                for (track in Downloads.tracks()) {
                    val source = Downloads.localPath(track.urn)?.takeIf { Fs.exists(it) } ?: continue
                    val artist = track.user?.username.orEmpty()
                    val ext = source.substringAfterLast('.', "m4a").let { if (it == "audio") "m4a" else it }
                    val name = plainName(if (artist.isBlank()) track.title else "$artist - ${track.title}", track.urn) + ".$ext"
                    val target = "$dir/$name"
                    if (Fs.exists(target)) {
                        skipped++
                        continue
                    }
                    val bytes = Fs.readAtomic(source)
                    if (bytes == null) {
                        failed++
                        continue
                    }
                    runCatching { Fs.writeAtomic(target, bytes) }.onSuccess { exported++ }.onFailure { failed++ }
                }
                Logs.add("download", "export to Files/SoundCloud: $exported new, $skipped already there, $failed failed")
                Outcome(exported, skipped, failed)
            }
        } finally {
            running = false
        }
    }
}
