package com.scd.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import coil3.SingletonImageLoader
import com.scd.android.res.Res
import com.scd.android.res.allStringResources
import com.scd.android.res.qr_use_scanner
import kotlinx.coroutines.delay
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.*

private object IosToast {
    var text by mutableStateOf<String?>(null)
    var serial by mutableIntStateOf(0)
}

private var root: UIViewController? = null

private val player: IosPlayer by lazy { IosPlayer() }

fun MainViewController(): UIViewController {
    IosCore.start()
    installUi()
    val vc = ComposeUIViewController { IosRoot() }
    root = vc
    return vc
}

private fun installUi() {
    val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String
    SingletonImageLoader.setSafe { buildScImageLoader(it, caches?.let { c -> "$c/images" }) }
    Strings.lookup = { name -> Res.allStringResources[name]?.let { text(it) } ?: name }
    Toasts.show = { msg ->
        IosToast.text = msg
        IosToast.serial++
    }
    Toasts.share = { text ->
        val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        root?.presentViewController(sheet, animated = true, completion = null)
    }
    Haptics.impl = { level ->
        val style = if (level == 0) UIImpactFeedbackStyle.UIImpactFeedbackStyleLight else UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
        UIImpactFeedbackGenerator(style = style).impactOccurred()
    }
    IosChrome.darkBars = { dark ->
        UIApplication.sharedApplication.setStatusBarStyle(if (dark) UIStatusBarStyleLightContent else UIStatusBarStyleDarkContent, animated = true)
    }
    PlayerHub.player = player
}

@Composable
private fun IosRoot() {
    CompositionLocalProvider(LocalQrCamera provides IosQrCamera) {
        Box(Modifier.fillMaxSize()) {
            FullApp(player)
            ToastHost(Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ToastHost(modifier: Modifier) {
    val msg = IosToast.text
    val serial = IosToast.serial
    LaunchedEffect(serial) {
        if (msg == null) return@LaunchedEffect
        delay(2_600)
        IosToast.text = null
    }
    AnimatedVisibility(msg != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 24.dp, end = 24.dp, bottom = 96.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xE6202024))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(msg ?: "", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

object IosLinks {
    fun open(url: String) {
        if (url.startsWith("scd:")) {
            val parsed = QrLink.parse(url)
            when {
                parsed == null -> Logs.add("qr", "external scd link ignored: malformed")
                parsed.second -> {
                    Logs.add("qr", "external pull link ignored: scan it with the in-app scanner")
                    showToast(PlatformContext.INSTANCE, text(Res.string.qr_use_scanner))
                }
                else -> PendingLink.set(parsed.first, pull = false)
            }
            return
        }
        val host = url.substringAfter("://", "").substringBefore('/').substringBefore('?').lowercase()
        if (host != "soundcloud.com" && !host.endsWith(".soundcloud.com")) return
        val path = "/" + url.substringAfter("://").substringAfter('/', "")
        if (isAuthPage(host, path)) {
            openInBrowser(PlatformContext.INSTANCE, url)
        } else {
            NavEvents.openUrl(url)
        }
    }
}
