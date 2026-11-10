package com.scd.android

class Sha1 {
    private val h = intArrayOf(0x67452301, -0x10325477, -0x67452302, 0x10325476, -0x3c2d1e10)
    private val block = ByteArray(64)
    private var blockLen = 0
    private var total = 0L
    private val w = IntArray(80)

    fun update(data: ByteArray) {
        total += data.size
        for (b in data) {
            block[blockLen++] = b
            if (blockLen == 64) {
                compress()
                blockLen = 0
            }
        }
    }

    fun digest(): ByteArray {
        val bits = total * 8
        val pad = ByteArray(if (blockLen < 56) 56 - blockLen else 120 - blockLen).also { it[0] = 0x80.toByte() }
        val saved = total
        update(pad)
        update(ByteArray(8) { (bits ushr (56 - 8 * it)).toByte() })
        total = saved
        return ByteArray(20) { i -> (h[i / 4] ushr (24 - 8 * (i % 4))).toByte() }
    }

    private fun compress() {
        for (t in 0 until 16) {
            w[t] = ((block[t * 4].toInt() and 0xff) shl 24) or ((block[t * 4 + 1].toInt() and 0xff) shl 16) or
                ((block[t * 4 + 2].toInt() and 0xff) shl 8) or (block[t * 4 + 3].toInt() and 0xff)
        }
        for (t in 16 until 80) w[t] = (w[t - 3] xor w[t - 8] xor w[t - 14] xor w[t - 16]).rotateLeft(1)
        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]; var e = h[4]
        for (t in 0 until 80) {
            val (f, k) = when {
                t < 20 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                t < 40 -> (b xor c xor d) to 0x6ED9EBA1
                t < 60 -> ((b and c) or (b and d) or (c and d)) to -0x70e44324
                else -> (b xor c xor d) to -0x359d3e2a
            }
            val temp = a.rotateLeft(5) + f + e + k + w[t]
            e = d; d = c; c = b.rotateLeft(30); b = a; a = temp
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e
    }
}

fun hexOf(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

class WebUrl(val scheme: String, val host: String, val path: String, val rawQuery: String) {
    fun query(): Map<String, String> = rawQuery.split('&').mapNotNull {
        val k = it.substringBefore('=')
        if (k.isEmpty()) null else percentDecode(k) to percentDecode(it.substringAfter('=', ""))
    }.toMap()

    companion object {
        fun parse(input: String): WebUrl? {
            val raw = input.trim().let { if (it.contains("://")) it else "https://$it" }
            val scheme = raw.substringBefore("://").lowercase()
            val rest = raw.substringAfter("://").substringBefore('#')
            val authority = rest.substringBefore('/').substringBefore('?')
            val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
            if (host.isEmpty() || host.any { it.isWhitespace() }) return null
            val afterHost = rest.substring(authority.length)
            val path = afterHost.substringBefore('?').ifEmpty { "/" }
            val query = afterHost.substringAfter('?', "")
            return WebUrl(scheme, host, percentDecode(path), query)
        }
    }
}

fun percentDecode(s: String): String {
    if ('%' !in s && '+' !in s) return s
    val out = ArrayList<Byte>(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%' && i + 2 < s.length && s.substring(i + 1, i + 3).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            out += s.substring(i + 1, i + 3).toInt(16).toByte()
            i += 3
            continue
        }
        if (c == '+') out += ' '.code.toByte() else c.toString().encodeToByteArray().forEach { out += it }
        i++
    }
    return out.toByteArray().decodeToString()
}
