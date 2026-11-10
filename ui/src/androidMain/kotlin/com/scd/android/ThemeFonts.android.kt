package com.scd.android

import androidx.compose.ui.text.font.FontFamily

actual fun themeFontFamily(path: String, bytes: ByteArray): FontFamily? =
    FontFamily(android.graphics.Typeface.createFromFile(path))
