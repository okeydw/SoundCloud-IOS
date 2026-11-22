package com.scd.android

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class AacSeekIndexTest {
    private fun broken() = checkNotNull(javaClass.getResourceAsStream("/broken-aac-index.m4a")).use { it.readBytes() }
    private fun ByteArray.field(at: Int) = ByteBuffer.wrap(this).getInt(at)
    private fun ByteArray.box(vararg path: String) = checkNotNull(Cenc.findBox(this, path.toList())).first

    @Test fun rebuildsAContinuousTimelineAndSeekTableWithoutChangingPackets() {
        val input = broken()
        val repaired = checkNotNull(AacSeekIndex.normalize(input))
        val table = listOf("moov", "trak", "mdia", "minf", "stbl")
        fun entry(type: String) = repaired.box(*(table + type).toTypedArray())
        assertNull(Cenc.findBox(repaired, listOf("moof")))
        assertEquals(39, repaired.field(entry("stsz") + 16))
        assertEquals(39424, repaired.field(repaired.box("moov", "trak", "mdia", "mdhd") + 24))
        val times = entry("stts")
        assertEquals(listOf(2, 38, 1024, 1, 512), (12..28 step 4).map { repaired.field(times + it) })
        val mapping = entry("stsc")
        assertEquals(listOf(2, 1, 9, 1, 5, 3, 1), (12..36 step 4).map { repaired.field(mapping + it) })
        val offsets = entry("stco")
        var chunk = 0
        Cenc.iterBoxes(input, 0, input.size) { type, start, end ->
            if (type == "mdat") {
                val at = repaired.field(offsets + 16 + 4 * chunk++)
                assertArrayEquals(input.copyOfRange(start + 8, end), repaired.copyOfRange(at, at + end - start - 8))
            }
        }
        assertEquals(5, chunk)
        assertSame(repaired, AacSeekIndex.normalize(repaired))
    }

    @Test fun rejectsMissingPayloadAndOutOfRangeSampleReferences() {
        val input = broken()
        val mdat = input.box("mdat")
        assertNull(AacSeekIndex.normalize(input.copyOf(mdat + 12)))
        val run = input.box("moof", "traf", "trun")
        ByteBuffer.wrap(input).putInt(run + 16, Int.MAX_VALUE)
        assertNull(AacSeekIndex.normalize(input))
    }
}
