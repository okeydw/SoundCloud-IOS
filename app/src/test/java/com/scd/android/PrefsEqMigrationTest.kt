package com.scd.android

import org.junit.Assert.*
import org.junit.Test

class PrefsEqMigrationTest {
    @Test
    fun legacyPresetSurvivesMigrationAndPublishesToAudio() {
        val store = MemoryPrefs(mutableMapOf(
            "eq_enabled" to true,
            "eq_preset" to -1,
            "eq_bands" to "600,300,0,-200,-400",
            "eq_user" to """[{"name":"My sound","bands":"600,300,0,-200,-400"}]""",
            "eq_hidden" to "0,1",
            "car_eq" to "user:My sound",
            "car_scenarios" to """[{"id":"car","eq":"user:My sound"}]""",
            "star" to true,
            "hq_streaming" to false,
            "haptics" to false,
        ))

        Prefs.init(store)

        val custom = EqPresets.custom.single()
        assertEquals("My sound", custom.name)
        assertEquals(custom.id, Prefs.eqPresetId)
        assertEquals(custom.gains, Prefs.eqGains)
        assertEquals(10, Prefs.eqGains.size)
        assertTrue(Prefs.eqGains.any { kotlin.math.abs(it) > 0.1 })
        assertEquals(EqPresets.customKey(custom.id), Prefs.carEq)
        assertEquals(Prefs.carEq, CarScenarios.all.single().eq)
        assertTrue(EqLive.params.enabled)
        assertArrayEquals(Prefs.eqGains.toDoubleArray(), EqLive.params.gains, 0.0)
        assertEquals(Prefs.QUALITY_SQ, Prefs.streamQuality)
        assertFalse(Prefs.haptics)
        listOf("eq_preset", "eq_bands", "eq_user", "eq_hidden").forEach { assertFalse(store.contains(it)) }

        Prefs.changeEqCurve(List(10) { if (it == 0) 6.0 else 0.0 }, EqPresets.CUSTOM, -4.0)
        assertEquals("-4.0", store.getString("eq_preamp", null))
        assertEquals(-4.0, EqLive.params.preamp, 0.0)
        assertEquals(6.0, EqLive.params.gains[0], 0.0)
        Prefs.changeEqEnabled(false)
        assertFalse(EqLive.params.enabled)
        assertFalse(store.getBoolean("eq_enabled", true))
        Prefs.changePlaybackRate(Float.NaN)
        assertEquals(1f, Prefs.playbackRate, 0f)
        Prefs.changeStreamQuality(Prefs.QUALITY_HQ)
        assertEquals("hq", store.getString("stream_quality", null))
        assertTrue(Prefs.hqStreaming)
    }
}

private class MemoryPrefs(private val values: MutableMap<String, Any?>) : KvStore {
    override fun getString(key: String, def: String?) = values[key] as? String ?: def
    override fun getBoolean(key: String, def: Boolean) = values[key] as? Boolean ?: def
    override fun getInt(key: String, def: Int) = values[key] as? Int ?: def
    override fun getLong(key: String, def: Long) = values[key] as? Long ?: def
    override fun getFloat(key: String, def: Float) = values[key] as? Float ?: def
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, def: Set<String>?) = values[key] as? Set<String> ?: def
    override fun contains(key: String) = values.containsKey(key)
    override fun all() = values.toMap()
    override fun edit(): KvEditor = object : KvEditor {
        private val updates = mutableMapOf<String, Any?>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { updates[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { updates[key] = value }
        override fun putInt(key: String, value: Int) = apply { updates[key] = value }
        override fun putLong(key: String, value: Long) = apply { updates[key] = value }
        override fun putFloat(key: String, value: Float) = apply { updates[key] = value }
        override fun putStringSet(key: String, value: Set<String>?) = apply { updates[key] = value }
        override fun remove(key: String) = apply { updates[key] = null }
        override fun clear() = apply { clear = true }
        override fun apply() {
            if (clear) values.clear()
            for ((key, value) in updates) {
                if (value == null) values.remove(key) else values[key] = value
            }
        }
    }
}
