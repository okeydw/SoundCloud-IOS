package com.scd.android

internal object ClearMp4 {
    private const val MAX_METADATA = 2 * 1024 * 1024

    fun needsRepair(prefix: ByteArray): Boolean {
        val moov = Cenc.findBox(prefix, listOf("moov")) ?: return false
        val box = prefix.copyOfRange(moov.first, moov.second)
        val moof = Cenc.findBox(prefix, listOf("moof")) ?: return false
        return encryptionOffsets(prefix.copyOfRange(moof.first, moof.second), clearTracks(box)).isNotEmpty()
    }

    private fun encryptionOffsets(box: ByteArray, tracks: Set<Int>): List<Int> = buildList {
        if (tracks.isEmpty()) return@buildList
        Cenc.iterBoxes(box, 8, box.size) { child, start, end ->
            if (child == "traf") {
                var trackId: Int? = null
                Cenc.iterBoxes(box, start + 8, end) { field, at, _ ->
                    if (field == "tfhd") trackId = id(box, at + 12)
                }
                if (trackId in tracks) Cenc.iterBoxes(box, start + 8, end) { field, at, _ ->
                    if (field == "senc" || field == "saiz" || field == "saio") add(at + 4)
                }
            }
        }
    }

    private fun id(bytes: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset > bytes.size - 4) return null
        return (0..3).fold(0) { value, i -> (value shl 8) or (bytes[offset + i].toInt() and 255) }
    }

    private fun clearTracks(moov: ByteArray): Set<Int> = buildSet {
        Cenc.iterBoxes(moov, 8, moov.size) { type, start, end ->
            if (type == "trak") {
                val track = moov.copyOfRange(start, end)
                val header = Cenc.findBox(track, listOf("trak", "tkhd"))
                val entries = Cenc.findBox(track, listOf("trak", "mdia", "minf", "stbl", "stsd"))
                if (header != null && entries != null && header.first + 9 <= header.second) {
                    var clear = false
                    var encrypted = false
                    Cenc.iterBoxes(track, entries.first + 16, entries.second) { entry, _, _ ->
                        if (entry == "mp4a") clear = true
                        if (entry == "enca" || entry == "encv") encrypted = true
                    }
                    val offset = header.first + if (track[header.first + 8].toInt() == 1) 28 else 20
                    if (clear && !encrypted) id(track, offset)?.let { add(it) }
                }
            }
        }
    }

    fun repair(bytes: ByteArray) {
        if (bytes.size < 8 || bytes.decodeToString(4, 8) != "ftyp") return
        var tracks = emptySet<Int>()
        Cenc.iterBoxes(bytes, 0, bytes.size) { type, start, end ->
            if (end - start <= MAX_METADATA && (type == "moov" || type == "moof")) {
                val box = bytes.copyOfRange(start, end)
                if (type == "moov") tracks = clearTracks(box)
                else for (offset in encryptionOffsets(box, tracks)) "free".encodeToByteArray().copyInto(bytes, start + offset)
            }
        }
    }
}
