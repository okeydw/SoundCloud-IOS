package com.scd.android

import com.scd.android.res.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.Image as SkImage
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGImageRelease
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.createCGImage
import platform.CoreImage.filterWithName
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal fun qrImage(text: String): ImageBitmap? {
    val bytes = text.encodeToByteArray()
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.convert()) }
    val filter = CIFilter.filterWithName("CIQRCodeGenerator", withInputParameters = mapOf<Any?, Any?>("inputMessage" to data, "inputCorrectionLevel" to "M"))
        ?: return null
    val out = filter.outputImage ?: return null
    val scaled = out.imageByApplyingTransform(CGAffineTransformMakeScale(12.0, 12.0))
    val cg = CIContext.contextWithOptions(null).createCGImage(scaled, fromRect = scaled.extent) ?: return null
    val png = UIImagePNGRepresentation(UIImage.imageWithCGImage(cg))
    CGImageRelease(cg)
    png ?: return null
    val size = png.length.toInt()
    if (size == 0) return null
    val raw = ByteArray(size)
    raw.usePinned { memcpy(it.addressOf(0), png.bytes, png.length) }
    return SkImage.makeFromEncoded(raw).toComposeImageBitmap()
}

@Composable
actual fun LinkQrDialog(push: Boolean, onDismiss: () -> Unit) {
    val context = LocalAppContext.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        bitmap = null
        status = null
        val link = runCatching { Api.linkCreate(push) }.getOrElse {
            Logs.add("qr", "link create failed: ${it.message?.take(80)}")
            status = text(Res.string.login_failed)
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.Default) {
            runCatching { qrImage(QrLink.deepLink(link.claimToken, push)) }.getOrNull()
        }
        val deadline = nowMillis() + 5 * 60_000L
        while (nowMillis() < deadline) {
            delay(2_000)
            val st = runCatching { Api.linkStatus(link.linkRequestId) }.getOrNull() ?: continue
            when (st.status) {
                "claimed" -> {
                    Logs.add("qr", "link claimed (${st.mode})")
                    if (!push) {
                        val sid = st.sessionId
                        if (sid != null) SessionGuard.signIn(context, sid)
                    } else {
                        showToast(context, text(Res.string.link_pull_done))
                    }
                    onDismiss()
                    return@LaunchedEffect
                }
                "failed", "expired" -> {
                    status = text(Res.string.link_expired)
                    bitmap = null
                    return@LaunchedEffect
                }
            }
        }
        status = text(Res.string.link_expired)
        bitmap = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (push) Res.string.link_share_title else Res.string.link_show_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(if (push) Res.string.link_share_hint else Res.string.qr_login_hint),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = bitmap
                    when {
                        bmp != null -> Image(bmp, stringResource(Res.string.cd_qr_code), filterQuality = FilterQuality.None)
                        status == null -> CircularProgressIndicator()
                        else -> Text(status ?: "", color = Color.Black, textAlign = TextAlign.Center)
                    }
                }
            }
        },
        confirmButton = {
            if (status != null) {
                TextButton(onClick = { attempt++ }) { Text(stringResource(Res.string.link_regenerate)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
