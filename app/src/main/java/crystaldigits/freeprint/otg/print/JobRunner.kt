package crystaldigits.freeprint.otg.print

import android.graphics.Bitmap
import crystaldigits.freeprint.otg.PrintSession
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
     * Returns null on success, [CANCELLED], or a short error message. [progress] gets (sheet, sheetCount).
     * [cancelled] is checked before every band; a cancelled job is simply abandoned mid-stream.
     */
    fun run(
        source: PageSource, options: PrintOptions, session: PrintSession, copies: Int = 1,
        cancelled: () -> Boolean = { false }, progress: (Int, Int) -> Unit = { _, _ -> },
    ): String? {
        val paper = options.paper
        if (source.pageCount == 0) return "The document has no pages."
        if (!session.beginJob(options.color, paper.media, options.quality.ipp, copies)) return "Could not start the job: ${session.error}"

        val bitmap = Bitmap.createBitmap(paper.widthPx, BAND_ROWS, Bitmap.Config.ARGB_8888)
        val buffer = ByteBuffer.allocateDirect(paper.widthPx * BAND_ROWS * 4)
        SheetComposer(source, options, 1f, BAND_ROWS).use { composer ->
            try {
                for (sheet in 0 until composer.sheetCount) {
                    progress(sheet + 1, composer.sheetCount)
                    if (!session.beginPage(paper.widthPx, paper.heightPx, PRINT_DPI, paper.widthPt, paper.heightPt))
                        return "Could not start sheet ${sheet + 1}: ${session.error}"
                    var top = 0
                    while (top < paper.heightPx) {
                        if (cancelled()) return CANCELLED
                        val rows = min(BAND_ROWS, paper.heightPx - top)
                        composer.renderRows(sheet, top, rows, bitmap)
                        buffer.clear()
                        bitmap.copyPixelsToBuffer(buffer)
                        if (!session.addBand(buffer, rows)) return "Sending sheet ${sheet + 1} failed at row $top: ${session.error}"
                        top += rows
                    }
                    if (!session.endPage()) return "Could not finish sheet ${sheet + 1}: ${session.error}"
                }
            } finally {
                bitmap.recycle()
            }
        }
        return null
    }
}
