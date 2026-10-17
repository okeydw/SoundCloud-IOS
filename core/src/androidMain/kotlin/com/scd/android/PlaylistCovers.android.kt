package com.scd.android

import android.content.Context
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File

fun PlaylistCovers.init(context: Context) =
    init(AndroidKvStore(context, "playlist_covers"), File(context.filesDir, "covers").path)

fun PlaylistCovers.file(urn: String): File? = get(urn)?.let { File(it) }

fun PlaylistCovers.set(urn: String, bitmap: Bitmap): Boolean = runCatching {
    val side = minOf(bitmap.width, bitmap.height)
    val scaled = if (side > 800) Bitmap.createScaledBitmap(bitmap, bitmap.width * 800 / side, bitmap.height * 800 / side, true) else bitmap
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
    if (scaled !== bitmap) scaled.recycle()
    setBytes(urn, out.toByteArray())
}.getOrDefault(false)

fun PlaylistCovers.importStream(urn: String, input: java.io.InputStream): Boolean = runCatching {
    setBytes(urn, input.use { it.readBytes() })
}.getOrDefault(false)
