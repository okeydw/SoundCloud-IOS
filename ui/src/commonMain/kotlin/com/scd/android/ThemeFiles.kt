package com.scd.android

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
data class ThemeFile(
    val version: Int = 1,
    val name: String = "",
    val accent: Int = AccentPalette.DEFAULT,
    val accent2: Int = 0,
    val backgroundColor1: Int = 0,
    val backgroundColor2: Int = 0,
    val textColor: Int = 0,
    val headerAlpha: Float = 1f,
    val footerAlpha: Float = 1f,
    val backgroundBlur: Boolean = true,
    val backgroundBlurRadius: Float = 28f,
    val backgroundDim: Float = 0.6f,
    val image: String? = null,
    val font: String? = null,
    val fontName: String? = null,
    val fontData: String? = null,
)

@OptIn(ExperimentalEncodingApi::class)
object ThemeFiles {
    const val FILE_NAME = "scd-theme.json"
    private const val FONT_CUSTOM = "custom"
    private const val FONT_SYSTEM = "system"
    private const val MAX_BYTES = 40 * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun export(): ByteArray {
        val image = Prefs.backgroundImage?.let { Fs.readAtomic(it) }?.let { Base64.encode(it) }
        val font = ThemeFonts.currentBytes()?.let { Base64.encode(it) }
        val file = ThemeFile(
            name = "SoundCloud theme",
            accent = Prefs.accent,
            accent2 = Prefs.accent2,
            backgroundColor1 = Prefs.bgColor1,
            backgroundColor2 = Prefs.bgColor2,
            textColor = Prefs.textColor,
            headerAlpha = Prefs.headerAlpha,
            footerAlpha = Prefs.footerAlpha,
            backgroundBlur = Prefs.backgroundBlur,
            backgroundBlurRadius = Prefs.backgroundBlurRadius,
            backgroundDim = Prefs.backgroundDim,
            image = image,
            font = if (font != null) FONT_CUSTOM else FONT_SYSTEM,
            fontName = Prefs.themeFontName.takeIf { font != null },
            fontData = font,
        )
        return json.encodeToString(ThemeFile.serializer(), file).encodeToByteArray()
    }

    private fun opaque(color: Int): Int = color or 0xFF000000.toInt()

    private fun hex(value: JsonElement?): Int? {
        val raw = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        if (!raw.matches(Regex("^#[0-9a-fA-F]{6}$"))) return null
        return opaque(raw.substring(1).toInt(16))
    }

    fun import(bytes: ByteArray, name: String): Boolean = runCatching {
        if (bytes.size > MAX_BYTES) return@runCatching false
        if (ThemeFonts.extensionOf(bytes) != null) return@runCatching ThemeFonts.save(bytes, name)
        val text = bytes.decodeToString()
        val tree = json.parseToJsonElement(text) as? JsonObject ?: return@runCatching false
        if ((tree["accent"] as? JsonPrimitive)?.isString == true) return@runCatching importDesktop(tree)
        val file = json.decodeFromString(ThemeFile.serializer(), text)
        val image = file.image?.takeIf { it.isNotEmpty() }?.let { Base64.decode(it) }
        val fontBytes = file.fontData?.takeIf { it.isNotEmpty() }?.let { Base64.decode(it) }
        if (fontBytes != null && ThemeFonts.extensionOf(fontBytes) == null) return@runCatching false

        if (file.accent2 != 0) Prefs.changeAccentGradient(opaque(file.accent), opaque(file.accent2)) else Prefs.changeAccent(opaque(file.accent))
        Prefs.changeBackgroundColors(
            if (file.backgroundColor1 == 0) 0 else opaque(file.backgroundColor1),
            if (file.backgroundColor2 == 0) 0 else opaque(file.backgroundColor2),
        )
        Prefs.changeTextColor(if (file.textColor == 0) 0 else opaque(file.textColor))
        Prefs.changeHeaderAlpha(file.headerAlpha.coerceIn(0f, 1f))
        Prefs.changeFooterAlpha(file.footerAlpha.coerceIn(0f, 1f))
        Prefs.changeBackgroundBlur(file.backgroundBlur)
        Prefs.changeBackgroundBlurRadius(file.backgroundBlurRadius.coerceIn(0f, 60f))
        Prefs.changeBackgroundDim(file.backgroundDim.coerceIn(0f, 0.95f))
        if (image != null && image.isNotEmpty()) {
            val folder = Dirs.sub("background")
            val target = "$folder/bg_${nowMillis()}.jpg"
            Fs.writeAtomic(target, image)
            Fs.list(folder).filter { it != target }.forEach { Fs.delete(it) }
            Prefs.changeBackgroundImage(target)
        } else {
            Prefs.changeBackgroundImage(null)
        }
        when {
            fontBytes != null -> ThemeFonts.save(fontBytes, file.fontName ?: "Custom")
            file.font == FONT_SYSTEM -> ThemeFonts.clear()
        }
        true
    }.getOrDefault(false)

    private fun importDesktop(tree: JsonObject): Boolean {
        val accent = hex(tree["accent"]) ?: return false
        Prefs.changeAccent(accent)
        hex(tree["background"])?.let { Prefs.changeBackgroundColors(it, 0) }
        when ((tree["scheme"] as? JsonPrimitive)?.content) {
            "dark" -> Prefs.setThemeMode("dark")
            "white" -> Prefs.setThemeMode("light")
        }
        val font = (tree["font"] as? JsonPrimitive)?.content
        if (font == FONT_SYSTEM) {
            ThemeFonts.clear()
        } else if (font != null) {
            Logs.add("theme", "desktop font preset '$font' is not bundled, keeping current")
        }
        return true
    }
}
