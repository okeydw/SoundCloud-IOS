package com.scd.android

import androidx.compose.runtime.mutableStateMapOf

object PlaylistCovers {
    private var sp: KvStore? = null
    private var dir: String? = null
    private val map = mutableStateMapOf<String, String>()

    fun init(store: KvStore, folder: String) {
        if (sp != null) return
        sp = store
        dir = folder.also { Fs.mkdirs(it) }
        store.all().forEach { (k, v) ->
            val path = v as? String ?: return@forEach
            if (Fs.exists(path)) map[k] = path
        }
    }

    fun get(urn: String): String? = map[urn]

    fun has(urn: String): Boolean = map.containsKey(urn)

    private fun target(urn: String): String? {
        val folder = dir ?: return null
        Fs.mkdirs(folder)
        val name = urn.replace(Regex("[^A-Za-z0-9_-]"), "_").takeLast(80)
        return "$folder/${name}_${nowMillis()}.jpg"
    }

    fun setBytes(urn: String, jpeg: ByteArray): Boolean = runCatching {
        if (jpeg.isEmpty()) return@runCatching false
        val path = target(urn) ?: return@runCatching false
        Fs.writeAtomic(path, jpeg)
        if (!Fs.exists(path)) return@runCatching false
        map[urn]?.let { old -> if (old != path) Fs.delete(old) }
        map[urn] = path
        sp?.edit()?.putString(urn, path)?.apply()
        true
    }.getOrDefault(false)

    fun clear(urn: String) {
        map.remove(urn)?.let { Fs.delete(it) }
        sp?.edit()?.remove(urn)?.apply()
    }

    fun move(from: String, to: String) {
        val path = map.remove(from) ?: return
        map[to] = path
        sp?.edit()?.remove(from)?.putString(to, path)?.apply()
    }
}
