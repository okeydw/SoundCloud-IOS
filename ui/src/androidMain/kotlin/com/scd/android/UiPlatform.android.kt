package com.scd.android

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual val LocalAppContext: ProvidableCompositionLocal<PlatformContext> = LocalContext

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled = enabled, onBack = onBack)

@Composable
actual fun SystemBars(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(dark) {
        val transparent = Color.Transparent.toArgb()
        val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

@Composable
actual fun platformPainter(icon: Any): Painter = androidx.compose.ui.res.painterResource(icon as Int)

@Composable
actual fun rememberArtColor(model: Any?): Color? {
    val context = LocalContext.current
    var color by remember(model) { mutableStateOf<Color?>(null) }
    LaunchedEffect(model) {
        if (model == null) return@LaunchedEffect
        color = runCatching {
            val request = ImageRequest.Builder(context).data(model).allowHardware(false).size(96).build()
            val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image ?: return@runCatching null
            val bitmap = image.toBitmap()
            withContext(Dispatchers.Default) {
                val palette = Palette.from(bitmap).generate()
                val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
                swatch?.rgb?.let { Color(it) }
            }
        }.getOrNull()
    }
    return color
}

actual fun Modifier.softShadow(shape: Shape, radius: Dp, color: Color, offsetY: Dp): Modifier =
    if (radius <= Dp(0f) || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) this else drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = androidx.compose.ui.graphics.Path().apply { addOutline(outline) }
        clipPath(path, androidx.compose.ui.graphics.ClipOp.Difference) {
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    this.color = android.graphics.Color.BLACK
                    setShadowLayer(radius.toPx(), 0f, offsetY.toPx(), color.toArgb())
                }
                canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
            }
        }
    }

actual fun currentHour(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

actual fun isLowRamDevice(context: PlatformContext): Boolean =
    (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true

actual fun openLink(context: PlatformContext, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

actual fun fullscreenDialogProperties(): androidx.compose.ui.window.DialogProperties =
    androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
