package com.scd.android

interface KvStore {
    fun getString(key: String, def: String?): String?
    fun getBoolean(key: String, def: Boolean): Boolean
    fun getInt(key: String, def: Int): Int
    fun getLong(key: String, def: Long): Long
    fun getFloat(key: String, def: Float): Float
    fun getStringSet(key: String, def: Set<String>?): Set<String>?
    fun contains(key: String): Boolean
    fun all(): Map<String, Any?>
    fun edit(): KvEditor
}

interface KvEditor {
    fun putString(key: String, value: String?): KvEditor
    fun putBoolean(key: String, value: Boolean): KvEditor
    fun putInt(key: String, value: Int): KvEditor
    fun putLong(key: String, value: Long): KvEditor
    fun putFloat(key: String, value: Float): KvEditor
    fun putStringSet(key: String, value: Set<String>?): KvEditor
    fun remove(key: String): KvEditor
    fun clear(): KvEditor
    fun apply()
}
