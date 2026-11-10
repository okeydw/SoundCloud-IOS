package com.scd.android

import kotlinx.cinterop.*
import platform.AVFoundation.*
import platform.CoreFoundation.CFRelease
import platform.MediaToolbox.*

@OptIn(ExperimentalForeignApi::class)
object IosEq {
    private const val FLAG_FLOAT = 1u
    private const val FLAG_NON_INTERLEAVED = 32u
    private const val TAP_POST_EFFECTS = 2u

    internal class TapState {
        val dsp = EqDsp()
        var ready = false
        var float32 = false
        var nonInterleaved = false
        var channels = 2
        val channelPtrs = arrayOfNulls<CPointer<FloatVar>>(8)
    }

    fun attach(item: AVPlayerItem) {
        runCatching {
            val track = item.asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack ?: return
            val tap = create() ?: return
            try {
                val params = AVMutableAudioMixInputParameters()
                params.setTrackID(track.trackID)
                params.setAudioTapProcessor(tap)
                val mix = AVMutableAudioMix()
                mix.setInputParameters(listOf(params))
                item.audioMix = mix
            } finally {
                CFRelease(tap)
            }
        }.onFailure { Logs.add("eq", "tap attach failed: ${it::class.simpleName}") }
    }

    private fun create(): MTAudioProcessingTapRef? = memScoped {
        val state = StableRef.create(TapState())
        val callbacks = alloc<MTAudioProcessingTapCallbacks>()
        callbacks.version = 0
        callbacks.clientInfo = state.asCPointer()
        callbacks.`init` = staticCFunction(::tapInit).reinterpret()
        callbacks.finalize = staticCFunction(::tapFinalize).reinterpret()
        callbacks.prepare = staticCFunction(::tapPrepare).reinterpret()
        callbacks.unprepare = staticCFunction(::tapUnprepare).reinterpret()
        callbacks.process = staticCFunction(::tapProcess).reinterpret()
        val out = alloc<MTAudioProcessingTapRefVar>()
        out.value = null
        val status = MTAudioProcessingTapCreate(null, callbacks.ptr, TAP_POST_EFFECTS, out.ptr)
        val tap = out.value
        if (status != 0 || tap == null) {
            state.dispose()
            Logs.add("eq", "tap create failed: $status")
            null
        } else {
            tap
        }
    }

    internal fun stateOf(tap: COpaquePointer?): TapState? {
        if (tap == null) return null
        val storage = MTAudioProcessingTapGetStorage(tap.reinterpret()) ?: return null
        return storage.asStableRef<TapState>().get()
    }

    private fun u32(base: COpaquePointer, offset: Long): UInt =
        interpretCPointer<UIntVar>(base.rawValue + offset)!!.pointed.value

    internal fun prepare(state: TapState, format: COpaquePointer) {
        state.ready = false
        val rate = interpretCPointer<DoubleVar>(format.rawValue)!!.pointed.value
        if (!rate.isFinite() || rate < 8_000 || rate > 384_000) return
        if (u32(format, 8) != 0x6c70636du) return
        val flags = u32(format, 12)
        val channels = u32(format, 28).toInt()
        val bits = u32(format, 32).toInt()
        state.float32 = (flags and FLAG_FLOAT) != 0u && bits == 32
        state.nonInterleaved = (flags and FLAG_NON_INTERLEAVED) != 0u
        state.channels = channels.coerceIn(1, 8)
        state.dsp.configure(rate.toInt().coerceAtLeast(8_000), state.channels)
        state.ready = state.float32 && channels in 1..8
    }

    internal fun process(state: TapState, list: COpaquePointer, frames: Int) {
        if (!state.ready || frames <= 0) return
        val buffers = u32(list, 0).toInt()
        if (buffers <= 0) return
        fun fits(index: Int, channels: Int): Boolean {
            val offset = 8L + index * 16L
            return u32(list, offset) == channels.toUInt() &&
                u32(list, offset + 4L).toULong() >= frames.toULong() * channels.toULong() * 4uL
        }
        fun data(index: Int): CPointer<FloatVar>? =
            interpretCPointer<COpaquePointerVar>(list.rawValue + 8L + index * 16L + 8L)!!.pointed.value?.reinterpret()
        val dsp = state.dsp
        if (state.nonInterleaved) {
            val ch = state.channels
            if (buffers < ch) return
            for (c in 0 until ch) {
                if (!fits(c, 1)) return
                state.channelPtrs[c] = data(c) ?: return
            }
            for (i in 0 until frames) {
                dsp.beginFrame()
                if (dsp.bypass) continue
                for (c in 0 until ch) {
                    val p = state.channelPtrs[c] ?: continue
                    p[i] = dsp.process(c, p[i].toDouble()).toFloat()
                }
            }
        } else {
            val ch = state.channels
            if (!fits(0, ch)) return
            val p = data(0) ?: return
            var at = 0
            for (i in 0 until frames) {
                dsp.beginFrame()
                if (dsp.bypass) {
                    at += ch
                    continue
                }
                for (c in 0 until ch) {
                    p[at] = dsp.process(c, p[at].toDouble()).toFloat()
                    at++
                }
            }
        }
    }
}


@OptIn(ExperimentalForeignApi::class)
private fun tapInit(tap: COpaquePointer?, clientInfo: COpaquePointer?, storageOut: COpaquePointer?) {
    storageOut?.reinterpret<COpaquePointerVar>()?.pointed?.value = clientInfo
}

@OptIn(ExperimentalForeignApi::class)
private fun tapFinalize(tap: COpaquePointer?) {
    runCatching {
        val storage = tap?.let { MTAudioProcessingTapGetStorage(it.reinterpret()) } ?: return
        storage.asStableRef<IosEq.TapState>().dispose()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun tapPrepare(tap: COpaquePointer?, maxFrames: Long, format: COpaquePointer?) {
    runCatching {
        val state = IosEq.stateOf(tap) ?: return
        IosEq.prepare(state, format ?: return)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun tapUnprepare(tap: COpaquePointer?) {
    runCatching { IosEq.stateOf(tap)?.ready = false }
}

@OptIn(ExperimentalForeignApi::class)
private fun tapProcess(
    tap: COpaquePointer?,
    numberFrames: Long,
    flags: UInt,
    bufferList: COpaquePointer?,
    numberFramesOut: COpaquePointer?,
    flagsOut: COpaquePointer?,
) {
    if (tap == null || bufferList == null || numberFramesOut == null) return
    numberFramesOut.reinterpret<LongVar>().pointed.value = 0L
    val status = MTAudioProcessingTapGetSourceAudio(
        tap.reinterpret(),
        numberFrames,
        bufferList.reinterpret(),
        flagsOut?.reinterpret(),
        null,
        numberFramesOut.reinterpret(),
    )
    if (status != 0) return
    runCatching {
        val frames = numberFramesOut.reinterpret<LongVar>().pointed.value.toInt()
        val state = IosEq.stateOf(tap) ?: return
        IosEq.process(state, bufferList, frames)
    }.onFailure {
        runCatching { IosEq.stateOf(tap)?.let { state -> state.ready = false; state.dsp.reset() } }
    }
}
