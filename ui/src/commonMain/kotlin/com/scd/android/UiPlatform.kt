package com.scd.android

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource

expect val LocalAppContext: ProvidableCompositionLocal<PlatformContext>

@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

@Composable
expect fun SystemBars(dark: Boolean)

@Composable
expect fun platformPainter(icon: Any): Painter

@Composable
fun iconPainter(icon: Any): Painter = if (icon is DrawableResource) painterResource(icon) else platformPainter(icon)

@Composable
expect fun SettingsScreen(
    username: String?,
    avatar: String? = null,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    visualExtras: @Composable () -> Unit = {},
    accountExtras: @Composable () -> Unit = {},
)

@Composable
expect fun SettingsLink(icon: DrawableResource, title: String, subtitle: String, onClick: () -> Unit)

@Composable
expect fun LinkQrDialog(push: Boolean, onDismiss: () -> Unit)

@Composable
expect fun rememberCoverPicker(urn: String): () -> Unit

@Composable
expect fun rememberArtColor(model: Any?): Color?

expect fun Modifier.softShadow(shape: Shape, radius: Dp, color: Color, offsetY: Dp = 0.dp): Modifier

expect fun fullscreenDialogProperties(): androidx.compose.ui.window.DialogProperties

expect fun currentHour(): Int

expect fun isLowRamDevice(context: PlatformContext): Boolean

expect fun openLink(context: PlatformContext, url: String)

fun text(res: StringResource, vararg args: Any): String = runBlocking { getString(res, *args) }

fun parseHexColor(hex: String?): Int? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    val v = h.toLongOrNull(16) ?: return null
    return when (h.length) {
        6 -> (0xFF000000 or v).toInt()
        8 -> v.toInt()
        else -> null
    }
}

interface QrCamera {
    fun hasPermission(context: PlatformContext): Boolean

    @Composable
    fun rememberPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit

    @Composable
    fun View(active: Boolean, torch: Boolean, onCode: (String) -> Unit, modifier: Modifier)
}

val LocalQrCamera = staticCompositionLocalOf<QrCamera?> { null }

class SettingsSkin(
    val header: @Composable (title: String, onBack: () -> Unit) -> Unit,
    val panel: @Composable (content: @Composable () -> Unit) -> Unit,
    val row: @Composable (icon: Any, title: String, subtitle: String, onClick: () -> Unit) -> Unit,
    val equalizer: (@Composable () -> Unit)? = null,
    val footerAlpha: Boolean = true,
)

val LocalSettingsSkin = androidx.compose.runtime.staticCompositionLocalOf<SettingsSkin?> { null }

object Motion {
    const val duration = 320

    fun <S> overlay(): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        (fadeIn(tween(duration)) + scaleIn(tween(duration), initialScale = 0.97f)) togetherWith
            (fadeOut(tween(duration * 2 / 3)) + scaleOut(tween(duration), targetScale = 1.01f))
    }
}

object Haptics {
    var impl: (Int) -> Unit = {}

    fun tap() {
        if (Prefs.haptics) impl(0)
    }

    fun strong() {
        if (Prefs.haptics) impl(1)
    }
}

fun signedOneDecimal(v: Double): String = (if (v >= 0) "+" else "") + oneDecimal(v)
