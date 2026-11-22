package com.scd.android

object IosBackground {
    fun set(jpeg: ByteArray): Boolean = runCatching {
        if (jpeg.isEmpty() || jpeg.size > 40 * 1024 * 1024) return@runCatching false
        val folder = Dirs.sub("background")
        val target = "$folder/bg_${nowMillis()}.jpg"
        Fs.writeAtomic(target, jpeg)
        if (!Fs.exists(target)) return@runCatching false
        Prefs.changeBackgroundImage(target)
        Fs.list(folder).filter { it != target && it.substringAfterLast('/').startsWith("bg_") && it.endsWith(".jpg") }.forEach { Fs.delete(it) }
        true
    }.getOrDefault(false)

    fun clear() {
        val old = Prefs.backgroundImage
        Prefs.changeBackgroundImage(null)
        val folder = Dirs.sub("background")
        if (old != null && old.startsWith("$folder/") && old.substringAfterLast('/').startsWith("bg_")) Fs.delete(old)
    }
}
