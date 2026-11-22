package com.scd.android

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamCacheRetentionTest {
    private val files = listOf(
        StreamCacheFile("new", 30.0, 100),
        StreamCacheFile("active", 1.0, 100),
        StreamCacheFile("old", 10.0, 100),
        StreamCacheFile("middle", 20.0, 100),
    )

    @Test fun sizeLimitSkipsPlayingFilesAndAccountsForFailedDeletions() {
        val removals = mutableListOf<String>()
        pruneStreamCache(files, 200, null, false, { it == "active" }) {
            removals += it
            it != "old"
        }
        assertEquals(listOf("old", "middle", "new"), removals)
    }

    @Test fun expirationAndClearWorkWithAnUnlimitedSize() {
        val removed = mutableListOf<String>()
        pruneStreamCache(files, 0, 15.0, false, { it == "active" }, removed::add)
        assertEquals(listOf("old"), removed)
        removed.clear()
        pruneStreamCache(files, 0, null, true, { it == "active" }, removed::add)
        assertEquals(listOf("old", "middle", "new"), removed)
    }

    @Test fun unlimitedCacheRetainsEverythingByDefault() {
        pruneStreamCache(files, 0, null, false, { false }) { error("Unexpected deletion: $it") }
    }
}
