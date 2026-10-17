package com.scd.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.scd.android.res.Res
import com.scd.android.res.ic_music
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSDate

actual val LocalAppContext: ProvidableCompositionLocal<PlatformContext> = staticCompositionLocalOf { PlatformContext.INSTANCE }

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}

@Composable
actual fun SystemBars(dark: Boolean) {
    LaunchedEffect(dark) { IosChrome.darkBars(dark) }
}

@Composable
actual fun platformPainter(icon: Any): Painter = painterResource(Res.drawable.ic_music)

@Composable
actual fun rememberArtColor(model: Any?): Color? {
    val context = LocalPlatformContext.current
    var color by remember(model) { mutableStateOf<Color?>(null) }
    LaunchedEffect(model) {
        if (model == null) return@LaunchedEffect
        color = runCatching {
            val request = ImageRequest.Builder(context).data(model).size(48).build()
            val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image ?: return@runCatching null
            val bmp = image.toBitmap()
            var best = 0
            var bestScore = -1f
            for (y in 0 until bmp.height step 4) for (x in 0 until bmp.width step 4) {
                val c = bmp.getColor(x, y)
                val r = (c shr 16 and 0xFF) / 255f
                val g = (c shr 8 and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val score = (max - min) * (0.4f + max)
                if (score > bestScore) {
                    bestScore = score
                    best = c
                }
            }
            Color(best or (0xFF shl 24))
        }.getOrNull()
    }
    return color
}

actual fun Modifier.softShadow(shape: Shape, radius: Dp, color: Color, offsetY: Dp): Modifier =
    if (radius <= Dp(0f)) this else drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        clipPath(path, androidx.compose.ui.graphics.ClipOp.Difference) {
            drawIntoCanvas { canvas ->
                val paint = Paint()
                paint.color = color
                paint.asFrameworkPaint().maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, radius.toPx() / 2f)
                canvas.save()
                canvas.translate(0f, offsetY.toPx())
                canvas.drawPath(path, paint)
                canvas.restore()
            }
        }
    }

actual fun currentHour(): Int = NSCalendar.currentCalendar.component(NSCalendarUnitHour, NSDate()).toInt()

actual fun isLowRamDevice(context: PlatformContext): Boolean = false

actual fun openLink(context: PlatformContext, url: String) = openInBrowser(context, url)

@Composable
actual fun rememberCoverPicker(urn: String): () -> Unit = {}

object IosChrome {
    var darkBars: (Boolean) -> Unit = {}
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
actual fun fullscreenDialogProperties(): androidx.compose.ui.window.DialogProperties =
    androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, usePlatformInsets = false)
