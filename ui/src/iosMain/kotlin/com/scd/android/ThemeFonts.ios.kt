package com.scd.android

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font

actual fun themeFontFamily(path: String, bytes: ByteArray): FontFamily? =
    FontFamily(Font(identity = path, data = bytes))
