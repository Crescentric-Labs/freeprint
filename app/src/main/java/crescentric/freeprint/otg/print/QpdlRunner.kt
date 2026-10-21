package crescentric.freeprint.otg.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.io.OutputStream
import kotlin.math.min

/**
 * The same job as [JobRunner], for printers that take Samsung's page language: each sheet is drawn in 256-row strips, halftoned
 * to 1 bit into one page-sized bitmap (about 5 MB), encoded and streamed. Blocking; call off the main thread.
 */
object QpdlRunner {
    private const val BAND_ROWS = 256

    /** Same contract as [JobRunner.run]: null on success, [JobRunner.CANCELLED], or a short error message. */
    fun run(
        source: PageSource, options: PrintOptions, out: OutputStream, copies: Int = 1,
        sheets: List<Int>? = null, rotate180: Boolean = false, title: String = "Free Print",
        cancelled: () -> Boolean = { false }, paused: () -> Boolean = { false }, progress: (Int, Int) -> Unit = { _, _ -> },
    ): String? {
        if (source.pageCount == 0) return "The document has no pages."
        val paper = options.paper
        val qpdl = QpdlOptions(paper = sizeOf(paper), copies = 1, jobTitle = title, userName = "FreePrint")
        val stride = (paper.widthPx + 7) / 8
        val bitmap = Bitmap.createBitmap(paper.widthPx, BAND_ROWS, Bitmap.Config.ARGB_8888)
        val turned = if (rotate180) Bitmap.createBitmap(paper.widthPx, BAND_ROWS, Bitmap.Config.ARGB_8888) else null
        val pixels = IntArray(paper.widthPx * BAND_ROWS)
        val writer = QpdlJobWriter(out, qpdl)
        var sent = 0
        return try {
            writer.start()
            SheetComposer(source, options, 1f, BAND_ROWS).use { composer ->
                val order = (sheets ?: (0 until composer.sheetCount).toList()).let { once -> List(copies.coerceAtLeast(1)) { once }.flatten() }
                for ((position, sheet) in order.withIndex()) {
                    while (position > 0 && paused() && !cancelled()) Thread.sleep(150)
                    if (cancelled()) return@use CANCELLED_AFTER_START
                    progress(position + 1, order.size)
                    val page = ByteArray(stride * paper.heightPx)
                    val halftone = Halftone(paper.widthPx)
                    var top = 0
                    while (top < paper.heightPx) {
                        if (cancelled()) return@use CANCELLED_AFTER_START
                        val rows = min(BAND_ROWS, paper.heightPx - top)
                        val band = if (turned == null) {
                            render(composer, sheet, top, rows, bitmap)
                            bitmap
                        } else {
                            render(composer, sheet, paper.heightPx - top - rows, rows, bitmap)
                            rotateHalfTurn(bitmap, turned, rows)
                            turned
                        }
                        band.getPixels(pixels, 0, paper.widthPx, 0, 0, paper.widthPx, rows)
                        halftone.convert(pixels, rows, page, top * stride, stride)
                        top += rows
                    }
                    writer.writePage(BitPage(paper.widthPx, paper.heightPx, page, stride), last = position == order.lastIndex)
                    sent++
                }
                null
            }.let { result ->
                writer.finish()
                if (result == CANCELLED_AFTER_START) JobRunner.CANCELLED else result
            }
        } catch (e: java.io.IOException) {
            "Sending to the printer failed after $sent sheet(s): ${e.message}"
        } finally {
            bitmap.recycle()
            turned?.recycle()
        }
    }

    private const val CANCELLED_AFTER_START = "cancelled-after-start"

    /** The page-header paper code for [paper]; sizes the printer has no code for go as a custom size. */
    fun sizeOf(paper: Paper): QpdlPaperSize = when (paper) {
        Paper.A4 -> QpdlPaperSize.A4
        Paper.LETTER -> QpdlPaperSize.LETTER
        Paper.LEGAL -> QpdlPaperSize.LEGAL
        Paper.A5 -> QpdlPaperSize.A5
        Paper.A6 -> QpdlPaperSize.A6
        Paper.B5 -> QpdlPaperSize.B5
        Paper.EXECUTIVE -> QpdlPaperSize.EXECUTIVE
        else -> QpdlPaperSize.custom(paper.widthPt, paper.heightPt)
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
