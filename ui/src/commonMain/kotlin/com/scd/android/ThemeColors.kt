package com.scd.android

import com.scd.android.res.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp

fun customBackground(dark: Boolean): List<Color>? {
    if (Prefs.bgColor1 == 0) return null
    val colors = listOfNotNull(Prefs.bgColor1, Prefs.bgColor2.takeIf { it != 0 }).map { Color(it) }
    return if (dark) colors else colors.map { lerp(it, Color.White, 0.55f) }
}

fun accentColors(): List<Color> = listOf(Color(Prefs.accent))

fun accentBrush(): Brush {
    val colors = accentColors()
    return if (colors.size > 1) Brush.horizontalGradient(colors) else SolidColor(colors.first())
}
