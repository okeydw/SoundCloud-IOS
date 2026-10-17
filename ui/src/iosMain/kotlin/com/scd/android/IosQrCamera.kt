package com.scd.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

object IosQrCamera : QrCamera {
    override fun hasPermission(context: PlatformContext): Boolean =
        AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized

    @Composable
    override fun rememberPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
        val latest by rememberUpdatedState(onResult)
        return remember {
            {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    dispatch_async(dispatch_get_main_queue()) { latest(granted) }
                }
            }
        }
    }

    @Composable
    override fun View(active: Boolean, torch: Boolean, onCode: (String) -> Unit, modifier: Modifier) {
        val latest by rememberUpdatedState(onCode)
        val scanning by rememberUpdatedState(active)
        val scanner = remember { QrScanner() }
        scanner.onCode = { if (scanning) latest(it) }
        DisposableEffect(scanner) {
            scanner.start()
            onDispose { scanner.stop() }
        }
        LaunchedEffect(torch, active) { scanner.torch(torch && active) }
        UIKitView(factory = { scanner.view }, modifier = modifier)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PreviewHost(val preview: AVCaptureVideoPreviewLayer) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    init {
        backgroundColor = UIColor.blackColor
        preview.videoGravity = AVLayerVideoGravityResizeAspectFill
        layer.addSublayer(preview)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        preview.frame = bounds
        CATransaction.commit()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class QrScanner {
    private val session = AVCaptureSession()
    private var device: AVCaptureDevice? = null
    private var lastHit = 0L
    var onCode: (String) -> Unit = {}

    val view: UIView = PreviewHost(AVCaptureVideoPreviewLayer(session = session))

    private val delegate = object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
        override fun captureOutput(output: AVCaptureOutput, didOutputMetadataObjects: List<*>, fromConnection: AVCaptureConnection) {
            val code = didOutputMetadataObjects.firstNotNullOfOrNull { (it as? AVMetadataMachineReadableCodeObject)?.stringValue } ?: return
            val now = nowMillis()
            if (now - lastHit < 2_500L) return
            lastHit = now
            onCode(code)
        }
    }

    fun start() {
        val cam = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return
        device = cam
        val input = AVCaptureDeviceInput.deviceInputWithDevice(cam, null) ?: return
        if (session.canAddInput(input)) session.addInput(input)
        val output = AVCaptureMetadataOutput()
        if (session.canAddOutput(output)) {
            session.addOutput(output)
            output.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
            output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        }
        dispatch_async(dispatch_get_global_queue(0, 0u)) { session.startRunning() }
    }

    fun stop() {
        torch(false)
        dispatch_async(dispatch_get_global_queue(0, 0u)) { session.stopRunning() }
    }

    fun torch(on: Boolean) {
        val cam = device ?: return
        if (!cam.hasTorch) return
        runCatching {
            if (cam.lockForConfiguration(null)) {
                cam.torchMode = if (on) AVCaptureTorchModeOn else AVCaptureTorchModeOff
                cam.unlockForConfiguration()
            }
        }
    }
}
