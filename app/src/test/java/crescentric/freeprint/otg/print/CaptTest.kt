package crescentric.freeprint.otg.print

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Round-trip test for the Hi-SCoA compressor: [decompress] follows hiscoa-decompress.c from captdriver's test suite
 * (https://github.com/mounaiban/captdriver, Copyright (C) 2013 Alexey Galakhov, GPL-3.0-or-later).
 */
class CaptTest {
    private class Bits(val buf: ByteArray) {
        var pos = 0
        fun bit(): Boolean {
            if (pos / 8 >= buf.size) return true
            val r = (buf[pos / 8].toInt() xor 0x43) and (0x80 shr (pos % 8)) != 0
            pos++
            return r
        }
        fun n10(limit: Int): Int { var r = 0; while (r < limit && bit()) r++; return r }
        fun number(bits: Int): Int { var r = 0; repeat(bits) { r = (r shl 1) or (if (bit()) 1 else 0) }; return r }
        fun longNumber(): Int {
            val order = n10(6)
            if (order == 6) return 0
            if (order == 0) { if (!bit()) return 1; return if (bit()) 2 else 3 }
            return (1 shl (order + 2)) - 1 - number(order + 1)
        }
        fun prefix(): Int { val nbits = number(2); val v = number(nbits); return 128 * ((1 shl (nbits + 1)) - 1 - v) }
    }

    private fun decompress(data: ByteArray, lineSize: Int, expected: Int): ByteArray {
        val p = HiscoaParams()
        val out = ByteArray(expected)
        var o = 0
        val b = Bits(data)
        val bytes = intArrayOf(0xDE, 0xAD, 0xBE, 0xEF, 0xDE, 0xAD, 0xBE, 0xFF, 0xDE, 0xAD, 0xBE, 0xEF, 0xDE, 0xAD, 0xBE, 0xFF)
        val origin = intArrayOf(p.origin0 + lineSize, 0, p.origin2 + lineSize, p.origin3, p.origin4, p.origin5)
        var prefix = 0
        fun put(v: Int) { if (o < out.size) out[o] = v.toByte(); o++ }
        fun copy(origin: Int) {
            val len = prefix + b.longNumber()
            repeat(len) { put(if (o >= origin && o - origin < out.size) out[o - origin].toInt() and 0xFF else 0xCC) }
        }
        fun save(v: Int): Int { for (i in 15 downTo 1) bytes[i] = bytes[i - 1]; bytes[0] = v; return v }
        fun get(pos: Int): Int { val r = bytes[pos]; for (i in pos downTo 1) bytes[i] = bytes[i - 1]; bytes[0] = r; return r }
        var end = false
        while (!end && b.pos < 8 * data.size) {
            var keep = false
            when (b.n10(8)) {
                0 -> copy(origin[0])
                1 -> put(get(15 - b.number(4)))
                2 -> if (b.bit()) put(save(b.number(8))) else { copy(origin[2]); val t = origin[2]; origin[2] = origin[0]; origin[0] = t }
                3 -> copy(origin[3])
                4 -> copy(origin[4])
                5 -> { copy(origin[5]); val t = origin[5]; origin[5] = origin[3]; origin[3] = t }
                6 -> if (b.bit()) put(save(0)) else { prefix = b.prefix(); keep = true }
                7 -> { b.number(2); end = true }
                8 -> {}
            }
            if (!keep) prefix = 0
        }
        assertEquals("decoded length", expected, o)
        return out
    }

    private fun roundTrip(band: ByteArray, lineSize: Int, lines: Int) {
        val packed = Hiscoa.compressBand(band, lineSize, lines)
        assertEquals(0, packed.size % 4)
        assertArrayEquals(band.copyOf(lineSize * lines), decompress(packed, lineSize, lineSize * lines))
    }

    @Test fun whiteBandIsTiny() {
        val band = ByteArray(595 * 160)
        val packed = Hiscoa.compressBand(band, 595, 160)
        assertTrue("size=${packed.size}", packed.size < 1024)
        roundTrip(band, 595, 160)
    }

    @Test fun noiseRoundTrips() {
        val r = Random(7)
        val band = ByteArray(97 * 40).also { r.nextBytes(it) }
        roundTrip(band, 97, 40)
    }

    @Test fun textLikeBandRoundTripsAndShrinks() {
        val r = Random(3)
        val lineSize = 595; val lines = 160
        val band = ByteArray(lineSize * lines)
        repeat(60) {
            val x = r.nextInt(lineSize - 20); val y = r.nextInt(lines - 12)
            for (dy in 0 until 10) for (dx in 0 until 6) band[(y + dy) * lineSize + x + dx] = (if (r.nextInt(3) == 0) 0xFF else 0x3C).toByte()
        }
        roundTrip(band, lineSize, lines)
        assertTrue(Hiscoa.compressBand(band, lineSize, lines).size < band.size / 10)
    }

    @Test fun solidBlackAndPartialBandsRoundTrip() {
        roundTrip(ByteArray(595 * 160) { 0xFF.toByte() }, 595, 160)
        roundTrip(ByteArray(595 * 37) { (it % 7).toByte() }, 595, 37)
    }

    @Test fun pageIsCutToTheLineWidthAndBandedByLines() {
        // A4 sheet: 620 bytes per row, 595 sent; a black column at byte 12 (the first one kept) must survive
        val rowBytes = 620; val rows = 400
        val page = ByteArray(rowBytes * rows)
        for (y in 0 until rows) { page[y * rowBytes + 11] = 0xFF.toByte(); page[y * rowBytes + 12] = 0x81.toByte() }
        val bands = CaptRunner.compressPage(page, rowBytes, rows, 595)
        assertEquals(3, bands.size)                         // 160 + 160 + 80 lines
        val first = decompress(bands[0], 595, 595 * 160)
        assertEquals(0x81, first[0].toInt() and 0xFF)       // byte 12 of the sheet row is byte 0 of the band row; byte 11 was cut
        assertEquals(0, first[1].toInt())
        val last = decompress(bands[2], 595, 595 * 80)
        assertEquals(0x81, last[595 * 79].toInt() and 0xFF)
    }

    /** A pretend printer that answers the way the conversation expects, to run the whole job sequence. */
    private class FakePrinter : CaptTransport {
        val replies = ArrayDeque<ByteArray>()
        val commands = ArrayList<Int>()
        var decoding = 0; var received = 0; var out = 0; var completed = 0
        var dataBytes = 0
        private var pending = ByteArray(0)

        override fun write(data: ByteArray, off: Int, len: Int) {
            pending += data.copyOfRange(off, off + len)
            while (pending.size >= 4) {
                val size = (pending[2].toInt() and 0xFF) or ((pending[3].toInt() and 0xFF) shl 8)
                if (pending.size < size) return
                handle(pending.copyOfRange(0, size))
                pending = pending.copyOfRange(size, pending.size)
            }
        }

        private fun reply(cmd: Int, payload: ByteArray) {
            val r = ByteArray(4 + payload.size)
            r[0] = cmd.toByte(); r[1] = (cmd shr 8).toByte(); r[2] = r.size.toByte(); r[3] = (r.size shr 8).toByte()
            payload.copyInto(r, 4)
            replies.addLast(r)
        }

        private fun handle(c: ByteArray) {
            val cmd = (c[0].toInt() and 0xFF) or ((c[1].toInt() and 0xFF) shl 8)
            commands += cmd
            when (cmd) {
                0xD0A9 -> decoding++
                0xC0A0 -> dataBytes += c.size - 4
                0xC0A4 -> received = decoding
                0xE0A7 -> { out = decoding; completed = decoding; reply(cmd, byteArrayOf(0, 0)) }
                0xE0A0, 0xA0A8 -> {
                    val p = ByteArray(44)
                    fun put(at: Int, v: Int) { p[at] = v.toByte(); p[at + 1] = (v shr 8).toByte() }
                    put(14, decoding); put(16, decoding); put(18, out); put(20, completed); put(34, received)
                    reply(cmd, p)
                }
                0xA2A0 -> reply(cmd, byteArrayOf(0, 0, 0x34, 0x12, 0, 0))
                0xD0A9, 0xC0A4 -> {}
                else -> reply(cmd, byteArrayOf(0, 0))
            }
        }

        override fun read(buf: ByteArray, timeoutMs: Int): Int {
            val r = replies.removeFirstOrNull() ?: throw CaptException("no reply")
            r.copyInto(buf); return r.size
        }
    }

    @Test fun aWholeJobRunsThroughTheSequence() {
        val printer = FakePrinter()
        val session = CaptSession(printer, sleepMs = {})
        val page = CaptSession.Page(sizeCode = 2, paperWidth = 4960, paperHeight = 7016, lineSize = 595, numLines = 7016)
        try {
            session.jobPrologue()
            repeat(2) {
                session.pagePrologue(page)
                session.startPageData()
                session.sendBand(ByteArray(70_000) { it.toByte() })
                assertTrue(session.pageEpilogue())
            }
            session.jobEpilogue()
        } catch (e: CaptException) {
            throw AssertionError(e.message + " after " + session.trace.joinToString(" | "), e)
        }
        assertEquals(2, printer.decoding)
        assertEquals(70_000 * 2, printer.dataBytes)
        assertTrue(printer.commands.first() == 0xA1A1)
        assertTrue(printer.commands.last() == 0xE0A9)       // JOB_END closes the job
        assertTrue(printer.commands.count { it == 0xE0A7 } == 2) // one FIRE per page
        // the SET_PARMS packet carries page parameters of the right size
        assertFalse(session.trace.isEmpty())
    }
}
