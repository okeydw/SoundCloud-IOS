package com.scd.android

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.crossfade
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath

private var imageErrors = 0

private fun report(what: String) {
    if (imageErrors++ < 20) Logs.add("img", what)
}

class ScImageFetcher(private val url: String) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val urls = if (ImageRoute.proxied(url)) {
            val (_, value) = Api.imageProxyTarget(url)
            ImageRoute.candidates(url, formEncode(value))
        } else {
            listOf(url)
        }
        val (name, value) = Api.imageProxyTarget(url)
        var last: Exception? = null
        for ((i, candidate) in urls.withIndex()) {
            val direct = candidate == url
            val req = if (direct) HttpRequest(candidate) else HttpRequest(candidate, headers = listOf(name to value))
            val res = try {
                withContext(IoDispatcher) { Http.execute(req) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                if (!direct) ImageRoute.failed(candidate, e::class.simpleName ?: "error") else report("fail ${e::class.simpleName}")
                continue
            }
            if (res.isSuccessful && res.body.isNotEmpty()) {
                if (!direct) ImageRoute.ok(candidate)
                if (i > 0) ImageRoute.report("served by ${if (direct) "sndcdn" else ImageRoute.base(candidate).substringAfter("://")}")
                return SourceFetchResult(
                    source = ImageSource(Buffer().write(res.body), FileSystem.SYSTEM),
                    mimeType = res.header("content-type"),
                    dataSource = DataSource.NETWORK,
                )
            }
            if (direct || !ImageRoute.failover(res.code) || i == urls.lastIndex) {
                report("HTTP ${res.code} @${candidate.substringAfter("://").substringBefore('/')}")
                throw IOException("image HTTP ${res.code}")
            }
            ImageRoute.failed(candidate, "HTTP ${res.code}")
        }
        throw (last as? IOException) ?: IOException("image unavailable")
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme ?: return null
            if (scheme != "http" && scheme != "https") return null
            return ScImageFetcher(data.toString())
        }
    }
}

fun buildScImageLoader(context: coil3.PlatformContext, cacheDir: String?): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(ScImageFetcher.Factory()) }
        .apply {
            if (cacheDir != null) {
                diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.toPath())
                        .maxSizeBytes(256L * 1024 * 1024)
                        .build()
                }
            }
        }
        .crossfade(true)
        .build()
