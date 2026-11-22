package com.scd.android

import kotlinx.io.Buffer
import kotlinx.io.readByteArray

internal object AacSeekIndex {
    private class Invalid : Exception()
    private fun checkFile(ok: Boolean) { if (!ok) throw Invalid() }

    private class Box(val source: ByteArray, val type: String, val start: Int, val body: Int, val end: Int) {
        fun word(offset: Int): Int {
            checkFile(offset >= 0 && offset <= end - body - 4)
            return source.wordAt(body + offset)
        }

        fun children(skip: Int = 0) = boxes(source, body + skip, end)
        fun child(type: String) = children().singleOrNull { it.type == type } ?: throw Invalid()
        fun copy() = source.copyOfRange(start, end)
        fun version() = word(0) ushr 24

        fun withDuration(offset: Int, duration: Long): ByteArray {
            val wide = version() == 1
            checkFile(version() in 0..1 && duration >= 0 && (wide || duration <= 0xffffffffL))
            word(offset + if (wide) 4 else 0)
            return copy().also {
                val at = body - start + offset
                if (wide) it.putWord(at, (duration ushr 32).toInt())
                it.putWord(at + if (wide) 4 else 0, duration.toInt())
            }
        }
    }

    private fun ByteArray.wordAt(at: Int): Int {
        var value = 0
        for (i in at until at + 4) value = (value shl 8) or (this[i].toInt() and 255)
        return value
    }

    private fun ByteArray.putWord(at: Int, value: Int) {
        for (i in 0..3) this[at + i] = (value ushr (24 - 8 * i)).toByte()
    }

    private fun boxes(source: ByteArray, from: Int, end: Int): Sequence<Box> = sequence {
        var at = from
        while (at < end) {
            checkFile(end - at >= 8)
            var length = source.wordAt(at).toLong() and 0xffffffffL
            var header = 8
            if (length == 1L) {
                checkFile(end - at >= 16 && source.wordAt(at + 8) == 0)
                length = source.wordAt(at + 12).toLong() and 0xffffffffL
                header = 16
            } else if (length == 0L) length = (end - at).toLong()
            checkFile(length in header.toLong()..(end - at).toLong())
            val next = at + length.toInt()
            yield(Box(source, source.decodeToString(at + 4, at + 8), at, at + header, next))
            at = next
        }
    }

    private fun atom(type: String, write: Buffer.() -> Unit): ByteArray {
        val payload = Buffer().apply(write)
        return Buffer().apply {
            writeInt((payload.size + 8).toInt())
            write(type.encodeToByteArray())
            transferFrom(payload)
        }.readByteArray()
    }

    private fun Box.rebuild(changes: Map<String, ByteArray?>) = atom(type) {
        for (box in children()) {
            if (box.type in changes) changes[box.type]?.let { write(it) }
            else write(box.source, box.start, box.end)
        }
    }

    private data class Chunk(val offset: Int, val size: Int)

    private class Index(private val firstOffset: Int) {
        val chunks = mutableListOf<Chunk>()
        private val sizes = Buffer()
        private val times = Buffer()
        private val mapping = Buffer()
        private val offsets = Buffer()
        private var previousDuration = 0
        private var consecutive = 0
        private var previousCount = 0
        var samples = 0
            private set
        var duration = 0L
            private set
        var payload = 0
            private set

        fun addSample(size: Int, ticks: Int) {
            checkFile(size > 0 && ticks > 0 && samples < 3_000_000)
            if (ticks != previousDuration) {
                flushTime()
                previousDuration = ticks
            }
            consecutive++
            samples++
            duration += ticks
            sizes.writeInt(size)
        }

        private fun flushTime() {
            if (consecutive > 0) {
                times.writeInt(consecutive)
                times.writeInt(previousDuration)
                consecutive = 0
            }
        }

        fun addChunk(start: Int, size: Int, count: Int) {
            checkFile(chunks.size < 100_000 && size <= Int.MAX_VALUE - firstOffset - payload)
            chunks.add(Chunk(start, size))
            offsets.writeInt(firstOffset + payload)
            payload += size
            if (count != previousCount) {
                mapping.writeInt(chunks.size)
                mapping.writeInt(count)
                mapping.writeInt(1)
                previousCount = count
            }
        }

        fun sampleTable(description: Box): ByteArray {
            flushTime()
            fun table(type: String, entries: Buffer, width: Int) = atom(type) {
                writeInt(0)
                if (type == "stsz") writeInt(0)
                writeInt((entries.size / width).toInt())
                transferFrom(entries)
            }
            return atom("stbl") {
                write(description.copy())
                write(table("stts", times, 8))
                write(table("stsc", mapping, 12))
                write(table("stsz", sizes, 4))
                write(table("stco", offsets, 4))
            }
        }
    }

    private fun fragment(moof: Box, data: Box, defaults: Box, index: Index) {
        val traf = moof.child("traf")
        val header = traf.child("tfhd")
        val flags = header.word(0)
        checkFile(header.word(4) == defaults.word(4) && flags and 0x010000 == 0)
        var pos = 8
        fun next() = header.word(pos).also { pos += 4 }
        val base = if (flags and 1 != 0) {
            checkFile(next() == 0)
            next().toLong() and 0xffffffffL
        } else moof.start.toLong()
        checkFile((if (flags and 2 != 0) next() else defaults.word(8)) == 1)
        val ticks = if (flags and 8 != 0) next() else defaults.word(12)
        val size = if (flags and 16 != 0) next() else defaults.word(16)
        var cursor = base
        var previousEnd = data.body
        var found = false
        for (run in traf.children().filter { it.type == "trun" }) {
            val fields = run.word(0)
            val count = run.word(4)
            checkFile(count in 1..(3_000_000 - index.samples))
            var at = 8
            fun field() = run.word(at).also { at += 4 }
            if (fields and 1 != 0) cursor = base + field()
            if (fields and 4 != 0) field()
            checkFile(cursor >= previousEnd && cursor < data.end)
            val start = cursor.toInt()
            repeat(count) {
                val sampleTicks = if (fields and 0x100 != 0) field() else ticks
                val sampleSize = if (fields and 0x200 != 0) field() else size
                if (fields and 0x400 != 0) field()
                if (fields and 0x800 != 0) checkFile(field() == 0)
                cursor += sampleSize
                checkFile(sampleSize > 0 && cursor <= data.end)
                index.addSample(sampleSize, sampleTicks)
            }
            checkFile(at == run.end - run.body)
            previousEnd = cursor.toInt()
            index.addChunk(start, previousEnd - start, count)
            found = true
        }
        checkFile(found)
    }

    fun normalize(bytes: ByteArray): ByteArray? {
        if (bytes.size < 8 || bytes.decodeToString(4, 8) != "ftyp") return bytes
        return try { remux(bytes) } catch (_: Invalid) { null }
    }

    private fun remux(bytes: ByteArray): ByteArray {
        val top = boxes(bytes, 0, bytes.size)
        if (top.none { it.type == "moof" }) return bytes
        val movie = top.singleOrNull { it.type == "moov" } ?: throw Invalid()
        checkFile(movie.end - movie.start <= 8 * 1024 * 1024)
        val track = movie.children().singleOrNull { it.type == "trak" } ?: return bytes
        val media = track.child("mdia")
        val info = media.child("minf")
        val table = info.child("stbl")
        val descriptions = table.child("stsd")
        if (descriptions.word(4) != 1 || descriptions.children(8).singleOrNull()?.type != "mp4a") return bytes
        if (table.child("stsz").word(8) != 0) return bytes
        val edits = track.children().singleOrNull { it.type == "edts" }
        val edit = edits?.child("elst")
        var trimStart = 0
        if (edit != null) {
            val wide = edit.version() == 1
            if (edit.version() !in 0..1 || edit.word(4) != 1 || edit.word(if (wide) 24 else 16) != 0x10000) return bytes
            if (wide && edit.word(16) != 0) return bytes
            trimStart = edit.word(if (wide) 20 else 12)
            if (trimStart < 0) return bytes
            val declared = edit.word(if (wide) 12 else 8)
            if ((wide && edit.word(8) != 0) || declared != 0) return bytes
        }
        val defaults = movie.child("mvex").child("trex")
        val mvhd = movie.child("mvhd")
        val tkhd = track.child("tkhd")
        val mdhd = media.child("mdhd")
        fun timeOffset(box: Box) = when (box.version()) { 0 -> 12; 1 -> 20; else -> throw Invalid() }
        checkFile(tkhd.word(timeOffset(tkhd)) == defaults.word(4))
        val mediaScale = mdhd.word(timeOffset(mdhd))
        val movieScale = mvhd.word(timeOffset(mvhd))
        checkFile(movieScale > 0 && mediaScale > 0)
        val ftyp = atom("ftyp") { write("M4A ".encodeToByteArray()); writeInt(0); write("isomM4A mp42".encodeToByteArray()) }
        val index = Index(ftyp.size + 8)
        var pending: Box? = null
        for (box in top) {
            when (box.type) {
                "moof" -> { checkFile(pending == null); pending = box }
                "mdat" -> {
                    val moof = pending ?: throw Invalid()
                    fragment(moof, box, defaults, index)
                    pending = null
                }
            }
        }
        checkFile(pending == null && index.samples > 0 && index.duration <= Long.MAX_VALUE / movieScale)
        checkFile(index.duration > trimStart)
        val movieDuration = (index.duration - trimStart) * movieScale / mediaScale
        val newEdits = edit?.let { edits.rebuild(mapOf("elst" to it.withDuration(8, movieDuration))) }
        val newInfo = info.rebuild(mapOf("stbl" to index.sampleTable(descriptions)))
        val newMedia = media.rebuild(mapOf("minf" to newInfo, "mdhd" to mdhd.withDuration(timeOffset(mdhd) + 4, index.duration)))
        val newTrack = track.rebuild(mapOf("edts" to newEdits, "mdia" to newMedia, "tkhd" to tkhd.withDuration(timeOffset(tkhd) + 8, movieDuration)))
        val newMovie = movie.rebuild(mapOf("mvex" to null, "trak" to newTrack, "mvhd" to mvhd.withDuration(timeOffset(mvhd) + 4, movieDuration)))
        val total = ftyp.size.toLong() + 8 + index.payload + newMovie.size
        checkFile(total <= Int.MAX_VALUE)
        val out = ByteArray(total.toInt())
        ftyp.copyInto(out)
        out.putWord(ftyp.size, 8 + index.payload)
        "mdat".encodeToByteArray().copyInto(out, ftyp.size + 4)
        var at = ftyp.size + 8
        for ((offset, size) in index.chunks) {
            bytes.copyInto(out, at, offset, offset + size)
            at += size
        }
        newMovie.copyInto(out, at)
        return out
    }
}
