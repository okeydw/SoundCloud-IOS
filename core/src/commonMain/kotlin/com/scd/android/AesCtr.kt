package com.scd.android

object AesCtr {
    private val sbox = IntArray(256)
    private val rcon = intArrayOf(0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1B, 0x36)

    init {
        val log = IntArray(256)
        val exp = IntArray(256)
        var a = 1
        for (i in 0 until 255) {
            exp[i] = a
            log[a] = i
            a = (a xor (a shl 1)).let { if (it and 0x100 != 0) it xor 0x11B else it } and 0xFF
        }
        for (i in 0 until 256) {
            val inv = if (i == 0) 0 else exp[(255 - log[i]) % 255]
            var s = inv
            var q = inv
            for (k in 0 until 4) {
                s = ((s shl 1) or (s ushr 7)) and 0xFF
                q = q xor s
            }
            sbox[i] = (q xor 0x63) and 0xFF
        }
    }

    private fun expandKey(key: ByteArray): IntArray {
        val rk = IntArray(176)
        for (i in 0 until 16) rk[i] = key[i].toInt() and 0xFF
        var i = 16
        var r = 0
        val t = IntArray(4)
        while (i < 176) {
            for (j in 0 until 4) t[j] = rk[i - 4 + j]
            if (i % 16 == 0) {
                val tmp = t[0]
                t[0] = sbox[t[1]] xor rcon[r++]
                t[1] = sbox[t[2]]
                t[2] = sbox[t[3]]
                t[3] = sbox[tmp]
            }
            for (j in 0 until 4) {
                rk[i] = rk[i - 16] xor t[j]
                i++
            }
        }
        return rk
    }

    private fun xtime(v: Int): Int {
        val s = v shl 1
        return (if (s and 0x100 != 0) s xor 0x11B else s) and 0xFF
    }

    private fun mul(aByte: Int, b: Int): Int {
        var a = aByte
        var bb = b
        var res = 0
        while (bb != 0) {
            if (bb and 1 != 0) res = res xor a
            a = xtime(a)
            bb = bb ushr 1
        }
        return res and 0xFF
    }

    private fun encryptBlock(block: IntArray, rk: IntArray) {
        val s = IntArray(16)
        for (i in 0 until 16) s[i] = block[i] xor rk[i]
        for (round in 1..10) {
            for (i in 0 until 16) s[i] = sbox[s[i]]
            val t = s.copyOf()
            for (r in 0 until 4) for (c in 0 until 4) s[r + 4 * c] = t[r + 4 * ((c + r) % 4)]
            if (round != 10) {
                for (c in 0 until 4) {
                    val c0 = s[4 * c]; val c1 = s[4 * c + 1]; val c2 = s[4 * c + 2]; val c3 = s[4 * c + 3]
                    s[4 * c] = mul(c0, 2) xor mul(c1, 3) xor c2 xor c3
                    s[4 * c + 1] = c0 xor mul(c1, 2) xor mul(c2, 3) xor c3
                    s[4 * c + 2] = c0 xor c1 xor mul(c2, 2) xor mul(c3, 3)
                    s[4 * c + 3] = mul(c0, 3) xor c1 xor c2 xor mul(c3, 2)
                }
            }
            for (i in 0 until 16) s[i] = s[i] xor rk[16 * round + i]
        }
        for (i in 0 until 16) block[i] = s[i]
    }

    fun applyKeystream(buf: ByteArray, start: Int, len: Int, key: ByteArray, iv: ByteArray) {
        if (len == 0) return
        require(key.size == 16) { "key length ${key.size}" }
        require(iv.size == 16) { "iv length ${iv.size}" }
        val rk = expandKey(key)
        val counter = IntArray(16) { iv[it].toInt() and 0xFF }
        val block = IntArray(16)
        val keystream = IntArray(16)
        var off = 0
        while (off < len) {
            for (i in 0 until 16) block[i] = counter[i]
            encryptBlock(block, rk)
            for (i in 0 until 16) keystream[i] = block[i]
            val n = minOf(16, len - off)
            for (i in 0 until n) {
                val idx = start + off + i
                buf[idx] = (buf[idx].toInt() xor keystream[i]).toByte()
            }
            var c = 15
            while (c >= 0) {
                counter[c] = (counter[c] + 1) and 0xFF
                if (counter[c] != 0) break
                c--
            }
            off += 16
        }
    }

    fun decrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val out = data.copyOf()
        applyKeystream(out, 0, out.size, key, iv)
        return out
    }
}
