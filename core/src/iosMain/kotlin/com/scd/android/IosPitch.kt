package com.scd.android

import kotlinx.cinterop.*
import platform.AudioToolbox.*
import platform.CoreAudioTypes.*
import platform.MediaToolbox.*
import platform.posix.memset
import kotlin.math.ceil
import kotlin.concurrent.Volatile

@OptIn(ExperimentalForeignApi::class)
internal object IosPitch {
    @Volatile var cents = 0f
    @Volatile var failure = 0
}

@OptIn(ExperimentalForeignApi::class)
internal class IosPitchUnit private constructor(
    private val tap: MTAudioProcessingTapRef,
    private val format: CPointer<AudioStreamBasicDescription>,
    private val maxFrames: Int,
) {
    private val arena = Arena()
    private var unit: AudioUnit? = null
    private var stable: StableRef<IosPitchUnit>? = null
    private val stamp = arena.alloc<AudioTimeStamp>()
    private val renderFlags = arena.alloc<UIntVar>()
    private val sourceFlags = arena.alloc<UIntVar>()
    private val sourceCount = arena.alloc<LongVar>()
    private val channels = format.pointed.mChannelsPerFrame.toInt()
    private val planar = format.pointed.mFormatFlags and kAudioFormatFlagIsNonInterleaved != 0u
    private val buffers = if (planar) channels else 1
    private val channelsPerBuffer = if (planar) 1 else channels
    private val bytesPerFrame = channelsPerBuffer * 4
    private val input = Array(buffers) { arena.allocArray<ByteVar>(maxFrames * bytesPerFrame) }
    private val output = Array(buffers) { arena.allocArray<ByteVar>(maxFrames * bytesPerFrame) }
    private val scratch = arena.allocArray<ByteVar>(8 + 16 * buffers).reinterpret<AudioBufferList>()
    private var latency = 0
    private var priming = 0
    private var sourceEnded = false
    private var sourceFrames = 0L
    private var returnedFrames = 0L
    private var sampleTime = 0.0
    private var appliedCents = Float.NaN
    private var disposed = false

    companion object {
        fun create(tap: MTAudioProcessingTapRef, format: CPointer<AudioStreamBasicDescription>, maxFrames: Long): IosPitchUnit? {
            if (maxFrames !in 1L..65_536L || format.pointed.mChannelsPerFrame !in 1u..8u) return null
            val pitch = IosPitchUnit(tap, format, maxFrames.toInt())
            return try {
                pitch.initialize()
                pitch
            } catch (_: Exception) {
                pitch.dispose()
                IosPitch.failure = -1
                null
            }
        }
    }

    private fun check(status: Int) {
        check(status == 0) { "AudioUnit $status" }
    }

    private fun initialize() = memScoped {
        val description = alloc<AudioComponentDescription>()
        description.componentType = kAudioUnitType_FormatConverter
        description.componentSubType = kAudioUnitSubType_NewTimePitch
        description.componentManufacturer = kAudioUnitManufacturer_Apple
        description.componentFlags = 0u
        description.componentFlagsMask = 0u
        val component = AudioComponentFindNext(null, description.ptr) ?: error("NewTimePitch unavailable")
        val instance = alloc<AudioComponentInstanceVar>()
        instance.value = null
        check(AudioComponentInstanceNew(component, instance.ptr))
        unit = instance.value ?: error("No audio unit")
        for (scope in listOf(kAudioUnitScope_Input, kAudioUnitScope_Output)) {
            check(AudioUnitSetProperty(unit, kAudioUnitProperty_StreamFormat, scope, 0u, format, sizeOf<AudioStreamBasicDescription>().toUInt()))
        }
        stable = StableRef.create(this@IosPitchUnit)
        val callback = alloc<AURenderCallbackStruct>()
        callback.inputProc = staticCFunction(::pitchInput)
        callback.inputProcRefCon = stable!!.asCPointer()
        check(AudioUnitSetProperty(unit, kAudioUnitProperty_SetRenderCallback, kAudioUnitScope_Input, 0u, callback.ptr, sizeOf<AURenderCallbackStruct>().toUInt()))
        val maximum = alloc<UIntVar>()
        maximum.value = maxFrames.toUInt()
        check(AudioUnitSetProperty(unit, kAudioUnitProperty_MaximumFramesPerSlice, kAudioUnitScope_Global, 0u, maximum.ptr, sizeOf<UIntVar>().toUInt()))
        check(AudioUnitSetParameter(unit, kNewTimePitchParam_Rate, kAudioUnitScope_Global, 0u, 1f, 0u))
        check(AudioUnitSetParameter(unit, kNewTimePitchParam_Pitch, kAudioUnitScope_Global, 0u, IosPitch.cents, 0u))
        check(AudioUnitInitialize(unit))
        val seconds = alloc<DoubleVar>()
        val size = alloc<UIntVar>()
        size.value = sizeOf<DoubleVar>().toUInt()
        check(AudioUnitGetProperty(unit, kAudioUnitProperty_Latency, kAudioUnitScope_Global, 0u, seconds.ptr, size.ptr))
        check(seconds.value.isFinite() && seconds.value in 0.0..1.0)
        latency = ceil(seconds.value * format.pointed.mSampleRate).toInt()
        memset(stamp.ptr, 0, sizeOf<AudioTimeStamp>().toULong())
        stamp.mFlags = kAudioTimeStampSampleTimeValid
        reset()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        unit?.let { AudioUnitUninitialize(it); AudioComponentInstanceDispose(it) }
        unit = null
        stable?.dispose()
        stable = null
        arena.clear()
    }

    private fun reset() {
        check(AudioUnitReset(unit, kAudioUnitScope_Global, 0u))
        priming = latency
        sourceEnded = false
        sourceFrames = 0L
        returnedFrames = 0L
        sampleTime = 0.0
    }

    private fun buffer(list: CPointer<AudioBufferList>, index: Int): AudioBuffer =
        interpretCPointer<AudioBuffer>(list.rawValue + 8L + index * sizeOf<AudioBuffer>())!!.pointed

    private fun prepareList(list: CPointer<AudioBufferList>, data: Array<CPointer<ByteVar>>, frames: Int) {
        list.pointed.mNumberBuffers = buffers.toUInt()
        for (i in 0 until buffers) {
            val b = buffer(list, i)
            b.mNumberChannels = channelsPerBuffer.toUInt()
            b.mDataByteSize = (frames * bytesPerFrame).toUInt()
            b.mData = data[i]
        }
    }

    internal fun supply(frames: Int, list: CPointer<AudioBufferList>): Int {
        if (disposed || frames !in 1..maxFrames) return -10874
        prepareList(list, input, frames)
        sourceFlags.value = 0u
        sourceCount.value = 0L
        if (!sourceEnded) {
            val status = MTAudioProcessingTapGetSourceAudio(tap, frames.toLong(), list, sourceFlags.ptr, null, sourceCount.ptr)
            if (status != 0) return status
            if (sourceFlags.value and kMTAudioProcessingTapFlag_EndOfStream != 0u) sourceEnded = true
        }
        val received = sourceCount.value.toInt().coerceIn(0, frames)
        sourceFrames += received
        for (i in 0 until buffers) {
            memset(input[i] + received * bytesPerFrame, 0, ((frames - received) * bytesPerFrame).toULong())
            buffer(list, i).mDataByteSize = (frames * bytesPerFrame).toUInt()
        }
        return 0
    }

    private fun render(frames: Int, list: CPointer<AudioBufferList>) {
        prepareList(list, output, frames)
        stamp.mSampleTime = sampleTime
        renderFlags.value = 0u
        check(AudioUnitRender(unit, renderFlags.ptr, stamp.ptr, 0u, frames.toUInt(), list))
        sampleTime += frames
    }

    fun process(frames: Int, flags: UInt, list: CPointer<AudioBufferList>, framesOut: CPointer<LongVar>, flagsOut: CPointer<UIntVar>?) {
        check(!disposed && frames in 1..maxFrames)
        if (flags and kMTAudioProcessingTapFlag_StartOfStream != 0u) reset()
        val cents = IosPitch.cents
        if (cents != appliedCents) {
            check(AudioUnitSetParameter(unit, kNewTimePitchParam_Pitch, kAudioUnitScope_Global, 0u, cents, 0u))
            appliedCents = cents
        }
        while (priming > 0) {
            val count = minOf(priming, maxFrames)
            render(count, scratch)
            priming -= count
        }
        render(frames, list)
        val count = if (sourceEnded) minOf(frames.toLong(), (sourceFrames - returnedFrames).coerceAtLeast(0L)).toInt() else frames
        returnedFrames += count
        framesOut.pointed.value = count.toLong()
        flagsOut?.pointed?.value = (flags and kMTAudioProcessingTapFlag_StartOfStream) or
            (if (sourceEnded && returnedFrames >= sourceFrames) kMTAudioProcessingTapFlag_EndOfStream else 0u)
        for (i in 0 until buffers) buffer(list, i).mDataByteSize = (count * bytesPerFrame).toUInt()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun pitchInput(
    ref: COpaquePointer?, flags: CPointer<UIntVar>?, stamp: CPointer<AudioTimeStamp>?,
    bus: UInt, frames: UInt, list: CPointer<AudioBufferList>?,
): Int {
    if (ref == null || list == null) return -50
    return try {
        ref.asStableRef<IosPitchUnit>().get().supply(frames.toInt(), list)
    } catch (_: Exception) {
        -50
    }
}
