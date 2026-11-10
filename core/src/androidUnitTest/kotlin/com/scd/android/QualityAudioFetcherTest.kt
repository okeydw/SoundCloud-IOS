package com.scd.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class QualityAudioFetcherTest {
    private val base = "https://stream.r1.relay.scnative.dev/stream/soundcloud%3Atracks%3A1"
    private val mirror = base.replace("r1", "r2")
    private val stored = "https://storage.r1.relay.scnative.dev/soundcloud_tracks_1.m4a"
    private val audio = byteArrayOf(1, 2, 3)
    private fun response(url: String, quality: String?, status: Int = 200, bytes: ByteArray = audio) =
        HttpResponse(status, quality?.let { mapOf("x-audio-quality" to it) }.orEmpty(), bytes, url)
    private fun fetcher(request: suspend (String) -> HttpResponse?) =
        QualityAudioFetcher(request, { audio }, { it.contentEquals(audio) })
    @Test fun anSqUpgradeKeepsHqReturnedByTheUqRoute() = runBlocking {
        val asked = mutableListOf<String>()
        val got = fetcher { url ->
            asked += url
            if (url.endsWith("quality=uq")) response(url, "hq") else response(url, null, 404)
        }.fetch(listOf(base), emptyList(), null, Quality.UQ, above = Quality.SQ)
        assertEquals(Quality.HQ, got?.tier)
        assertEquals(listOf("$base?hq=true&quality=uq"), asked)
    }

    @After fun reset() = QualityLadder.forgetRefusal()

    @Test fun uqUsesStreamingAndNeverDownload() = runBlocking {
        val asked = mutableListOf<String>()
        val got = fetcher { url -> asked += url; response(url, "uq") }
            .fetch(listOf(base), emptyList(), null, Quality.UQ)
        assertEquals(Quality.UQ, got?.tier)
        assertEquals(listOf("$base?hq=true&quality=uq"), asked)
    }

    @Test fun anotherUqRouteIsTriedBeforeAcceptingLowerQuality() = runBlocking {
        val asked = mutableListOf<String>()
        val got = fetcher { url -> asked += url; response(url, if (url.contains("r1")) "hq" else "uq") }
            .fetch(listOf(base, mirror), emptyList(), null, Quality.UQ)
        assertEquals(Quality.UQ, got?.tier)
        assertEquals(2, asked.size)
        assertTrue(asked.all { it.endsWith("quality=uq") })
    }

    @Test fun unavailableUqFallsBackToHqWithAnHonestLabel() = runBlocking {
        val asked = mutableListOf<String>()
        val got = fetcher { url ->
            asked += url
            if (url.endsWith("quality=uq")) response(url, null, 403) else response(url, "hq")
        }.fetch(listOf(base), emptyList(), null, Quality.UQ)
        assertEquals(Quality.HQ, got?.tier)
        assertTrue(asked.none { it.contains("/download/") && it.contains("quality=uq") })
        assertTrue(asked.last().endsWith("quality=hq"))
    }

    @Test fun anUpgradeNeverReplacesHqWithHqOrSq() = runBlocking {
        val asked = mutableListOf<String>()
        val got = fetcher { url -> asked += url; response(url, "hq") }
            .fetch(listOf(base), emptyList(), null, Quality.UQ, above = Quality.HQ)
        assertNull(got)
        assertEquals(1, asked.size)
        assertTrue(asked.single().endsWith("quality=uq"))
    }

    @Test fun hqStorageDoesNotPreventTryingLiveUq() = runBlocking {
        val asked = mutableListOf<String>()
        val got = fetcher { url ->
            asked += url
            if (url == stored) response(url, "hq") else response(url, null, 404)
        }.fetch(listOf(base), listOf(stored), Quality.HQ, Quality.UQ)
        assertEquals(Quality.HQ, got?.tier)
        assertTrue(asked.first().endsWith("quality=uq"))
        assertEquals(stored, asked.last())
    }

    @Test fun downloadCandidatesKeepTheirActualTier() = runBlocking {
        val descriptor = """{"candidates":[{"kind":"progressive","quality":"sq","url":"https://example.test/sq"},{"kind":"progressive","quality":"hq","url":"https://example.test/hq"}]}""".encodeToByteArray()
        val got = fetcher { url ->
            if (url.contains("/download/")) response(url, null, bytes = descriptor) else response(url, null, 404)
        }.fetch(listOf(base), emptyList(), null, Quality.HQ)
        assertEquals(Quality.HQ, got?.tier)
    }

    @Test fun cancellationStopsRequestsInsteadOfFallingBack() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                fetcher { throw CancellationException("closed") }
                    .fetch(listOf(base, mirror), emptyList(), null, Quality.UQ)
            }
        }
    }

    @Test fun sqStillPlaysWhenBothUpperTiersAreUnavailable() = runBlocking {
        val got = fetcher { url ->
            if (url.endsWith("quality=sq")) response(url, "sq") else response(url, null, 404)
        }.fetch(listOf(base), emptyList(), null, Quality.UQ)
        assertEquals(Quality.SQ, got?.tier)
    }

    @Test fun staleStorageMetadataCannotLabelSqAudioAsUq() = runBlocking {
        val got = fetcher { url ->
            if (url == stored) response(url, "sq") else response(url, null, 404)
        }.fetch(listOf(base), listOf(stored), Quality.UQ, Quality.UQ)
        assertEquals(Quality.SQ, got?.tier)
    }
}
