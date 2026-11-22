package com.scd.android

import kotlinx.cinterop.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.AVFAudio.*
import platform.Foundation.NSURL
import kotlin.math.sqrt

@OptIn(ExperimentalForeignApi::class)
internal suspend fun localWaveform(urn: String): List<Float>? = withContext(IoDispatcher) {
    val path = Downloads.localPath(urn) ?: StreamFiles.cached(urn) ?: return@withContext null
    val file = AVAudioFile(forReading = NSURL.fileURLWithPath(path), commonFormat = AVAudioPCMFormatFloat32, interleaved = false, error = null)
    val length = file.length
    if (length <= 0) return@withContext null
    val buffer = AVAudioPCMBuffer(pCMFormat = file.processingFormat, frameCapacity = 8192u)
    val sums = DoubleArray(144)
    val counts = IntArray(sums.size)
    var position = 0L
    while (position < length) {
        currentCoroutineContext().ensureActive()
        if (!file.readIntoBuffer(buffer, frameCount = minOf(8192L, length - position).toUInt(), error = null)) return@withContext null
        val frames = buffer.frameLength.toInt()
        if (frames == 0) break
        val channels = buffer.floatChannelData ?: return@withContext null
        for (c in 0 until file.processingFormat.channelCount.toInt()) {
            val samples = channels[c] ?: continue
            for (i in 0 until frames step 8) {
                val bin = (((position + i).toDouble() / length) * sums.size).toInt().coerceIn(sums.indices)
                val value = samples[i].toDouble()
                sums[bin] += value * value
                counts[bin]++
            }
        }
        position += frames
    }
    if (position == 0L) return@withContext null
    val peaks = sums.indices.map { sqrt(sums[it] / counts[it].coerceAtLeast(1)).toFloat() }
    val top = peaks.max().coerceAtLeast(0.001f)
    peaks.map { (it / top).coerceIn(0f, 1f) }
}
