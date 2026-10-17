package com.scd.android

import platform.Foundation.NSUserDefaults

class IosKvStore(private val name: String) : KvStore {
    private val d = NSUserDefaults.standardUserDefaults
    private val prefix = "$name."

    private fun k(key: String) = prefix + key

    override fun getString(key: String, def: String?): String? = d.stringForKey(k(key)) ?: def

    override fun getBoolean(key: String, def: Boolean): Boolean =
        if (d.objectForKey(k(key)) == null) def else d.boolForKey(k(key))

    override fun getInt(key: String, def: Int): Int =
        if (d.objectForKey(k(key)) == null) def else d.integerForKey(k(key)).toInt()

    override fun getLong(key: String, def: Long): Long =
        if (d.objectForKey(k(key)) == null) def else d.integerForKey(k(key)).toLong()

    override fun getFloat(key: String, def: Float): Float =
        if (d.objectForKey(k(key)) == null) def else d.floatForKey(k(key))

    override fun getStringSet(key: String, def: Set<String>?): Set<String>? =
        d.arrayForKey(k(key))?.filterIsInstance<String>()?.toSet() ?: def

    override fun contains(key: String): Boolean = d.objectForKey(k(key)) != null

    override fun all(): Map<String, Any?> =
        d.dictionaryRepresentation().entries
            .mapNotNull { (key, value) ->
                val s = key as? String ?: return@mapNotNull null
                if (!s.startsWith(prefix)) null else s.removePrefix(prefix) to value
            }
            .toMap()

    override fun edit(): KvEditor = Editor()

    private inner class Editor : KvEditor {
        private val ops = mutableListOf<() -> Unit>()
        private var wipe = false

        override fun putString(key: String, value: String?): KvEditor = apply {
            ops += { if (value == null) d.removeObjectForKey(k(key)) else d.setObject(value, k(key)) }
        }

        override fun putBoolean(key: String, value: Boolean): KvEditor = apply { ops += { d.setBool(value, k(key)) } }
        override fun putInt(key: String, value: Int): KvEditor = apply { ops += { d.setInteger(value.toLong(), k(key)) } }
        override fun putLong(key: String, value: Long): KvEditor = apply { ops += { d.setInteger(value, k(key)) } }
        override fun putFloat(key: String, value: Float): KvEditor = apply { ops += { d.setFloat(value, k(key)) } }

        override fun putStringSet(key: String, value: Set<String>?): KvEditor = apply {
            ops += { if (value == null) d.removeObjectForKey(k(key)) else d.setObject(value.toList(), k(key)) }
        }

        override fun remove(key: String): KvEditor = apply { ops += { d.removeObjectForKey(k(key)) } }
        override fun clear(): KvEditor = apply { wipe = true }

        override fun apply() {
            if (wipe) all().keys.forEach { d.removeObjectForKey(k(it)) }
            ops.forEach { it() }
        }
    }
}
