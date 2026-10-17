package com.scd.android

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.IOException
import platform.Foundation.*
import platform.darwin.NSObject
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DarwinHttpEngine : HttpEngine {
    private val sessions = HashMap<HttpProfile, NSURLSession>()

    private class Limits(val request: Double, val resource: Double, val noCache: Boolean = false)

    private fun limits(p: HttpProfile): Limits = when (p) {
        HttpProfile.BASE -> Limits(60.0, 600.0)
        HttpProfile.API -> Limits(45.0, 60.0)
        HttpProfile.MUTATE -> Limits(60.0, 90.0)
        HttpProfile.SLOW -> Limits(180.0, 180.0)
        HttpProfile.PROBE -> Limits(8.0, 8.0)
        HttpProfile.SHORT_LINK -> Limits(8.0, 8.0, noCache = true)
        HttpProfile.LOGOUT -> Limits(15.0, 15.0)
        HttpProfile.REFRESH -> Limits(45.0, 45.0)
        HttpProfile.VOLUME -> Limits(6.0, 20.0, noCache = true)
        HttpProfile.QUICK -> Limits(4.0, 5.0, noCache = true)
        HttpProfile.ANON -> Limits(8.0, 12.0, noCache = true)
        HttpProfile.ANON_HOME -> Limits(20.0, 30.0, noCache = true)
    }

    private val sessionLock = NSLock()
    private val redirectGuard = RedirectGuard()

    private fun session(p: HttpProfile): NSURLSession {
        sessionLock.lock()
        try {
            return sessionFor(p)
        } finally {
            sessionLock.unlock()
        }
    }

    private fun sessionFor(p: HttpProfile): NSURLSession =
        sessions.getOrPut(p) {
            val l = limits(p)
            val config = NSURLSessionConfiguration.defaultSessionConfiguration
            config.timeoutIntervalForRequest = l.request
            config.timeoutIntervalForResource = l.resource
            config.HTTPMaximumConnectionsPerHost = 8
            if (l.noCache) config.URLCache = null
            NSURLSession.sessionWithConfiguration(config, delegate = redirectGuard, delegateQueue = null)
        }

    private fun request(r: HttpRequest): NSMutableURLRequest {
        val url = NSURL.URLWithString(r.url) ?: throw IOException("bad url")
        val req = NSMutableURLRequest.requestWithURL(url)
        req.setHTTPMethod(r.method)
        r.headers.forEach { (k, v) -> req.setValue(v, forHTTPHeaderField = k) }
        r.body?.let { b ->
            req.setHTTPBody(b.bytes.toNSData())
            b.contentType?.let { req.setValue(it, forHTTPHeaderField = "Content-Type") }
        }
        if (r.forceNetwork) req.setCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
        return req
    }

    override suspend fun execute(req: HttpRequest): HttpResponse {
        val ns = request(req)
        val session = session(req.profile)
        return suspendCancellableCoroutine { cont ->
            val task = session.dataTaskWithRequest(ns) { data: NSData?, response: NSURLResponse?, error: NSError? ->
                if (error != null) {
                    if (cont.isActive) cont.resumeWithException(error.toIo())
                } else {
                    val http = response as? NSHTTPURLResponse
                    val out = HttpResponse(
                        code = http?.statusCode?.toInt() ?: 0,
                        headers = http.headerMap(),
                        body = if (req.discardBody) ByteArray(0) else data?.toByteArray() ?: ByteArray(0),
                        finalUrl = response?.URL?.absoluteString ?: req.url,
                    )
                    if (cont.isActive) cont.resume(out)
                }
            }
            cont.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }

    override fun <T> stream(req: HttpRequest, block: (HttpStream) -> T): T {
        val ns = request(req)
        val sem = dispatch_semaphore_create(0)
        var result: HttpResponse? = null
        var failure: Throwable? = null
        val task = session(req.profile).dataTaskWithRequest(ns) { data: NSData?, response: NSURLResponse?, error: NSError? ->
            if (error != null) {
                failure = error.toIo()
            } else {
                val http = response as? NSHTTPURLResponse
                result = HttpResponse(
                    http?.statusCode?.toInt() ?: 0,
                    http.headerMap(),
                    data?.toByteArray() ?: ByteArray(0),
                    response?.URL?.absoluteString ?: req.url,
                )
            }
            dispatch_semaphore_signal(sem)
        }
        task.resume()
        dispatch_semaphore_wait(sem, DISPATCH_TIME_FOREVER)
        failure?.let { throw it }
        val res = result ?: throw IOException("no response")
        var pos = 0
        return block(object : HttpStream {
            override val code: Int = res.code
            override fun header(name: String): String? = res.header(name)
            override fun read(buf: ByteArray): Int {
                if (pos >= res.body.size) return -1
                val n = minOf(buf.size, res.body.size - pos)
                res.body.copyInto(buf, 0, pos, pos + n)
                pos += n
                return n
            }
        })
    }
}

private class RedirectGuard : NSObject(), NSURLSessionTaskDelegateProtocol {
    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit,
    ) {
        val target = newRequest.URL?.absoluteString.orEmpty()
        if (Endpoints.trustedUrl(target)) {
            completionHandler(newRequest)
            return
        }
        val clean = newRequest.mutableCopy() as? NSMutableURLRequest
        if (clean == null) {
            completionHandler(null)
            return
        }
        clean.setValue(null, forHTTPHeaderField = "x-session-id")
        val parts = newRequest.URL?.let { NSURLComponents.componentsWithURL(it, resolvingAgainstBaseURL = false) }
        val items = parts?.queryItems?.filterIsInstance<NSURLQueryItem>()
        if (parts != null && items != null && items.any { it.name == "session_id" }) {
            val kept = items.filter { it.name != "session_id" }
            parts.queryItems = kept.ifEmpty { null }
            parts.URL?.let { clean.setURL(it) }
        }
        completionHandler(clean)
    }
}

private val unreachableCodes = setOf(-1003L, -1004L, -1006L, -1009L, -1200L)

private fun NSError.toIo(): IOException =
    if (code in unreachableCodes) UnreachableException(localizedDescription) else IOException(localizedDescription)

private fun NSHTTPURLResponse?.headerMap(): Map<String, String> {
    val out = HashMap<String, String>()
    this?.allHeaderFields?.forEach { (k, v) ->
        val key = k as? String ?: return@forEach
        out[key.lowercase()] = v?.toString().orEmpty()
    }
    return out
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }
