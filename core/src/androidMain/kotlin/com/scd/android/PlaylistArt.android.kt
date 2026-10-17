package com.scd.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun platformArtHash(track: Track): Long? {
    val url = track.artwork_url ?: return null
        val local = Downloads.artPathOrNull(track.urn)
        return withContext(Dispatchers.IO) {
            run {
                runCatching {
                    val bmp = if (local != null) {
                        android.graphics.BitmapFactory.decodeFile(
                            local,
                            android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 },
                        )
                    } else {
                        val small = Api.artworkUrl(url, "small") ?: return@runCatching null
                        val req = if (small.contains("sndcdn.com")) {
                            val (name, value) = Api.imageProxyTarget(small)
                            okhttp3.Request.Builder()
                                .url("${Api.IMAGES_BASE}/?t=${android.net.Uri.encode(value)}")
                                .header(name, value)
                                .build()
                        } else {
                            okhttp3.Request.Builder().url(small).build()
                        }
                        val bytes = Api.http.newCall(req).execute().use { res ->
                            if (!res.isSuccessful) null else res.body?.bytes()
                        } ?: return@runCatching null
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } ?: return@runCatching null
                    val tiny = android.graphics.Bitmap.createScaledBitmap(bmp, 8, 8, true)
                    val gray = IntArray(64) { i ->
                        val p = tiny.getPixel(i % 8, i / 8)
                        ((p shr 16 and 0xFF) * 30 + (p shr 8 and 0xFF) * 59 + (p and 0xFF) * 11) / 100
                    }
                    if (tiny !== bmp) tiny.recycle()
                    bmp.recycle()
                    val avg = gray.average()
                    var h = 0L
                    gray.forEachIndexed { i, g -> if (g >= avg) h = h or (1L shl i) }
                    h
                }.getOrNull()
            }
        }
}
