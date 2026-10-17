package com.scd.android

object WaveCache {
    private val map = SyncMap<String, List<Float>>()

    fun get(urn: String?): List<Float>? = urn?.let { map[it] }

    fun put(urn: String, samples: List<Float>) {
        if (samples.isEmpty()) return
        if (map.size > 300) map.clear()
        map[urn] = samples
    }

    fun clear() = map.clear()
}
