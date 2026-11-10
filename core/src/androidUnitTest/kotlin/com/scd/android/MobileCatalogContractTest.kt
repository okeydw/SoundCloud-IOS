package com.scd.android

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MobileCatalogContractTest {
    @Test fun pendingCollectionPreservesRetryAfterAndTerminalErrors() {
        fun pending(error: String) = MobileApiContract.collectionFailure(
            """{"collection":[],"sync":{"status":"refreshing","lastCompletedAt":null,"retryAfterSeconds":7,"errorCode":"$error"}}""",
        )!!
        assertEquals("collection_refresh_pending", pending("").serviceCode)
        assertEquals(7L, pending("").retryAfterSeconds)
        assertEquals("soundcloud_reauthorization_required", pending("auth_required").serviceCode)
        assertEquals("collection_not_found", pending("not_found").serviceCode)
        assertEquals("collection_forbidden", pending("forbidden").serviceCode)
        assertNull(MobileApiContract.collectionFailure("""{"collection":[],"sync":{"status":"ready"}}"""))
    }
    @Test fun oneMalformedLinkDoesNotHideTheGoodLinks() {
        val links = Api.decodeWebProfiles(Json.parseToJsonElement(
            """{"collection":[{"url":"https://example.com"},{"url":[]},null]}""",
        ))
        assertEquals(listOf("https://example.com"), links.map { it.url })
    }
    @Test fun invalidResponsesAreRetryableErrorsRatherThanEmptyProfiles() {
        for (body in listOf("null", "{}", "[null]", """[{"url":[]}]""")) {
            try {
                Api.decodeWebProfiles(Json.parseToJsonElement(body))
                fail("Invalid links response was accepted: $body")
            } catch (_: kotlinx.io.IOException) { }
        }
        assertTrue(Api.decodeWebProfiles(Json.parseToJsonElement("[]")).isEmpty())
    }
    @Test fun mp4BoxLengthsCannotOverflowOrEscapeTheirContainer() {
        val bytes = byteArrayOf(0x7f, -1, -1, -1, 109, 111, 111, 102)
        Cenc.iterBoxes(bytes, 0, bytes.size) { _, _, _ -> fail("Oversized box") }
        Cenc.iterBoxes(bytes, -1, bytes.size) { _, _, _ -> fail("Invalid region") }
        Cenc.iterBoxes(bytes, 0, bytes.size + 1) { _, _, _ -> fail("Invalid region") }
        val zero = ByteArray(8)
        var count = 0
        Cenc.iterBoxes(zero, 0, zero.size) { _, from, to ->
            assertEquals(0, from)
            assertEquals(8, to)
            count++
        }
        assertEquals(1, count)
    }
}
