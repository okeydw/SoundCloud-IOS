package com.scd.android

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.session.MediaController
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

@Composable
fun AppRoot(controller: MediaController?) {
    val queue = remember(controller) { controller?.queue() }
    CompositionLocalProvider(LocalQrCamera provides CameraXQr) {
        FullApp(queue)
    }
}

private object CameraXQr : QrCamera {
    override fun hasPermission(context: PlatformContext): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @Composable
    override fun rememberPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
        val latest by rememberUpdatedState(onResult)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { latest(it) }
        return { launcher.launch(Manifest.permission.CAMERA) }
    }

    @Composable
    override fun View(active: Boolean, torch: Boolean, onCode: (String) -> Unit, modifier: Modifier) =
        CameraScanner(active, torch, onCode, modifier)
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraScanner(active: Boolean, torch: Boolean, onCode: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onCode)
    val scanning by rememberUpdatedState(active)
    var camera by remember { mutableStateOf<Camera?>(null) }
    val preview = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    val lastHit = remember { AtomicLong(0L) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { scanner.close() }
            executor.shutdown()
        }
    }

    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            val cp = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = cp
            val previewUse = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null || !scanning) {
                    proxy.close()
                    return@setAnalyzer
                }
                val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                scanner.process(input)
                    .addOnSuccessListener { codes ->
                        val value = codes.firstNotNullOfOrNull { it.rawValue } ?: return@addOnSuccessListener
                        val now = nowMillis()
                        if (now - lastHit.get() < 2500L) return@addOnSuccessListener
                        lastHit.set(now)
                        ContextCompat.getMainExecutor(context).execute { if (scanning) latest(value) }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            runCatching {
                cp.unbindAll()
                camera = cp.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, previewUse, analysis)
            }.onFailure { Logs.add("qr", "camera failed: ${it::class.simpleName}") }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            camera = null
            runCatching { provider?.unbindAll() }
        }
    }

    LaunchedEffect(torch, camera, active) {
        val cam = camera ?: return@LaunchedEffect
        if (cam.cameraInfo.hasFlashUnit()) runCatching { cam.cameraControl.enableTorch(torch && active) }
    }
    AndroidView(factory = { preview }, modifier = modifier)
}
