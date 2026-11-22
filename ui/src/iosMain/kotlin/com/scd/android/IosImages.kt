package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.*
import platform.darwin.NSObject
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object IosImages {
    var busy by mutableStateOf(false)
        private set
    private var delegate: PhotoDelegate? = null

    fun pick(onPicked: (ByteArray) -> Boolean, onError: () -> Unit) {
        if (busy) return
        var presenter = IosFiles.root ?: run { onError(); return }
        while (true) {
            val next = presenter.presentedViewController?.takeUnless { it.isBeingDismissed() } ?: break
            presenter = next
        }
        val config = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1
        }
        val picker = PHPickerViewController(config)
        val handler = PhotoDelegate { data, failed ->
            delegate = null
            if (data == null) {
                busy = false
                if (failed) onError()
            } else {
                AppScope.scope.launch(Dispatchers.Main) {
                    try {
                        val jpeg = withContext(IoDispatcher) { jpeg(data) }
                        if (jpeg == null || !runCatching { onPicked(jpeg) }.getOrDefault(false)) onError()
                    } finally { busy = false }
                }
            }
        }
        delegate = handler
        picker.delegate = handler
        busy = true
        presenter.presentViewController(picker, animated = true, completion = null)
    }

    private fun jpeg(data: NSData): ByteArray? = runCatching {
        if (data.length == 0UL || data.length > 40UL * 1024UL * 1024UL) return@runCatching null
        val image = UIImage.imageWithData(data) ?: return@runCatching null
        val (width, height) = image.size.useContents { width to height }
        if (!width.isFinite() || !height.isFinite() || width <= 0 || height <= 0 || width * height > 64_000_000) return@runCatching null
        val scale = minOf(1.0, 2048.0 / maxOf(width, height))
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(width * scale, height * scale), false, 1.0)
        val output = try {
            image.drawInRect(CGRectMake(0.0, 0.0, width * scale, height * scale))
            UIGraphicsGetImageFromCurrentImageContext()?.let { UIImageJPEGRepresentation(it, 0.9) }
        } finally { UIGraphicsEndImageContext() }
        output ?: return@runCatching null
        val size = output.length.toInt()
        if (size <= 0) return@runCatching null
        ByteArray(size).also { bytes -> bytes.usePinned { memcpy(it.addressOf(0), output.bytes, output.length) } }
    }.getOrNull()
}

@OptIn(ExperimentalForeignApi::class)
private class PhotoDelegate(private val complete: (NSData?, Boolean) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        picker.dismissViewControllerAnimated(true) {
            if (result == null) complete(null, false)
            else result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, error ->
                AppScope.scope.launch(Dispatchers.Main) { complete(data, error != null || data == null) }
            }
        }
    }
}
