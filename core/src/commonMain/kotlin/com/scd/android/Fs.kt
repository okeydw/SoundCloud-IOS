package com.scd.android

expect object Fs {
    fun readText(path: String): String?
    fun writeText(path: String, text: String)
    fun readAtomic(path: String): ByteArray?
    fun writeAtomic(path: String, bytes: ByteArray)
    fun delete(path: String): Boolean
    fun mkdirs(path: String)
    fun exists(path: String): Boolean
    fun list(dir: String): List<String>
}

object Dirs {
    lateinit var files: String

    val ready: Boolean get() = ::files.isInitialized

    fun sub(name: String): String = "$files/$name".also { Fs.mkdirs(it) }
}

fun isPlayable(t: Track): Boolean =
    !t.starLocked && (!t.unavailable || Prefs.playBlocked) && (!t.external || Downloads.isDownloaded(t.urn))
