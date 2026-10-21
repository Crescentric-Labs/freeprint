package crescentric.freeprint.otg.print

import java.io.OutputStream
import java.util.Calendar

/*
 * Encoder for the Samsung ML-2160 job format (PJL wrapper, page header, 128-row column-major bands, "0x11" container).
 * Pure Kotlin, no Android classes, so it runs in plain JVM unit tests.
 *
 * Typical use, streaming one page at a time:
 *
 *   val job = QpdlJobWriter(usbStream, QpdlOptions(paper = QpdlPaperSize.A4, copies = 2))
 *   job.start()
 *   pages.forEachIndexed { i, p -> job.writePage(p, last = i == pages.lastIndex) }
 *   job.finish()
 *
 * Every point where the format description was ambiguous is isolated in a constant or function whose name starts with
 * ASSUMED_ / assumed, so it can be checked against a real printer.
 */

/** A paper size: the code that goes in the page header, and the size in PostScript points (1/72 inch). */
data class QpdlPaperSize(val code: Int, val widthPt: Int, val heightPt: Int) {
    companion object {
        val LETTER = QpdlPaperSize(0, 612, 792)
        val LEGAL = QpdlPaperSize(1, 612, 1008)
        val A4 = QpdlPaperSize(2, 595, 842)
        val EXECUTIVE = QpdlPaperSize(3, 522, 756)
        val LEDGER = QpdlPaperSize(4, 792, 1224)
        val A3 = QpdlPaperSize(5, 842, 1191)
        val ENV_10 = QpdlPaperSize(6, 297, 684)
        val MONARCH = QpdlPaperSize(7, 279, 540)
        val C5 = QpdlPaperSize(8, 459, 649)
        val DL = QpdlPaperSize(9, 312, 624)
        val B4 = QpdlPaperSize(10, 709, 1001)
        val B5 = QpdlPaperSize(11, 499, 709)
        val ENV_ISO_B5 = QpdlPaperSize(12, 499, 709)
        val POSTCARD = QpdlPaperSize(14, 284, 419)
        val A5 = QpdlPaperSize(16, 420, 595)
        val A6 = QpdlPaperSize(17, 298, 420)
        val B6 = QpdlPaperSize(18, 354, 499)
        const val CUSTOM_CODE = 21
        val C6 = QpdlPaperSize(23, 323, 459)

        fun custom(widthPt: Int, heightPt: Int) = QpdlPaperSize(CUSTOM_CODE, widthPt, heightPt)
    }
}

enum class QpdlDuplex {
    /** Single-sided job. */
    SIMPLEX,

    /** First pass of manual duplex: the front sides (odd pages). */
    MANUAL_FRONTS,

    /** Second pass of manual duplex: the back sides (even pages). */
    MANUAL_BACKS,
}

enum class QpdlBinding(val pjl: String) { LONG_EDGE("LONGEDGE"), SHORT_EDGE("SHORTEDGE") }

data class QpdlOptions(
    val paper: QpdlPaperSize = QpdlPaperSize.A4,
    val copies: Int = 1,
    val jobTitle: String = "Free Print",
    val userName: String = "FreePrint",
    /** 1..5, 3 is the normal setting. */
    val density: Int = 3,
    /** Toner save. */
    val economode: Boolean = false,
    val duplex: QpdlDuplex = QpdlDuplex.SIMPLEX,
    val binding: QpdlBinding = QpdlBinding.LONG_EDGE,
    /** YYYYMMDD, written to the SERVICEDATE line. */
    val serviceDate: String = QpdlJob.today(),
) {
    init {
        require(copies in 1..0xFFFF) { "copies must be 1..65535" }
        require(density in 1..5) { "density must be 1..5" }
        require(serviceDate.length == 8 && serviceDate.all { it in '0'..'9' }) { "serviceDate must be YYYYMMDD" }
    }
}

/**
 * A 1-bit page, 1 = black, rows top to bottom, most significant bit = leftmost pixel, [stride] bytes per row.
 * The page is the whole sheet rendered at 600 dpi, including the area the printer cannot reach; the encoder cuts the
 * hard margins itself. It may be larger or smaller than the sheet: missing pixels are white and extra pixels are dropped.
 * Padding bits at the end of a row (beyond [widthPx]) are ignored.
 */
class BitPage(val widthPx: Int, val heightPx: Int, val data: ByteArray, val stride: Int = (widthPx + 7) / 8) {
    init {
        require(widthPx >= 0 && heightPx >= 0 && stride >= (widthPx + 7) / 8)
        require(data.size >= stride.toLong() * heightPx) { "bitmap is smaller than stride x height" }
    }
}

/** Sizes derived from a paper size at 600 dpi. */
class QpdlGeometry(val paper: QpdlPaperSize) {
    /** Sheet width in pixels, rounded up to a multiple of 8. Also the page-header width and the band width. */
    val widthPx: Int = roundUp(ceilDiv(paper.widthPt * 25, 3), 8)

    /** Sheet height in pixels before the hard margin is taken off. */
    val sheetHeightPx: Int = ceilDiv(paper.heightPt * 25, 3)

    /** Page-header height: the sheet height minus the vertical hard margin. */
    val heightPx: Int = sheetHeightPx - HARD_MARGIN_ROWS
    val bandBytes: Int = widthPx / 8
    val bandCount: Int = ceilDiv(heightPx, BAND_ROWS)

    init {
        require(heightPx > 0 && bandBytes > HARD_MARGIN_BYTES) { "paper too small" }
        require(bandCount <= 256) { "page has more bands than the 1-byte band number can count" }
    }

    companion object {
        const val BAND_ROWS = 128

        /** 12.5 pt at 600 dpi is 104.17 rows, rounded up. ASSUMED: rounding up (the description says "verify exact rounding"). */
        const val HARD_MARGIN_ROWS = 105

        /** 12.5 pt at 600 dpi is 104.2 px, rounded up to a multiple of 8 = 112 px. */
        const val HARD_MARGIN_BYTES = 14
        private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b
        private fun roundUp(a: Int, m: Int) = (a + m - 1) / m * m
    }
}

/** Counters for a finished (or running) job, handy for logging and tests. */
class QpdlStats {
    var pages = 0
    var bandsSent = 0
    var bandsSkipped = 0

    /** Bands whose block could not be made smaller than the raw band even with the best of both strategies. */
    var oversizeBands = 0
    var bytesWritten = 0L
}

object QpdlJob {
    fun today(): String {
        val c = Calendar.getInstance()
        return "%04d%02d%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** Convenience: a whole job from a sequence of pages, streamed. */
    fun write(out: OutputStream, options: QpdlOptions, pages: Iterator<BitPage>): QpdlStats {
        val w = QpdlJobWriter(out, options)
        w.start()
        while (pages.hasNext()) {
            val p = pages.next()
            w.writePage(p, last = !pages.hasNext())
        }
        w.finish()
        return w.stats
    }
}

/** Writes one job to [out] page by page. Not thread safe. */
class QpdlJobWriter(private val out: OutputStream, private val options: QpdlOptions) {
    val geometry = QpdlGeometry(options.paper)
    val stats = QpdlStats()
    private var started = false
    private var finished = false

    // Reusable scratch, one band each.
    private val rowMajor = ByteArray(QpdlGeometry.BAND_ROWS * geometry.bandBytes)
    private val colMajor = ByteArray(QpdlGeometry.BAND_ROWS * geometry.bandBytes)

    fun start() {
        check(!started) { "already started" }
        started = true
        put(UEL)
        put(pjlHeaderText(options).toByteArray(Charsets.ISO_8859_1))
    }

    /** [last] only matters for manual duplex fronts, where the final page is fed from a different source (see [assumedPaperSource]). */
    fun writePage(page: BitPage, last: Boolean = false) {
        check(started && !finished) { "job not open" }
        put(pageHeader(options, geometry, last))
        for (band in 0 until geometry.bandCount) {
            if (!gatherBand(page, band)) {
                stats.bandsSkipped++
                continue
            }
            transposeInverted()
            val n = colMajor.size
            val block = QpdlBandCodec.compress(colMajor, n)
            if (block.size > n) stats.oversizeBands++
            writeBandRecord(band, block)
            stats.bandsSent++
        }
        put(byteArrayOf(0x01, (options.copies shr 8).toByte(), options.copies.toByte()))
        stats.pages++
    }

    fun finish() {
        check(started && !finished) { "job not open" }
        finished = true
        put(PJL_FOOTER_TAB)
        put(UEL)
        out.flush()
    }

    private fun put(b: ByteArray) {
        out.write(b)
        stats.bytesWritten += b.size
    }

    private fun writeBandRecord(band: Int, block: ByteArray) {
        val g = geometry
        val head = ByteArray(15)
        head[0] = 0x0C
        head[1] = band.toByte()
        head[2] = (g.widthPx shr 8).toByte(); head[3] = g.widthPx.toByte()
        head[4] = 0; head[5] = QpdlGeometry.BAND_ROWS.toByte()
        head[6] = COMPRESSION_ID
        val recordLen = 4 + block.size + 4
        be32(head, 7, recordLen)
        System.arraycopy(SIGNATURE, 0, head, 11, 4)
        put(head)
        put(block)
        val tail = ByteArray(4)
        be32(tail, 0, checksum(block))
        put(tail)
    }

    /** Copies the printable part of [band] into [rowMajor] (1 = black). Returns false when it holds no black pixel. */
    private fun gatherBand(page: BitPage, band: Int): Boolean {
        val g = geometry
        val bb = g.bandBytes
        val srcCols = (page.widthPx + 7) / 8
        rowMajor.fill(0)
        val copyCols = minOf(bb - QpdlGeometry.HARD_MARGIN_BYTES, srcCols - QpdlGeometry.HARD_MARGIN_BYTES)
        if (copyCols <= 0) return false
        val partialBits = page.widthPx and 7
        val lastSrcCol = srcCols - 1
        var any = false
        for (y in 0 until QpdlGeometry.BAND_ROWS) {
            val bandRow = band * QpdlGeometry.BAND_ROWS + y
            if (bandRow >= g.heightPx) break // white padding rows of the last band
            val srcRow = bandRow + QpdlGeometry.HARD_MARGIN_ROWS
            if (srcRow >= page.heightPx) break
            val srcBase = srcRow * page.stride + QpdlGeometry.HARD_MARGIN_BYTES
            val dstBase = y * bb
            for (x in 0 until copyCols) {
                var v = page.data[srcBase + x].toInt() and 0xFF
                if (v != 0) {
                    if (partialBits != 0 && x + QpdlGeometry.HARD_MARGIN_BYTES == lastSrcCol) {
                        v = v and (0xFF00 shr partialBits and 0xFF)
                    }
                    if (v != 0) {
                        rowMajor[dstBase + x] = v.toByte()
                        any = true
                    }
                }
            }
        }
        return any
    }

    /** Row-major 1=black into column-major 0=black: index = x * 128 + y. */
    private fun transposeInverted() {
        val bb = geometry.bandBytes
        for (y in 0 until QpdlGeometry.BAND_ROWS) {
            val rb = y * bb
            for (x in 0 until bb) {
                colMajor[x * QpdlGeometry.BAND_ROWS + y] = rowMajor[rb + x].toInt().inv().toByte()
            }
        }
    }

    companion object {
        const val COMPRESSION_ID: Byte = 0x11
        val SIGNATURE = byteArrayOf(0xEF.toByte(), 0xCD.toByte(), 0xAB.toByte(), 0x09)

        /** ESC then "%-12345X": the Universal Exit Language sequence, 9 bytes. */
        val UEL = byteArrayOf(0x1B, '%'.code.toByte(), '-'.code.toByte(), '1'.code.toByte(), '2'.code.toByte(),
            '3'.code.toByte(), '4'.code.toByte(), '5'.code.toByte(), 'X'.code.toByte())
        private val PJL_FOOTER_TAB = byteArrayOf(0x09)

        /** Sum of the compressed block and the four signature bytes, modulo 2^32. */
        fun checksum(block: ByteArray): Int {
            var s = 0
            for (b in SIGNATURE) s += b.toInt() and 0xFF
            for (b in block) s += b.toInt() and 0xFF
            return s
        }

        private fun be32(a: ByteArray, off: Int, v: Int) {
            a[off] = (v ushr 24).toByte(); a[off + 1] = (v ushr 16).toByte()
            a[off + 2] = (v ushr 8).toByte(); a[off + 3] = v.toByte()
        }

        private fun pjlText(s: String): String {
            val sb = StringBuilder()
            for (ch in s.take(80)) sb.append(if (ch.code in 0x20..0x7E && ch != '"') ch else '?')
            return sb.toString()
        }

        fun pjlHeaderText(o: QpdlOptions): String = buildString {
            append("@PJL DEFAULT SERVICEDATE=").append(o.serviceDate).append('\n')
            append("@PJL SET USERNAME=\"").append(pjlText(o.userName)).append("\"\n")
            append("@PJL SET JOBNAME=\"").append(pjlText(o.jobTitle)).append("\"\n")
            if (o.economode) append("@PJL SET ECONOMODE=ON\n") // ASSUMED position: right before JAMRECOVERY
            append("@PJL SET JAMRECOVERY=OFF\n")
            if (o.duplex == QpdlDuplex.SIMPLEX) {
                append("@PJL SET DUPLEX=OFF\n")
            } else {
                append("@PJL SET DUPLEX=MANUAL\n") // ASSUMED: BINDING line directly after DUPLEX
                append("@PJL SET BINDING=").append(o.binding.pjl).append('\n')
            }
            append("@PJL SET PAPERTYPE=OFF\n")
            append("@PJL SET ALTITUDE=LOW\n")
            append("@PJL SET DENSITY=").append(o.density).append('\n')
            append("@PJL SET RET=NORMAL\n")
            append("@PJL ENTER LANGUAGE = QPDL\n")
        }

        /**
         * ASSUMED: the description says "paper source 3 on the odd pages except the last" for manual duplex. Read literally:
         * the front-side pass uses source 3 for every page but the last of the job; everything else uses 1 (default tray).
         */
        fun assumedPaperSource(duplex: QpdlDuplex, last: Boolean): Int =
            if (duplex == QpdlDuplex.MANUAL_FRONTS && !last) 3 else 1

        fun pageHeader(o: QpdlOptions, g: QpdlGeometry, last: Boolean): ByteArray {
            val h = ByteArray(17)
            h[0] = 0x00
            h[1] = 6
            h[2] = (o.copies shr 8).toByte(); h[3] = o.copies.toByte()
            h[4] = o.paper.code.toByte()
            h[5] = (g.widthPx shr 8).toByte(); h[6] = g.widthPx.toByte()
            h[7] = (g.heightPx shr 8).toByte(); h[8] = g.heightPx.toByte()
            h[9] = assumedPaperSource(o.duplex, last).toByte()
            h[10] = 0
            h[11] = if (o.duplex == QpdlDuplex.SIMPLEX) 1 else 0
            h[12] = if (o.duplex == QpdlDuplex.MANUAL_BACKS) 1 else 0
            h[13] = 0
            h[14] = 3
            h[15] = 1
            h[16] = 6
            return h
        }
    }
}

/**
 * The band compressor. Output block layout: 4-byte little-endian L, 64 little-endian 16-bit distances, L raw bytes,
 * then literal runs and copies until the band is complete.
 *
 * Own design: distances are limited to 1..128 because the leading raw count L must cover the largest table entry and may
 * not exceed 128. The table holds the 64 distances that score best on a weighted histogram of equal-byte pairs; then a
 * greedy matcher tries every table distance at each position and takes the longest match. Literal-only encoding is built as
 * well and the smaller of the two wins.
 */
object QpdlBandCodec {
    const val TABLE_SIZE = 64
    const val MAX_LEAD = 128
    const val MIN_COPY = 3
    const val MAX_COPY = 514

    /** Match lengths below this are written as literals instead (a 3-byte copy rarely pays for splitting a literal run). */
    private const val MIN_WORTHWHILE_COPY = 4

    fun compress(band: ByteArray, n: Int): ByteArray {
        require(n >= MAX_LEAD && n <= band.size)
        val smart = compressWithTable(band, n, chooseTable(band, n))
        val literal = compressWithTable(band, n, IntArray(TABLE_SIZE) { 1 }, literalOnly = true)
        return if (smart.size <= literal.size) smart else literal
    }

    /** The 64 distances (ascending) to put in the table. */
    internal fun chooseTable(d: ByteArray, n: Int): IntArray {
        val score = LongArray(MAX_LEAD + 1)
        for (dist in 1..MAX_LEAD) {
            var s = 0L
            var i = MAX_LEAD
            while (i < n) {
                if (d[i] == d[i - dist]) s += if (d[i] == 0xFF.toByte()) 1 else 4
                i += 2
            }
            score[dist] = s
        }
        score[1] = Long.MAX_VALUE // runs along a column
        score[MAX_LEAD] = Long.MAX_VALUE - 1 // same row in the next column
        val order = (1..MAX_LEAD).sortedWith(compareBy({ -score[it] }, { it }))
        return order.take(TABLE_SIZE).sorted().toIntArray()
    }

    internal fun compressWithTable(d: ByteArray, n: Int, table: IntArray, literalOnly: Boolean = false): ByteArray {
        require(table.size == TABLE_SIZE)
        val lead = if (literalOnly) 1 else table.max()
        val out = Sink(n / 2 + 512)
        out.u8(lead); out.u8(0); out.u8(0); out.u8(0)
        for (t in table) { out.u8(t and 0xFF); out.u8(t ushr 8) }
        out.bytes(d, 0, lead)
        var pos = lead
        var litStart = pos
        val maxIdx = table.size
        while (pos < n) {
            var bestLen = 0
            var bestIdx = 0
            if (!literalOnly) {
                val maxLen = minOf(MAX_COPY, n - pos)
                if (maxLen >= MIN_WORTHWHILE_COPY) {
                    for (k in 0 until maxIdx) {
                        val src = pos - table[k]
                        if (d[src] != d[pos] || d[src + 1] != d[pos + 1] || d[src + 2] != d[pos + 2]) continue
                        var len = 3
                        while (len < maxLen && d[src + len] == d[pos + len]) len++
                        if (len > bestLen) {
                            bestLen = len; bestIdx = k
                            if (len == maxLen) break
                        }
                    }
                }
            }
            if (bestLen >= MIN_WORTHWHILE_COPY) {
                flushLiterals(out, d, litStart, pos)
                val e = bestLen - MIN_COPY
                out.u8(0x80 or (e and 0x7F))
                out.u8(((e shr 1) and 0xC0) or bestIdx)
                pos += bestLen
                litStart = pos
            } else {
                pos++
            }
        }
        flushLiterals(out, d, litStart, pos)
        return out.toByteArray()
    }

    private fun flushLiterals(out: Sink, d: ByteArray, from: Int, to: Int) {
        var p = from
        while (p < to) {
            val run = minOf(128, to - p)
            out.u8(run - 1)
            out.bytes(d, p, run)
            p += run
        }
    }

    private class Sink(cap: Int) {
        private var a = ByteArray(cap)
        private var len = 0
        private fun ensure(extra: Int) {
            if (len + extra > a.size) a = a.copyOf(maxOf(a.size * 2, len + extra))
        }
        fun u8(v: Int) { ensure(1); a[len++] = v.toByte() }
        fun bytes(src: ByteArray, off: Int, n: Int) { ensure(n); System.arraycopy(src, off, a, len, n); len += n }
        fun toByteArray(): ByteArray = a.copyOf(len)
    }
}
