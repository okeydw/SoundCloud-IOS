package com.scd.android

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CencTest {
    private val key = "0123456789abcdef".encodeToByteArray()

    private fun u32(v: Int): ByteArray =
        byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { ((s[it * 2].digitToInt(16) shl 4) or s[it * 2 + 1].digitToInt(16)).toByte() }

    private fun boxed(typ: String, body: ByteArray) = u32(body.size + 8) + typ.encodeToByteArray() + body
    private fun full(typ: String, flags: Int, body: ByteArray) = boxed(typ, u32(flags) + body)

    private fun segment(
        tfhdFlags: Int,
        tfhdFields: List<Int>,
        trunFlags: Int,
        perSample: List<List<Int>>,
        ivs: List<ByteArray>,
        samples: List<ByteArray>,
    ): ByteArray {
        var tfhd = u32(1)
        for (f in tfhdFields) tfhd += u32(f)
        var trun = u32(perSample.size) + u32(0)
        for (fields in perSample) for (f in fields) trun += u32(f)
        var senc = u32(ivs.size)
        for (iv in ivs) senc += iv
        val traf = full("tfhd", tfhdFlags, tfhd) + full("trun", trunFlags, trun) + full("senc", 0, senc)
        return boxed("moof", boxed("traf", traf)) + boxed("mdat", samples.reduce { a, b -> a + b })
    }

    private fun iv(b: Int) = ByteArray(16) { b.toByte() }

    private fun encrypted(sample: ByteArray, iv: ByteArray) = AesCtr.decrypt(sample, key, iv)

    private fun mdatPayload(seg: ByteArray): ByteArray {
        var range: Pair<Int, Int>? = null
        Cenc.iterBoxes(seg, 0, seg.size) { typ, bo, be -> if (typ == "mdat") range = bo to be }
        val (ms, me) = range!!
        return seg.copyOfRange(ms + 8, me)
    }

    @Test
    fun aesMatchesFips197() {
        val k = hex("000102030405060708090a0b0c0d0e0f")
        val block = hex("00112233445566778899aabbccddeeff")
        val keystream = AesCtr.decrypt(ByteArray(16), k, block)
        assertEquals("69c4e0d86a7b0430d8cdb78070b4c55a", keystream.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') })
        val pt = "The quick brown fox".encodeToByteArray()
        val iv = hex("f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff")
        assertArrayEquals(pt, AesCtr.decrypt(AesCtr.decrypt(pt, key, iv), key, iv))
    }

    @Test
    fun perSampleSizesInTrunAreUsedWithTheirOwnIvs() {
        val ivs = listOf(iv(1), iv(2))
        val a = "first sample".encodeToByteArray()
        val b = "second".encodeToByteArray()
        val seg = segment(0x38, listOf(1024, 99, 0), 0x201, listOf(listOf(a.size), listOf(b.size)), ivs, listOf(encrypted(a, ivs[0]), encrypted(b, ivs[1])))
        assertArrayEquals(a + b, mdatPayload(Cenc.decryptSegment(seg, key)))
    }

    @Test
    fun aTailWithoutSizesInTrunTakesTheTfhdDefault() {
        val ivs = listOf(iv(3), iv(4))
        val a = "tail01".encodeToByteArray()
        val b = "tail02".encodeToByteArray()
        val seg = segment(0x2_0038, listOf(1024, 6, 0x0200_0000), 0x101, listOf(listOf(1024), listOf(1024)), ivs, listOf(encrypted(a, ivs[0]), encrypted(b, ivs[1])))
        assertArrayEquals(a + b, mdatPayload(Cenc.decryptSegment(seg, key)))
    }

    @Test
    fun aDefaultSizeAfterTheOptionalTfhdFieldsIsFound() {
        val ivs = listOf(iv(5))
        val a = "abcd".encodeToByteArray()
        val seg = segment(0x1 or 0x2 or 0x8 or 0x10, listOf(0, 0, 7, 1024, 4), 0x1, listOf(emptyList()), ivs, listOf(encrypted(a, ivs[0])))
        assertArrayEquals(a, mdatPayload(Cenc.decryptSegment(seg, key)))
    }

    @Test
    fun aSegmentWithNoSizeAnywhereIsRefused() {
        val seg = segment(0x8, listOf(1024), 0x101, listOf(listOf(1024)), listOf(iv(6)), listOf("abcd".encodeToByteArray()))
        val error = runCatching { Cenc.decryptSegment(seg, key) }.exceptionOrNull()
        assertTrue("$error", error is Cenc.MediaException && error.message!!.contains("no sample size"))
    }
}
