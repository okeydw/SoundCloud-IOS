package com.scd.android

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class AndroidApiCacheTest {
    @Test fun compressedPendingCollectionIsNotStoredAndAccountsStaySeparate() {
        val bytes = Buffer().also { buffer ->
            GzipSink(buffer).buffer().use { it.writeUtf8("""{"collection":[],"sync":{"status":"refreshing","baselineGeneration":0}}""") }
        }.readByteArray()
        val request = Request.Builder().url("https://api.r1.relay.scnative.dev/me/likes/tracks").build()
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .header("Content-Encoding", "gzip").body(bytes.toResponseBody()).build().use { response ->
                val cached = AndroidApiCache.response(response, true, false)
                assertTrue(cached.cacheControl.noStore)
                assertTrue(cached.cacheControl.isPrivate)
                assertTrue(cached.header("Vary").orEmpty().contains("x-session-id"))
                assertArrayEquals(bytes, response.body!!.bytes())
            }
    }
}
