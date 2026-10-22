package crescentric.freeprint.otg.print

import java.io.ByteArrayOutputStream
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// ---------------------------------------------------------------------------------------------------------------------
// Test-only decoder, written from the format description (section 7 and 8) and independent of the encoder's helpers.
// ---------------------------------------------------------------------------------------------------------------------

private class DecodedBand(
    val number: Int, val width: Int, val height: Int, val recordLen: Int,
    val block: ByteArray, val storedChecksum: Long, val pixels: ByteArray,
)

private class DecodedPage(val header: ByteArray, val bands: List<DecodedBand>, val footer: ByteArray)
private class DecodedJob(val pjl: String, val pages: List<DecodedPage>, val tail: ByteArray)

private var overlapCopiesSeen = 0

private fun decodeBlock(block: ByteArray, total: Int): ByteArray {
    fun u8(i: Int) = block[i].toInt() and 0xFF
    val lead = u8(0) or (u8(1) shl 8) or (u8(2) shl 16) or (u8(3) shl 24)
    val table = IntArray(64) { u8(4 + 2 * it) or (u8(5 + 2 * it) shl 8) }
    assertTrue("lead $lead too small", lead >= (table.max()))
    assertTrue("lead $lead > 128", lead <= 128)
    assertTrue(table.all { it >= 1 })
    val out = ByteArray(total)
    var o = 0
    var p = 4 + 128
    for (i in 0 until lead) out[o++] = block[p++]
    while (o < total) {
        val b = u8(p++)
        if (b and 0x80 == 0) {
            val run = (b and 0x7F) + 1
            for (i in 0 until run) out[o++] = block[p++]
        } else {
            val c = u8(p++)
            val dist = table[c and 0x3F]
            val n = 3 + ((b and 0x7F) or ((c and 0xC0) shl 1))
            if (n > dist) overlapCopiesSeen++
            assertTrue("copy reaches before start", dist <= o)
            for (i in 0 until n) { out[o] = out[o - dist]; o++ }
        }
    }
    assertEquals("block has trailing bytes or overran", block.size, p)
    assertEquals(total, o)
    return out
}

private fun decodeJob(s: ByteArray): DecodedJob {
    val uel = byteArrayOf(0x1B) + "%-12345X".toByteArray()
    assertEquals(9, uel.size)
    assertArrayEquals(uel, s.copyOfRange(0, 9))
    val marker = "@PJL ENTER LANGUAGE = QPDL\n".toByteArray()
    var i = 9
    while (!(s.copyOfRange(i, i + marker.size).contentEquals(marker))) i++
    i += marker.size
    val pjl = String(s, 0, i, Charsets.ISO_8859_1)
    val pages = ArrayList<DecodedPage>()
    fun be16(p: Int) = ((s[p].toInt() and 0xFF) shl 8) or (s[p + 1].toInt() and 0xFF)
    fun be32(p: Int) = (be16(p).toLong() shl 16) or be16(p + 2).toLong()
    while (s[i].toInt() == 0x00) {
        val header = s.copyOfRange(i, i + 17); i += 17
        val bands = ArrayList<DecodedBand>()
        while (s[i].toInt() == 0x0C) {
            val num = s[i + 1].toInt() and 0xFF
            val w = be16(i + 2); val h = be16(i + 4)
            assertEquals(0x11, s[i + 6].toInt())
            val len = be32(i + 7).toInt()
            assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xCD.toByte(), 0xAB.toByte(), 0x09), s.copyOfRange(i + 11, i + 15))
            val blockStart = i + 15
            val blockLen = len - 8
            val block = s.copyOfRange(blockStart, blockStart + blockLen)
            val sum = be32(blockStart + blockLen)
            val pixels = decodeBlock(block, (w / 8) * h)
            bands += DecodedBand(num, w, h, len, block, sum, pixels)
            i = blockStart + blockLen + 4
        }
        val footer = s.copyOfRange(i, i + 3); i += 3
        pages += DecodedPage(header, bands, footer)
    }
    return DecodedJob(pjl, pages, s.copyOfRange(i, s.size))
}

// ---------------------------------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------------------------------

private fun blank(w: Int, h: Int) = BitPage(w, h, ByteArray(((w + 7) / 8) * h))

private fun BitPage.set(x: Int, y: Int) {
    if (x in 0 until widthPx && y in 0 until heightPx) data[y * stride + x / 8] = (data[y * stride + x / 8].toInt() or (0x80 shr (x and 7))).toByte()
}

private fun BitPage.black(x: Int, y: Int): Boolean =
    x in 0 until widthPx && y in 0 until heightPx && (data[y * stride + x / 8].toInt() and (0x80 shr (x and 7))) != 0

private fun encode(pages: List<BitPage>, o: QpdlOptions): Pair<ByteArray, QpdlStats> {
    val bos = ByteArrayOutputStream()
    val stats = QpdlJob.write(bos, o, pages.iterator())
    return bos.toByteArray() to stats
}

private val FIXED = QpdlOptions(serviceDate = "20260101", userName = "tester", jobTitle = "Doc")

/** Expected printable area, row-major 1 = black, bandCount*128 rows of bandBytes bytes, built pixel by pixel. */
private fun expectedImage(p: BitPage, g: QpdlGeometry): ByteArray {
    val rows = g.bandCount * 128
    val img = ByteArray(rows * g.bandBytes)
    for (r in 0 until g.heightPx) for (px in 0 until g.widthPx - 112) {
        if (p.black(px + 112, r + 105)) {
            val idx = r * g.bandBytes + px / 8
            img[idx] = (img[idx].toInt() or (0x80 shr (px and 7))).toByte()
        }
    }
    return img
}

/** Rebuild the same picture from decoded bands: undo the inversion and the column-major order. */
private fun decodedImage(pg: DecodedPage, g: QpdlGeometry): ByteArray {
    val img = ByteArray(g.bandCount * 128 * g.bandBytes)
    for (b in pg.bands) for (x in 0 until g.bandBytes) for (y in 0 until 128) {
        img[(b.number * 128 + y) * g.bandBytes + x] = b.pixels[x * 128 + y].toInt().inv().toByte()
    }
    return img
}

private fun roundTrip(page: BitPage, o: QpdlOptions = FIXED): QpdlStats {
    val g = QpdlGeometry(o.paper)
    val (bytes, stats) = encode(listOf(page), o)
    val job = decodeJob(bytes)
    assertEquals(1, job.pages.size)
    assertArrayEquals(expectedImage(page, g), decodedImage(job.pages[0], g))
    return stats
}

private fun textLike(w: Int, h: Int, seed: Long): BitPage {
    val p = blank(w, h)
    val rnd = Random(seed)
    var y = 300
    while (y + 60 < h - 300) {
        var x = 300
        while (x + 40 < w - 300) {
            if (rnd.nextInt(6) != 0) {
                val glyph = IntArray(7) { rnd.nextInt(32) }
                for (gy in 0 until 7) for (gx in 0 until 5) if (glyph[gy] and (1 shl gx) != 0)
                    for (dy in 0 until 5) for (dx in 0 until 5) p.set(x + gx * 5 + dx, y + gy * 5 + dy)
            }
            x += 40
        }
        y += 70
    }
    return p
}

class SamsungQpdlTest {
    private val customOdd = QpdlOptions(paper = QpdlPaperSize.custom(203, 203), serviceDate = "20260101")

    @Test fun geometryA4AndLetter() {
        val a4 = QpdlGeometry(QpdlPaperSize.A4)
        assertEquals(4960, a4.widthPx); assertEquals(6912, a4.heightPx); assertEquals(620, a4.bandBytes); assertEquals(54, a4.bandCount)
        val lt = QpdlGeometry(QpdlPaperSize.LETTER)
        assertEquals(5104, lt.widthPx); assertEquals(6495, lt.heightPx); assertEquals(51, lt.bandCount)
        QpdlGeometry(QpdlPaperSize.LEDGER)
    }

    @Test fun pjlHeaderExactBytes() {
        val (bytes, _) = encode(emptyList(), FIXED)
        val expected = byteArrayOf(0x1B) + ("%-12345X" +
            "@PJL DEFAULT SERVICEDATE=20260101\n" +
            "@PJL SET USERNAME=\"tester\"\n" +
            "@PJL SET JOBNAME=\"Doc\"\n" +
            "@PJL SET JAMRECOVERY=OFF\n" +
            "@PJL SET DUPLEX=OFF\n" +
            "@PJL SET PAPERTYPE=OFF\n" +
            "@PJL SET ALTITUDE=LOW\n" +
            "@PJL SET DENSITY=3\n" +
            "@PJL SET RET=NORMAL\n" +
            "@PJL ENTER LANGUAGE = QPDL\n").toByteArray() +
            byteArrayOf(0x09, 0x1B) + "%-12345X".toByteArray()
        assertArrayEquals(expected, bytes)
    }

    @Test fun economodeDensityAndSanitising() {
        val o = FIXED.copy(economode = true, density = 5, jobTitle = "a\"b\nc")
        val text = QpdlJobWriter.pjlHeaderText(o)
        assertTrue(text.contains("@PJL SET ECONOMODE=ON\n@PJL SET JAMRECOVERY=OFF\n"))
        assertTrue(text.contains("DENSITY=5\n"))
        assertTrue(text.contains("JOBNAME=\"a?b?c\"\n"))
    }

    @Test fun a4SimplexPageHeaderAndFooterExactBytes() {
        val (bytes, _) = encode(listOf(blank(4960, 7017)), FIXED)
        val job = decodeJob(bytes)
        val pg = job.pages.single()
        val hdr = intArrayOf(0x00, 6, 0, 1, 2, 0x13, 0x60, 0x1B, 0x00, 1, 0, 1, 0, 0, 3, 1, 6).map { it.toByte() }.toByteArray()
        assertArrayEquals(hdr, pg.header)
        assertArrayEquals(byteArrayOf(1, 0, 1), pg.footer)
        assertEquals(0, pg.bands.size)
        assertArrayEquals(byteArrayOf(0x09, 0x1B) + "%-12345X".toByteArray(), job.tail)
    }

    @Test fun copiesAndDuplexFlags() {
        val p = blank(100, 100)
        val front = decodeJob(encode(listOf(p, p), FIXED.copy(copies = 300, duplex = QpdlDuplex.MANUAL_FRONTS)).first)
        assertEquals(2, front.pages.size)
        assertTrue(front.pjl.contains("@PJL SET DUPLEX=MANUAL\n@PJL SET BINDING=LONGEDGE\n"))
        val h0 = front.pages[0].header; val h1 = front.pages[1].header
        assertEquals(300, ((h0[2].toInt() and 0xFF) shl 8) or (h0[3].toInt() and 0xFF))
        assertEquals(3, h0[9].toInt()); assertEquals(1, h1[9].toInt())
        assertEquals(0, h0[11].toInt()); assertEquals(0, h0[12].toInt())
        assertArrayEquals(byteArrayOf(1, 0x01, 0x2C), front.pages[0].footer)
        val back = decodeJob(encode(listOf(p), FIXED.copy(duplex = QpdlDuplex.MANUAL_BACKS, binding = QpdlBinding.SHORT_EDGE)).first)
        assertTrue(back.pjl.contains("BINDING=SHORTEDGE"))
        assertEquals(0, back.pages[0].header[11].toInt()); assertEquals(1, back.pages[0].header[12].toInt())
    }

    @Test fun roundTripNoiseOddSizedPage() {
        // bitmap width not a multiple of 8, taller than the sheet in one test and shorter in the other
        for (h in intArrayOf(1650, 1800)) {
            val w = 1677
            val p = blank(w, h)
            Random(h.toLong()).nextBytes(p.data) // padding bits are random too and must be ignored
            roundTrip(p, customOdd)
        }
    }

    @Test fun noiseFallsBackToLiteralOnlyAndStillDecodes() {
        val p = blank(1672, 1700)
        Random(7).nextBytes(p.data)
        roundTrip(p, customOdd)
        // random data cannot be compressed: the block is literal-only and (unavoidably, by header overhead) larger than the band
        val band = ByteArray(128 * 209).also { Random(9).nextBytes(it) }
        val block = QpdlBandCodec.compress(band, band.size)
        val literal = QpdlBandCodec.compressWithTable(band, band.size, IntArray(64) { 1 }, literalOnly = true)
        assertTrue(block.size <= literal.size) // never worse than literal-only
        assertTrue(block.size > band.size)
    }

    @Test fun roundTripAllWhiteAndAllBlack() {
        val w = QpdlGeometry(customOdd.paper).widthPx
        val white = roundTrip(blank(w, 1700), customOdd)
        assertEquals(0, white.bandsSent)
        val black = blank(w, 1700)
        black.data.fill(0xFF.toByte())
        val st = roundTrip(black, customOdd)
        assertEquals(QpdlGeometry(customOdd.paper).bandCount, st.bandsSent)
        assertEquals(0, st.oversizeBands)
    }

    @Test fun roundTripTextLikeLetter() {
        val g = QpdlGeometry(QpdlPaperSize.LETTER)
        roundTrip(textLike(g.widthPx, g.sheetHeightPx, 3), FIXED.copy(paper = QpdlPaperSize.LETTER))
    }

    @Test fun marginPixelsAreDroppedAndEmptyBandsSkipped() {
        val o = customOdd
        val g = QpdlGeometry(o.paper)
        val p = blank(g.widthPx, g.sheetHeightPx)
        // inside the hard margins: must vanish
        for (x in 0 until g.widthPx) p.set(x, 104)
        for (y in 0 until g.sheetHeightPx) p.set(111, y)
        val st0 = roundTrip(p, o)
        assertEquals(0, st0.bandsSent)
        // one pixel in band 3 (first row of it) and one in band 7 (last row of it)
        p.set(112, 105 + 3 * 128)
        p.set(g.widthPx - 1, 105 + 7 * 128 + 127)
        val (bytes, stats) = encode(listOf(p), o)
        val pg = decodeJob(bytes).pages.single()
        assertEquals(listOf(3, 7), pg.bands.map { it.number })
        assertEquals(g.bandCount - 2, stats.bandsSkipped)
        // pixel at x=112 is the first printable pixel: band column 0, row 0, bit 7 cleared (black = 0)
        assertEquals(0x7F, pg.bands[0].pixels[0].toInt() and 0xFF)
        assertEquals(0xFF, pg.bands[0].pixels[1].toInt() and 0xFF)
        // the sheet's last column lands in band byte column (width-112)/8-1 .. which is still inside the data
        assertEquals(0xFE, pg.bands[1].pixels[(g.bandBytes - 15) * 128 + 127].toInt() and 0xFF)
        // the 14 trailing byte columns are white
        for (x in g.bandBytes - 14 until g.bandBytes) for (y in 0 until 128) assertEquals(0xFF, pg.bands[1].pixels[x * 128 + y].toInt() and 0xFF)
    }

    @Test fun recordLengthsAndChecksumsAreRight() {
        val g = QpdlGeometry(QpdlPaperSize.LETTER)
        val (bytes, _) = encode(listOf(textLike(g.widthPx, g.sheetHeightPx, 11)), FIXED.copy(paper = QpdlPaperSize.LETTER))
        val job = decodeJob(bytes)
        val bands = job.pages.single().bands
        assertTrue(bands.size > 10)
        for (b in bands) {
            assertEquals(g.widthPx, b.width); assertEquals(128, b.height)
            assertEquals(4 + b.block.size + 4, b.recordLen)
            var sum = 0xEFL + 0xCD + 0xAB + 0x09
            for (x in b.block) sum += (x.toInt() and 0xFF)
            assertEquals(sum and 0xFFFFFFFFL, b.storedChecksum)
        }
    }

    @Test fun compressedBlocksNeverExceedRawBand() {
        val g = QpdlGeometry(QpdlPaperSize.LETTER)
        val (bytes, stats) = encode(listOf(textLike(g.widthPx, g.sheetHeightPx, 5)), FIXED.copy(paper = QpdlPaperSize.LETTER))
        assertEquals(0, stats.oversizeBands)
        for (b in decodeJob(bytes).pages.single().bands) assertTrue(b.block.size <= 128 * g.bandBytes)
    }

    @Test fun overlappingCopiesDecode() {
        // hand-made block: lead 1 raw byte 0xAA, then a 10-byte copy at distance 1 that reads its own output
        fun block(lead: ByteArray, ops: ByteArray): ByteArray {
            val table = ByteArray(128) { if (it % 2 == 0) 1 else 0 }
            table[2] = lead.size.toByte() // entry 1 is the distance under test (2 when the lead is 2 bytes)
            return byteArrayOf(lead.size.toByte(), 0, 0, 0) + table + lead + ops
        }
        val a = decodeBlock(block(byteArrayOf(0xAA.toByte()), intArrayOf(0x80 or 7, 0).map { it.toByte() }.toByteArray()), 11)
        assertArrayEquals(ByteArray(11) { 0xAA.toByte() }, a)
        // distance 2 (table index 1), pattern ABAB..., length 514 uses both extra length bits
        val b = decodeBlock(block(byteArrayOf(1, 2), intArrayOf(0x80 or 0x7F, 0xC0 or 1).map { it.toByte() }.toByteArray()), 2 + 514)
        for (i in b.indices) assertEquals(if (i % 2 == 0) 1 else 2, b[i].toInt())
        // and the encoder itself produces overlapping copies for a solid black page
        overlapCopiesSeen = 0
        val w = QpdlGeometry(customOdd.paper).widthPx
        val black = blank(w, 1700).also { it.data.fill(0xFF.toByte()) }
        roundTrip(black, customOdd)
        assertTrue(overlapCopiesSeen > 0)
    }

    @Test fun fullA4TextPageEncodesQuickly() {
        val g = QpdlGeometry(QpdlPaperSize.A4)
        val page = textLike(g.widthPx, g.sheetHeightPx, 1)
        encode(listOf(textLike(g.widthPx, g.sheetHeightPx, 2)), FIXED) // warm-up
        val t0 = System.nanoTime()
        val (bytes, stats) = encode(listOf(page), FIXED)
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("QPDL A4 600dpi text-like page: encoded in $ms ms, ${bytes.size} bytes, ${stats.bandsSent} bands sent")
        assertTrue("took $ms ms", ms < 5000)
        assertEquals(0, stats.oversizeBands)
        val job = decodeJob(bytes)
        assertArrayEquals(expectedImage(page, g), decodedImage(job.pages.single(), g))
    }
}
