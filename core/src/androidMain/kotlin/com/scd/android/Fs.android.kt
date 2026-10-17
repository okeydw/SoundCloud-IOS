package com.scd.android

import java.io.File

actual object Fs {
    actual fun readText(path: String): String? {
        val f = File(path)
        if (!f.exists()) return null
        return f.readText()
    }

    actual fun writeText(path: String, text: String) = File(path).writeText(text)

    actual fun readAtomic(path: String): ByteArray? {
        val f = File(path)
        if (!f.exists() && !File("$path.bak").exists()) return null
        return android.util.AtomicFile(f).readFully()
    }

    actual fun writeAtomic(path: String, bytes: ByteArray) {
        val atomic = android.util.AtomicFile(File(path))
        var out: java.io.FileOutputStream? = null
        try {
            out = atomic.startWrite()
            out.write(bytes)
            atomic.finishWrite(out)
        } catch (e: Exception) {
            out?.let { runCatching { atomic.failWrite(it) } }
            throw e
        }
    }

    actual fun delete(path: String): Boolean = File(path).delete()

    actual fun mkdirs(path: String) {
        File(path).mkdirs()
    }

    actual fun exists(path: String): Boolean = File(path).exists()

    actual fun list(dir: String): List<String> = File(dir).listFiles()?.map { it.path }.orEmpty()
}
