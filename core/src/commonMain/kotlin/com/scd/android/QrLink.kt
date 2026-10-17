package com.scd.android

object QrLink {
    private fun decode(s: String): String {
        val bytes = ArrayList<Byte>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length + 0 && i + 2 <= s.lastIndex) {
                val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (v != null) {
                    bytes.add(v.toByte())
                    i += 3
                    continue
                }
            }
            c.toString().encodeToByteArray().forEach { bytes.add(it) }
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun query(text: String): Map<String, String>? {
        val scheme = text.substringBefore(':', "")
        if (scheme.isEmpty() || scheme.lowercase() != "scd") return null
        val rest = text.substring(scheme.length + 1)
        if (!rest.startsWith("//") && !rest.startsWith("/")) return null
        val q = rest.substringAfter('?', "").substringBefore('#')
        val out = LinkedHashMap<String, String>()
        for (part in q.split('&')) {
            if (part.isEmpty()) continue
            val k = decode(part.substringBefore('='))
            val v = decode(part.substringAfter('=', ""))
            if (k !in out) out[k] = v
        }
        return out
    }

    fun parse(text: String): Pair<String, Boolean>? {
        val params = runCatching { query(text.trim()) }.getOrNull() ?: return null
        val token = params["token"] ?: params["claim"]
        if (token.isNullOrBlank() || token.length > 512) return null
        val pull = params["mode"]?.lowercase() == "pull"
        return token to pull
    }

    fun deepLink(token: String, push: Boolean): String =
        "scd://link?token=${uriEncode(token)}&mode=${if (push) "push" else "pull"}"

    private fun uriEncode(s: String): String {
        val sb = StringBuilder()
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xff
            val ch = c.toChar()
            if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch in "_-!.~'()*") sb.append(ch)
            else {
                sb.append('%')
                sb.append("0123456789ABCDEF"[c shr 4])
                sb.append("0123456789ABCDEF"[c and 0xf])
            }
        }
        return sb.toString()
    }
}
