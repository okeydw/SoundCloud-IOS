package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily

expect fun themeFontFamily(path: String, bytes: ByteArray): FontFamily?

object ThemeFonts {
    const val MAX_BYTES = 8 * 1024 * 1024

    var family by mutableStateOf<FontFamily?>(null)
        private set

    fun init() {
        load(Prefs.themeFontPath)
    }

    private fun load(path: String?) {
        family = path?.let { p ->
            val bytes = Fs.readAtomic(p) ?: return@let null
            runCatching { themeFontFamily(p, bytes) }
                .onFailure { Logs.add("theme", "font load failed: ${it::class.simpleName}") }
                .getOrNull()
        }
    }

    fun extensionOf(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        val head = (0 until 4).map { bytes[it].toInt() and 0xFF }
        val tag = (0 until 4).map { bytes[it].toInt().toChar() }.joinToString("")
        return when {
            head == listOf(0x00, 0x01, 0x00, 0x00) -> "ttf"
            tag == "true" -> "ttf"
            tag == "OTTO" -> "otf"
            tag == "ttcf" -> "ttc"
            else -> null
        }
    }

    fun currentBytes(): ByteArray? = Prefs.themeFontPath?.let { Fs.readAtomic(it) }

    fun save(bytes: ByteArray, name: String): Boolean = runCatching {
        if (bytes.size > MAX_BYTES) return@runCatching false
        val ext = extensionOf(bytes) ?: return@runCatching false
        val folder = Dirs.sub("fonts")
        val target = "$folder/theme_${nowMillis()}.$ext"
        Fs.writeAtomic(target, bytes)
        if (runCatching { themeFontFamily(target, bytes) }.getOrNull() == null) {
            Fs.delete(target)
            return@runCatching false
        }
        Fs.list(folder).filter { it != target }.forEach { Fs.delete(it) }
        val label = name.trim().substringAfterLast('/').substringBeforeLast('.').ifBlank { "Custom" }.take(48)
        Prefs.changeThemeFont(target, label)
        load(target)
        true
    }.getOrDefault(false)

    fun clear() {
        if (Dirs.ready) Fs.list(Dirs.sub("fonts")).forEach { Fs.delete(it) }
        Prefs.changeThemeFont(null, "")
        family = null
    }
}
