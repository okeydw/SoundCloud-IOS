package com.scd.android

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
actual object Fs {
    private val fm get() = NSFileManager.defaultManager

    actual fun readText(path: String): String? {
        if (!fm.fileExistsAtPath(path)) return null
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        return data.toByteArray().decodeToString()
    }

    actual fun writeText(path: String, text: String) {
        text.encodeToByteArray().toNSData().writeToFile(path, atomically = false)
    }

    actual fun readAtomic(path: String): ByteArray? {
        if (!fm.fileExistsAtPath(path)) return null
        return NSData.dataWithContentsOfFile(path)?.toByteArray()
    }

    actual fun writeAtomic(path: String, bytes: ByteArray) {
        if (!bytes.toNSData().writeToFile(path, atomically = true)) throw kotlinx.io.IOException("write failed")
    }

    actual fun delete(path: String): Boolean = fm.removeItemAtPath(path, null)

    actual fun mkdirs(path: String) {
        fm.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
    }

    actual fun exists(path: String): Boolean = fm.fileExistsAtPath(path)

    actual fun list(dir: String): List<String> =
        fm.contentsOfDirectoryAtPath(dir, null)?.filterIsInstance<String>()?.map { "$dir/$it" }.orEmpty()
}
