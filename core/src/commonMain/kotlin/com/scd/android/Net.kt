package com.scd.android

import kotlinx.io.IOException

enum class HttpProfile { BASE, API, MUTATE, SLOW, PROBE, SHORT_LINK, LOGOUT, REFRESH, VOLUME, QUICK, ANON, ANON_HOME }

class HttpBody(val bytes: ByteArray, val contentType: String?) {
    companion object {
        fun json(text: String) = HttpBody(text.encodeToByteArray(), "application/json; charset=utf-8")
        val EMPTY get() = HttpBody(ByteArray(0), null)
    }
}

const val PROBE_HEADER = "x-scd-probe"

class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: List<Pair<String, String>> = emptyList(),
    val body: HttpBody? = null,
    val profile: HttpProfile = HttpProfile.BASE,
    val forceNetwork: Boolean = false,
    val queued: Boolean = false,
    val discardBody: Boolean = false,
)

class HttpResponse(
    val code: Int,
    private val headers: Map<String, String>,
    val body: ByteArray,
    val finalUrl: String,
) {
    val isSuccessful: Boolean get() = code in 200..299

    fun header(name: String): String? = headers[name.lowercase()]

    fun text(): String = body.decodeToString()

    val host: String get() = finalUrl.substringAfter("://").substringBefore('/').substringBefore(':')
}

interface HttpStream {
    val code: Int
    fun header(name: String): String?
    fun read(buf: ByteArray): Int
}

interface HttpEngine {
    suspend fun execute(req: HttpRequest): HttpResponse
    fun <T> stream(req: HttpRequest, block: (HttpStream) -> T): T
}

open class UnreachableException(message: String) : IOException(message)

class ApiHttpException(val code: Int, val body: String) : IOException("API $code: $body")

object Http {
    lateinit var engine: HttpEngine

    val ready: Boolean get() = ::engine.isInitialized

    suspend fun execute(req: HttpRequest): HttpResponse = engine.execute(req)

    fun <T> stream(req: HttpRequest, block: (HttpStream) -> T): T = engine.stream(req, block)
}

fun formEncode(s: String): String {
    val sb = StringBuilder()
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xff
        val ch = c.toChar()
        when {
            ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '.' || ch == '-' || ch == '*' || ch == '_' -> sb.append(ch)
            ch == ' ' -> sb.append('+')
            else -> {
                sb.append('%')
                sb.append(HEX_UPPER[c shr 4])
                sb.append(HEX_UPPER[c and 0xf])
            }
        }
    }
    return sb.toString()
}

private const val HEX_UPPER = "0123456789ABCDEF"
private const val HEX_LOWER = "0123456789abcdef"

fun ByteArray.toHex(): String {
    val sb = StringBuilder(size * 2)
    for (b in this) {
        val c = b.toInt() and 0xff
        sb.append(HEX_LOWER[c shr 4])
        sb.append(HEX_LOWER[c and 0xf])
    }
    return sb.toString()
}

class Sha256 {
    private val h = intArrayOf(
        0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6,
        0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19,
    )
    private val block = ByteArray(64)
    private var blockLen = 0
    private var total = 0L
    private val w = IntArray(64)

    fun update(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        var i = offset
        val end = offset + length
        total += length
        while (i < end) {
            block[blockLen++] = data[i++]
            if (blockLen == 64) {
                compress()
                blockLen = 0
            }
        }
    }

    fun digest(): ByteArray {
        val bits = total * 8
        update(byteArrayOf(0x80.toByte()))
        while (blockLen != 56) update(byteArrayOf(0))
        val len = ByteArray(8) { (bits ushr (56 - 8 * it)).toByte() }
        update(len)
        val out = ByteArray(32)
        for (j in 0 until 8) {
            out[j * 4] = (h[j] ushr 24).toByte()
            out[j * 4 + 1] = (h[j] ushr 16).toByte()
            out[j * 4 + 2] = (h[j] ushr 8).toByte()
            out[j * 4 + 3] = h[j].toByte()
        }
        return out
    }

    private fun compress() {
        for (t in 0 until 16) {
            w[t] = ((block[t * 4].toInt() and 0xff) shl 24) or
                ((block[t * 4 + 1].toInt() and 0xff) shl 16) or
                ((block[t * 4 + 2].toInt() and 0xff) shl 8) or
                (block[t * 4 + 3].toInt() and 0xff)
        }
        for (t in 16 until 64) {
            val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
            val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
            w[t] = w[t - 16] + s0 + w[t - 7] + s1
        }
        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
        for (t in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = hh + s1 + ch + K[t] + w[t]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = s0 + maj
            hh = g; g = f; f = e; e = d + t1
            d = c; c = b; b = a; a = t1 + t2
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d
        h[4] += e; h[5] += f; h[6] += g; h[7] += hh
    }

    private companion object {
        val K = intArrayOf(
            0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
            -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
            -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
            -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
        )
    }
}
