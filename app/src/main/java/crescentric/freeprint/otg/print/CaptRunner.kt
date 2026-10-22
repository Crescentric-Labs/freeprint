package crescentric.freeprint.otg.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.min

/**
 * The same job as [JobRunner], for Canon CAPT printers: each sheet is drawn in 256-row strips, halftoned to 1 bit into one
 * page-sized bitmap, compressed in bands of [BAND_LINES] rows and sent through a [CaptSession]. Blocking; call off the main thread.
 */
object CaptRunner {
    private const val BAND_ROWS = 256
    private const val BAND_LINES = 160

    /** Same contract as [JobRunner.run]: null on success, [JobRunner.CANCELLED], or a short error message with the end of the trace. */
    fun run(
        source: PageSource, options: PrintOptions, link: CaptTransport, copies: Int = 1,
        sheets: List<Int>? = null, rotate180: Boolean = false,
        cancelled: () -> Boolean = { false }, paused: () -> Boolean = { false }, progress: (Int, Int) -> Unit = { _, _ -> },
    ): String? {
        if (source.pageCount == 0) return "The document has no pages."
        val paper = options.paper
        val session = CaptSession(link, cancelled)
        val bitmap = Bitmap.createBitmap(paper.widthPx, BAND_ROWS, Bitmap.Config.ARGB_8888)
        val turned = if (rotate180) Bitmap.createBitmap(paper.widthPx, BAND_ROWS, Bitmap.Config.ARGB_8888) else null
        val pixels = IntArray(paper.widthPx * BAND_ROWS)
        val rowBytes = (paper.widthPx + 7) / 8
        var inJob = false
        try {
            SheetComposer(source, options, 1f, BAND_ROWS).use { composer ->
                val once = sheets ?: (0 until composer.sheetCount).toList()
                val order = List(copies.coerceAtLeast(1)) { once }.flatten()
                for ((position, sheet) in order.withIndex()) {
                    while (position > 0 && paused() && !cancelled()) Thread.sleep(150)
                    if (cancelled()) { if (inJob) session.cancelCleanup(); return JobRunner.CANCELLED }
                    progress(position + 1, order.size)

                    // Draw and halftone the whole sheet, then compress it band by band
                    val page = ByteArray(rowBytes * paper.heightPx)
                    val halftone = Halftone(paper.widthPx)
                    var top = 0
                    while (top < paper.heightPx) {
                        if (cancelled()) { if (inJob) session.cancelCleanup(); return JobRunner.CANCELLED }
                        val rows = min(BAND_ROWS, paper.heightPx - top)
                        val band = if (turned == null) {
                            render(composer, sheet, top, rows, bitmap); bitmap
                        } else {
                            render(composer, sheet, paper.heightPx - top - rows, rows, bitmap)
                            rotateHalfTurn(bitmap, turned, rows); turned
                        }
                        band.getPixels(pixels, 0, paper.widthPx, 0, 0, paper.widthPx, rows)
                        halftone.convert(pixels, rows, page, top * rowBytes, rowBytes)
                        top += rows
                    }
                    val geometry = geometryOf(paper)
                    val bands = compressPage(page, rowBytes, paper.heightPx, geometry.lineSize)

                    if (!inJob) { session.jobPrologue(); inJob = true }
                    session.pagePrologue(geometry)
                    session.startPageData()
                    for (b in bands) {
                        if (cancelled()) { session.cancelCleanup(); return JobRunner.CANCELLED }
                        session.sendBand(b)
                    }
                    if (!session.pageEpilogue()) return withTrace("The printer is out of paper.", session)
                }
                session.jobEpilogue()
                inJob = false
            }
            return null
        } catch (e: CaptSession.CaptCancelled) {
            if (inJob) session.cancelCleanup()
            return JobRunner.CANCELLED
        } catch (e: java.io.IOException) {
            if (inJob) session.cancelCleanup()
            return withTrace(e.message ?: "Sending to the printer failed.", session)
        } finally {
            bitmap.recycle()
            turned?.recycle()
        }
    }

    private fun withTrace(message: String, session: CaptSession) =
        message + "\nCanon CAPT (beta) trace:\n" + session.trace.takeLast(8).joinToString("\n")

    /** The page header for [paper], as captdriver builds it for this family: bytes per line are the paper width in points. */
    fun geometryOf(paper: Paper): CaptSession.Page = CaptSession.Page(
        sizeCode = when (paper) {
            Paper.A4 -> 0x02; Paper.A5 -> 0x03; Paper.B5 -> 0x07; Paper.EXECUTIVE -> 0x0A; Paper.LEGAL -> 0x0C; Paper.LETTER -> 0x0D
            else -> 0x02
        },
        paperWidth = paper.widthPx, paperHeight = paper.heightPx, lineSize = paper.widthPt, numLines = paper.heightPx,
    )

    /**
     * Cuts the sheet's rows to [lineSize] bytes (centred, as captdriver does) and compresses them in bands of [BAND_LINES] rows.
     * [page] is [rows] rows of [rowBytes] bytes, 1 = black.
     */
    fun compressPage(page: ByteArray, rowBytes: Int, rows: Int, lineSize: Int): List<ByteArray> {
        val shiftFrom = if (lineSize < rowBytes) (rowBytes - lineSize) / 2 else 0   // bytes skipped at the start of each sheet row
        val shiftTo = if (rowBytes < lineSize) (lineSize - rowBytes) / 2 else 0     // bytes of white added before each band row
        val copy = min(rowBytes, lineSize)
        val band = ByteArray(lineSize * BAND_LINES)
        val bands = ArrayList<ByteArray>()
        var start = 0
        while (start < rows) {
            val n = min(BAND_LINES, rows - start)
            band.fill(0)
            for (y in 0 until n) System.arraycopy(page, (start + y) * rowBytes + shiftFrom, band, y * lineSize + shiftTo, copy)
            bands.add(Hiscoa.compressBand(band, lineSize, n))
            start += n
        }
        return bands
    }

    private fun render(composer: SheetComposer, sheet: Int, top: Int, rows: Int, out: Bitmap) {
        if (sheet == DuplexPlan.BLANK) out.eraseColor(Color.WHITE) else composer.renderRows(sheet, top, rows, out)
    }

    private fun rotateHalfTurn(from: Bitmap, to: Bitmap, rows: Int) {
        val canvas = Canvas(to)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.rotate(180f, from.width / 2f, rows / 2f)
        canvas.drawBitmap(from, 0f, 0f, null)
        canvas.restore()
    }
}
