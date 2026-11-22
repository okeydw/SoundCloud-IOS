package com.scd.android

import java.util.concurrent.atomic.AtomicInteger
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class WaveCacheRequestTest {
    private fun target(req: HttpRequest): String = req.headers.firstOrNull { it.first == "X-Target" }
        ?.second?.let { Base64.getDecoder().decode(it).decodeToString() } ?: req.url

    private fun response(req: HttpRequest, body: String = "{\"samples\":[1,4,2,3]}", code: Int = 200) =
        HttpResponse(code, emptyMap(), body.encodeToByteArray(), req.url)

    private fun runWithNetwork(fetch: suspend (HttpRequest) -> HttpResponse, block: suspend CoroutineScope.() -> Unit) = runBlocking {
        val previous = if (Http.ready) Http.engine else null
        WaveCache.clear(keepDownloaded = false)
        Http.engine = object : HttpEngine {
            override suspend fun execute(req: HttpRequest): HttpResponse = fetch(req)
            override fun <T> stream(req: HttpRequest, block: (HttpStream) -> T): T = error("No streaming in waveform tests")
        }
        try {
            withTimeout(5000) { block() }
        } finally {
            WaveCache.clear(keepDownloaded = false)
            if (previous != null) Http.engine = previous
            else Http::class.java.getDeclaredField("engine").apply { isAccessible = true }.set(null, null)
        }
    }

    @Test fun youtubeWaveIsAvailableImmediatelyWithoutNetwork() {
        val calls = AtomicInteger()
        runWithNetwork(fetch = { req -> calls.incrementAndGet(); response(req) }) {
            val urn = "youtube:tracks:cached"
            val first = WaveCache.get(urn)
            assertNotNull(first)
            assertEquals(first, WaveCache.load(urn, null))
            WaveCache.clear(keepDownloaded = false)
            assertEquals(first, WaveCache.get(urn))
            assertEquals(0, calls.get())
        }
    }

    @Test fun savedTrackUsesItsLocalWaveWhileTheNetworkStillLooksOnline() {
        val expected = listOf(0.3f, 1f, 0.5f)
        val reads = AtomicInteger()
        val calls = AtomicInteger()
        val previous = WaveCache.localSamples
        try {
            WaveCache.localSamples = { reads.incrementAndGet(); expected }
            runWithNetwork(fetch = { req -> calls.incrementAndGet(); response(req) }) {
                val urn = "soundcloud:tracks:123"
                val waves = WaveCache.display(urn, "https://waves.test/one").toList()
                assertEquals(fallbackWaveform(urn), waves.first())
                assertEquals(expected, waves[1])
                val fresh = waves.last()
                assertEquals(96, fresh.size)
                assertEquals(fresh, WaveCache.initial(urn))
                assertEquals(listOf(fresh), WaveCache.display(urn, null).toList())
                assertEquals(1, reads.get())
                assertEquals(1, calls.get())
            }
        } finally { WaveCache.localSamples = previous }
    }

    @Test fun aFailedRefreshKeepsTheSavedWaveOnScreen() {
        val urn = "soundcloud:tracks:456"
        val saved = listOf(0.2f, 0.9f, 0.4f)
        val previous = WaveCache.localSamples
        try {
            WaveCache.localSamples = { saved }
            runWithNetwork(fetch = { response(it, "", 503) }) {
                assertEquals(listOf(fallbackWaveform(urn), saved), WaveCache.display(urn, "https://waves.test/missing").toList())
                WaveCache.localSamples = { error("Unexpected decode for a saved waveform") }
                assertEquals(listOf(saved), WaveCache.display(urn, "https://waves.test/missing").toList())
            }
        } finally { WaveCache.localSamples = previous }
    }

    @Test fun closingOneScreenDoesNotCancelTheSharedWaveLoad() {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        runWithNetwork(fetch = { req ->
            calls.incrementAndGet()
            entered.complete(Unit)
            release.await()
            response(req)
        }) {
            val first = async(start = CoroutineStart.UNDISPATCHED) { WaveCache.load("track", "https://waves.test/a.json") }
            entered.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { WaveCache.load("track", "https://waves.test/a.json") }
            first.cancelAndJoin()
            release.complete(Unit)
            val actual = second.await()
            assertEquals(96, actual!!.size)
            assertEquals(actual, WaveCache.get("track"))
            assertEquals(1, calls.get())
        }
    }

    @Test fun aFreshWaveUrlDoesNotJoinAnOlderFailingRequest() {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        runWithNetwork(fetch = { req ->
            if (target(req).contains("old.json")) {
                entered.complete(Unit)
                release.await()
                response(req, "", 503)
            } else response(req)
        }) {
            val old = async { WaveCache.load("track", "https://waves.test/old.json") }
            entered.await()
            val fresh = WaveCache.load("track", "https://waves.test/new.json")
            assertEquals(96, fresh!!.size)
            release.complete(Unit)
            assertNull(old.await())
            assertEquals(fresh, WaveCache.get("track"))
        }
    }

    @Test fun concurrentTracksKeepTheirOwnWaveSamples() {
        runWithNetwork(fetch = { req ->
            response(req, if (target(req).contains("a.json")) "{\"samples\":[1,4]}" else "{\"samples\":[4,1]}")
        }) {
            val a = async { WaveCache.load("a", "https://waves.test/a.json") }
            val b = async { WaveCache.load("b", "https://waves.test/b.json") }
            assertEquals(0.25f, a.await()!!.first())
            assertEquals(1f, b.await()!!.first())
            assertEquals(a.await(), WaveCache.get("a"))
            assertEquals(b.await(), WaveCache.get("b"))
        }
    }
}
