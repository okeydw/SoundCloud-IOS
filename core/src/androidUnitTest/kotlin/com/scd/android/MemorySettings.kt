package com.scd.android

internal class MemorySettings : KvStore, KvEditor {
    private val entries = mutableMapOf<String, Any?>()
    override fun getString(key: String, def: String?) = entries[key] as? String ?: def
    override fun getBoolean(key: String, def: Boolean) = entries[key] as? Boolean ?: def
    override fun getInt(key: String, def: Int) = entries[key] as? Int ?: def
    override fun getLong(key: String, def: Long) = entries[key] as? Long ?: def
    override fun getFloat(key: String, def: Float) = entries[key] as? Float ?: def
    override fun getStringSet(key: String, def: Set<String>?) = (entries[key] as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: def
    override fun contains(key: String) = key in entries
    override fun all() = entries.toMap()
    override fun edit(): KvEditor = this
    private fun put(key: String, value: Any?): KvEditor { entries[key] = value; return this }
    override fun putString(key: String, value: String?) = put(key, value)
    override fun putBoolean(key: String, value: Boolean) = put(key, value)
    override fun putInt(key: String, value: Int) = put(key, value)
    override fun putLong(key: String, value: Long) = put(key, value)
    override fun putFloat(key: String, value: Float) = put(key, value)
    override fun putStringSet(key: String, value: Set<String>?) = put(key, value)
    override fun remove(key: String): KvEditor { entries.remove(key); return this }
    override fun clear(): KvEditor { entries.clear(); return this }
    override fun apply() {}
}
