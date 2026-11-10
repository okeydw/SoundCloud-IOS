package com.scd.android

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
object IosBackground {
    private const val MAX_BYTES = 40 * 1024 * 1024

    fun set(bytes: ByteArray): Boolean = runCatching {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@runCatching false
        val image = UIImage.imageWithData(bytes.asNSData()) ?: return@runCatching false
        val jpeg = UIImageJPEGRepresentation(image, 0.9)?.asBytes() ?: return@runCatching false
        if (jpeg.isEmpty()) return@runCatching false
        val folder = Dirs.sub("background")
        val target = "$folder/bg_${nowMillis()}.jpg"
        Fs.writeAtomic(target, jpeg)
        Fs.list(folder).filter { it != target }.forEach { Fs.delete(it) }
        Prefs.changeBackgroundImage(target)
        true
    }.getOrDefault(false)

    fun clear() {
        val old = Prefs.backgroundImage
        Prefs.changeBackgroundImage(null)
        old?.let { Fs.delete(it) }
    }

    private fun ByteArray.asNSData(): NSData =
        usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

    private fun NSData.asBytes(): ByteArray {
        val size = length.toInt()
        val out = ByteArray(size)
        if (size > 0) out.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
        return out
    }
}
