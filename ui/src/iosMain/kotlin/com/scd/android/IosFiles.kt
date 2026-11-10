package com.scd.android

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.*
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
object IosFiles {
    var root: UIViewController? = null
    private var delegate: PickerDelegate? = null

    private fun presenter(): UIViewController? {
        var vc = root ?: return null
        while (true) vc = vc.presentedViewController ?: return vc
    }

    fun pick(onPicked: (bytes: ByteArray, name: String) -> Unit) {
        val vc = presenter() ?: return
        val picker = UIDocumentPickerViewController(documentTypes = listOf("public.item"), inMode = UIDocumentPickerMode.UIDocumentPickerModeImport)
        val d = PickerDelegate { url ->
            delegate = null
            val data = NSData.dataWithContentsOfURL(url) ?: return@PickerDelegate
            onPicked(data.toBytes(), url.lastPathComponent ?: "file")
        }
        delegate = d
        picker.delegate = d
        vc.presentViewController(picker, animated = true, completion = null)
    }

    fun share(name: String, bytes: ByteArray) {
        val vc = presenter() ?: return
        val path = NSTemporaryDirectory() + name
        Fs.writeAtomic(path, bytes)
        val sheet = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
        sheet.popoverPresentationController?.let { popover ->
            popover.sourceView = vc.view
            popover.sourceRect = vc.view.bounds.useContents { CGRectMake(size.width / 2, size.height / 2, 1.0, 1.0) }
        }
        vc.presentViewController(sheet, animated = true, completion = null)
    }

    private fun NSData.toBytes(): ByteArray {
        val size = length.toInt()
        val out = ByteArray(size)
        if (size > 0) out.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
        return out
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PickerDelegate(private val onUrl: (NSURL) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(onUrl)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {}
}
