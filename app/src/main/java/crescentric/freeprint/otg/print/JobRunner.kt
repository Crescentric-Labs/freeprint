package crescentric.freeprint.otg.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import crescentric.freeprint.otg.PrintSession
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * Streams every sheet of a [PageSource] to the printer, one 256-row band at a time (about 5 MB),
 * so a full sheet at 600 dpi is never held in memory. Blocking; call off the main thread.
 */
object JobRunner {
    private const val BAND_ROWS = 256

    /** Returned by [run] when [cancelled] became true. */
    const val CANCELLED = "cancelled"

    /**
     * Returns null on success, [CANCELLED], or a short error message. [progress] gets (position in this job, sheets in this job).
     * [cancelled] is checked before every band; a cancelled job is simply abandoned mid-stream.
     *
     * [paused] is polled between sheets; while it is true the job waits. [sheets] picks which sheets to print and in what order (0-based); null means all, in order. [rotate180] turns every
     * printed sheet half a turn, used for the backs of two-sided pages that are flipped on the short edge.
     */
    fun run(
        source: PageSource, options: PrintOptions, session: PrintSession, copies: Int = 1,
        sheets: List<Int>? = null, rotate180: Boolean = false,
        cancelled: () -> Boolean = { false }, paused: () -> Boolean = { false }, progress: (Int, Int) -> Unit = { _, _ -> },
    ): String? {
        val paper = options.paper
        if (source.pageCount == 0) return "The document has no pages."
        if (!session.beginJob(options.color, paper.media, options.quality.ipp, copies)) return "Could not start the job: ${session.error}"

        val bitmap = Bitmap.createBitmap(paper.widthPx, BAND_ROWS, Bitmap.Config.ARGB_8888)
        val turned = if (rotate180) Bitmap.createBitmap(paper.widthPx, BAND_ROWS, Bitmap.Config.ARGB_8888) else null
        val buffer = ByteBuffer.allocateDirect(paper.widthPx * BAND_ROWS * 4)
        SheetComposer(source, options, 1f, BAND_ROWS).use { composer ->
            try {
                val order = sheets ?: (0 until composer.sheetCount).toList()
                for ((position, sheet) in order.withIndex()) {
                    // Pausing takes effect between sheets: the one being sent finishes, then nothing more goes out until resumed
                    while (position > 0 && paused() && !cancelled()) Thread.sleep(150)
                    if (cancelled()) return CANCELLED
                    progress(position + 1, order.size)
                    if (!session.beginPage(paper.widthPx, paper.heightPx, PRINT_DPI, paper.widthPt, paper.heightPt))
                        return "Could not start sheet ${sheet + 1}: ${session.error}"
                    var top = 0
                    while (top < paper.heightPx) {
                        if (cancelled()) return CANCELLED
                        val rows = min(BAND_ROWS, paper.heightPx - top)
                        val band = if (turned == null) {
                            render(composer, sheet, top, rows, bitmap)
                            bitmap
                        } else {
                            // The bottom of the sheet comes first when it is turned half a turn, and each strip is itself turned.
                            render(composer, sheet, paper.heightPx - top - rows, rows, bitmap)
                            rotateHalfTurn(bitmap, turned, rows)
                            turned
                        }
                        buffer.clear()
                        band.copyPixelsToBuffer(buffer)
                        if (!session.addBand(buffer, rows)) return "Sending sheet ${sheet + 1} failed at row $top: ${session.error}"
                        top += rows
                    }
                    if (!session.endPage()) return "Could not finish sheet ${sheet + 1}: ${session.error}"
                }
            } finally {
                bitmap.recycle()
                turned?.recycle()
            }
        }
        return null
    }

    /** Draws a strip of [sheet], or plain white for [DuplexPlan.BLANK]. */
    private fun render(composer: SheetComposer, sheet: Int, top: Int, rows: Int, out: Bitmap) {
        if (sheet == DuplexPlan.BLANK) out.eraseColor(android.graphics.Color.WHITE) else composer.renderRows(sheet, top, rows, out)
    }

    /** Copies the first [rows] rows of [from] into [to], turned half a turn about the middle of that strip. */
    private fun rotateHalfTurn(from: Bitmap, to: Bitmap, rows: Int) {
        val canvas = Canvas(to)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.rotate(180f, from.width / 2f, rows / 2f)
        canvas.drawBitmap(from, 0f, 0f, null)
        canvas.restore()
    }
}
