package com.scd.android

import okhttp3.Response
import okio.GzipSource
import okio.buffer

object AndroidApiCache {
    private fun cacheBody(response: Response): String = runCatching {
        val limit = 2L * 1024 * 1024
        response.peekBody(limit).use { body ->
            if (!response.header("Content-Encoding").equals("gzip", ignoreCase = true)) body.string()
            else GzipSource(body.source()).buffer().use { source ->
                source.request(limit)
                source.buffer.readUtf8(minOf(limit, source.buffer.size))
            }
        }
    }.getOrDefault("")

    fun response(response: Response, isApi: Boolean, isImage: Boolean): Response {
        val request = response.request
        if (request.method != "GET" || response.code != 200) return response
        val path = request.url.encodedPath
        if (isApi) {
            val privateControl = path.startsWith("/auth") || path.startsWith("/health") || path.startsWith("/recommendations") ||
                path.startsWith("/lyrics") || path.startsWith("/rooms") || path.startsWith("/me/subscription") || path.endsWith("/stream")
            val pending = !privateControl && MobileApiContract.pendingCollection(cacheBody(response))
            return response.newBuilder().removeHeader("Pragma")
                .header("Cache-Control", if (privateControl || pending) "private, no-store" else "private, max-age=0")
                .header("Vary", (response.headers.values("Vary").flatMap { it.split(',') }.map { it.trim() } + "x-session-id").distinct().joinToString(", "))
                .build()
        }
        if (!isImage || response.cacheControl.noStore) return response
        return response.newBuilder().header("Cache-Control", "public, max-age=604800").build()
    }
}
