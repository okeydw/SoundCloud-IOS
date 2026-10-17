package com.scd.android

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

object ImageRouteOkHttp {
    fun proceed(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (!req.url.host.endsWith("sndcdn.com")) return chain.proceed(req)
        val target = req.url.toString()
        val (name, value) = Api.imageProxyTarget(target)
        val urls = ImageRoute.candidates(target, android.net.Uri.encode(value))
        var last: IOException? = null
        for ((i, url) in urls.withIndex()) {
            val direct = url == target
            val builder = Request.Builder().url(url)
            if (!direct) builder.header(name, value)
            val res = try {
                chain.proceed(builder.build())
            } catch (e: IOException) {
                if (chain.call().isCanceled()) throw e
                last = e
                if (!direct) ImageRoute.failed(url, e.javaClass.simpleName)
                continue
            }
            if (res.isSuccessful || i == urls.lastIndex || !ImageRoute.failover(res.code)) {
                if (res.isSuccessful && !direct) ImageRoute.ok(url)
                if (res.isSuccessful && i > 0) ImageRoute.report("served by ${if (direct) "sndcdn" else ImageRoute.base(url).substringAfter("://")}")
                return res
            }
            if (!direct) ImageRoute.failed(url, "HTTP ${res.code}")
            res.close()
        }
        throw last ?: IOException("image unavailable")
    }
}
