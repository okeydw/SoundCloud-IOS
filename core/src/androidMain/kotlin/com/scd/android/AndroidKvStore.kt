package com.scd.android

import android.content.Context
import android.content.SharedPreferences

class AndroidKvStore(private val sp: SharedPreferences) : KvStore {
    constructor(context: Context, name: String) : this(context.getSharedPreferences(name, Context.MODE_PRIVATE))

    override fun getString(key: String, def: String?): String? = sp.getString(key, def)
    override fun getBoolean(key: String, def: Boolean): Boolean = sp.getBoolean(key, def)
    override fun getInt(key: String, def: Int): Int = sp.getInt(key, def)
    override fun getLong(key: String, def: Long): Long = sp.getLong(key, def)
    override fun getFloat(key: String, def: Float): Float = sp.getFloat(key, def)
    override fun getStringSet(key: String, def: Set<String>?): Set<String>? = sp.getStringSet(key, def)
    override fun contains(key: String): Boolean = sp.contains(key)
    override fun all(): Map<String, Any?> = sp.all
    override fun edit(): KvEditor = Editor(sp.edit())

    private class Editor(private val e: SharedPreferences.Editor) : KvEditor {
        override fun putString(key: String, value: String?): KvEditor = apply { e.putString(key, value) }
        override fun putBoolean(key: String, value: Boolean): KvEditor = apply { e.putBoolean(key, value) }
        override fun putInt(key: String, value: Int): KvEditor = apply { e.putInt(key, value) }
        override fun putLong(key: String, value: Long): KvEditor = apply { e.putLong(key, value) }
        override fun putFloat(key: String, value: Float): KvEditor = apply { e.putFloat(key, value) }
        override fun putStringSet(key: String, value: Set<String>?): KvEditor = apply { e.putStringSet(key, value) }
        override fun remove(key: String): KvEditor = apply { e.remove(key) }
        override fun clear(): KvEditor = apply { e.clear() }
        override fun apply() = e.apply()
    }
}
