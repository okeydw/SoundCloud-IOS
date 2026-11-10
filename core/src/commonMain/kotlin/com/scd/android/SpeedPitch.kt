package com.scd.android

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

object SpeedPitch {
    const val RATE_MIN = 0.5f
    const val RATE_MAX = 2.0f
    const val RATE_STEP = 0.05f
    const val RATE_DEFAULT = 1.0f

    const val SEMI_MIN = -12f
    const val SEMI_MAX = 12f
    const val SEMI_STEP = 0.5f

    const val MODE_AUTO = "auto"
    const val MODE_MANUAL = "manual"

    var manualPitchSupported = true

    fun clampRate(rate: Float): Float {
        if (!rate.isFinite()) return RATE_DEFAULT
        return (rate.coerceIn(RATE_MIN, RATE_MAX) * 100f).roundToInt() / 100f
    }

    fun clampSemitones(semi: Float): Float {
        if (!semi.isFinite()) return 0f
        return (semi.coerceIn(SEMI_MIN, SEMI_MAX) * 2f).roundToInt() / 2f
    }

    fun effectiveSemitones(rate: Float, mode: String, manual: Float): Float =
        if (mode == MODE_MANUAL) {
            if (manualPitchSupported) clampSemitones(manual) else 0f
        } else {
            clampSemitones((ln(maxOf(0.01f, rate).toDouble()) / ln(2.0) * 12.0).toFloat())
        }

    fun pitchRatio(rate: Float = Prefs.playbackRate, mode: String = Prefs.pitchMode, manual: Float = Prefs.pitchSemitones): Float =
        if (mode == MODE_MANUAL) 2.0.pow(effectiveSemitones(rate, mode, manual) / 12.0).toFloat() else clampRate(rate)

    val active: Boolean
        get() = abs(Prefs.playbackRate - RATE_DEFAULT) > 0.001f ||
            (Prefs.pitchMode == MODE_MANUAL && Prefs.pitchSemitones != 0f && manualPitchSupported)

    fun rateLabel(rate: Float): String {
        val hundredths = (clampRate(rate) * 100f).roundToInt()
        return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}×"
    }

    fun semitoneLabel(semi: Float): String {
        val halves = (clampSemitones(semi) * 2f).roundToInt()
        val sign = when {
            halves > 0 -> "+"
            halves < 0 -> "-"
            else -> ""
        }
        val a = abs(halves)
        return sign + if (a % 2 == 0) "${a / 2}" else "${a / 2}.5"
    }
}
