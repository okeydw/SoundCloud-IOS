package com.scd.android

object DownloadQuality {
    fun restore(saved: String?, legacyHq: Boolean): String = when (saved) {
        Prefs.QUALITY_AUTO, Prefs.QUALITY_SQ, Prefs.QUALITY_HQ, Prefs.QUALITY_UQ -> saved
        else -> if (legacyHq) Prefs.QUALITY_AUTO else Prefs.QUALITY_SQ
    }

    fun wanted(setting: String, star: Boolean): Quality = when (setting) {
        Prefs.QUALITY_SQ -> Quality.SQ
        Prefs.QUALITY_HQ -> Quality.HQ
        else -> if (star) Quality.UQ else Quality.HQ
    }
}
