package crescentric.freeprint.otg.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lays [PageSource] pages out on sheets (1 to 9 per sheet, portrait or landscape) and renders any
 * horizontal strip of a sheet in the printer's own portrait raster. The print job calls it with
 * scale 1 (600 dpi) for 256-row bands; the preview calls it with a small scale and the whole
 * sheet, so both show the same arrangement.
 *
 * Layout is done in "layout space" (the sheet as the reader sees it). A landscape sheet is laid out
 * wide and each cell is then rotated 90 degrees counter-clockwise into the portrait raster.
 *
 * Each page is drawn into its own cell-sized bitmap and then copied into the strip, so a page
 * can never spill into its neighbour and no renderer has to honour a clip rectangle.
 */
class SheetComposer(
    private val source: PageSource,
    private val options: PrintOptions,
    scale: Float = 1f,
    maxRows: Int,
) : AutoCloseable {
    /** A cell in layout space (lx, ly, lw, lh) and the same area in raster space (x, y, w, h). */
    private class Cell(val lx: Int, val ly: Int, val lw: Int, val lh: Int, val x: Int, val y: Int, val w: Int, val h: Int)

    /** Raster size: always portrait, as the printer feeds it. */
    val width = (options.paper.widthPx * scale).roundToInt()
    val height = (options.paper.heightPx * scale).roundToInt()

    /** True when the sheet reads sideways (content turned 90 degrees counter-clockwise). */
    val landscape = when (options.orientation) {
        Orientation.PORTRAIT -> false
        Orientation.LANDSCAPE -> true
        Orientation.AUTO -> source.pageCount > 0 && source.pageSize(0).let { it.first > it.second }
    }

    private val dpi = PRINT_DPI * scale
    private val per = options.perSheet
    private val cols: Int
    private val rows: Int
    val sheetCount = ceil(source.pageCount / per.n.toDouble()).toInt().coerceAtLeast(1)

    private val cells: List<Cell>
    private val tmp: Bitmap
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE; color = Color.BLACK; strokeWidth = maxOf(1f, dpi / 150f)
    }

    init {
        val sheetW = if (landscape) height else width       // layout-space sheet size
        val sheetH = if (landscape) width else height
        // Single pages use the whole sheet; shared sheets get a margin and a gutter.
        val wantedMargin = (options.effectiveMarginMm * dpi / MM_PER_INCH).roundToInt()
        val margin = min(wantedMargin, (min(sheetW, sheetH) * MAX_MARGIN_FRACTION).toInt())     // never swallow the sheet
        val gutter = if (per.n == 1) 0 else (4 * dpi / MM_PER_INCH).roundToInt()
        // The grid that draws the first page largest; the same grid is used on every sheet so they look alike.
        val (firstW, firstH) = if (source.pageCount > 0) source.pageSize(0) else (1f to 1.414f)
        val grid = SheetGrid.best(
            per.n, sheetW.toFloat(), sheetH.toFloat(), margin.toFloat(), gutter.toFloat(),
            firstW, firstH, canRotate = options.orientation == Orientation.AUTO,
        )
        cols = grid.cols
        rows = grid.rows
        val cw = (sheetW - 2 * margin - (cols - 1) * gutter) / cols
        val ch = (sheetH - 2 * margin - (rows - 1) * gutter) / rows
        cells = (0 until rows).flatMap { r ->
            (0 until cols).map { c ->
                val lx = margin + c * (cw + gutter)
                val ly = margin + r * (ch + gutter)
                // Counter-clockwise quarter turn: layout (lx, ly) -> raster (ly, sheetW - lx).
                if (landscape) Cell(lx, ly, cw, ch, ly, sheetW - lx - cw, ch, cw) else Cell(lx, ly, cw, ch, lx, ly, cw, ch)
            }
        }
        tmp = Bitmap.createBitmap(cells[0].w, min(maxRows, cells[0].h).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    }

    /** Grid position (row-major index into [cells]) of the k-th page on a sheet. */
    private fun position(k: Int) = when (options.order) {
        PageOrder.ACROSS -> k
        PageOrder.DOWN -> (k % rows) * cols + k / rows
    }

    /** Renders raster rows [top, top+count) of [sheet] into the first [count] rows of [out]. */
    fun renderRows(sheet: Int, top: Int, count: Int, out: Bitmap) {
        out.eraseColor(Color.WHITE)
        val canvas = Canvas(out)
        for (k in 0 until per.n) {
            val page = sheet * per.n + k
            if (page >= source.pageCount) break
            val c = cells[position(k)]
            if (options.border && per.n > 1) canvas.drawRect(
                c.x.toFloat(), (c.y - top).toFloat(), (c.x + c.w).toFloat(), (c.y + c.h - top).toFloat(), borderPaint,
            )
            val (pw, ph) = source.pageSize(page)
            // Page into its layout cell; for a landscape sheet then turn the cell into raster space.
            val cellMatrix = Matrix(PageLayout.matrix(pw, ph, c.lw, c.lh, options, dpi, source.unitsPerInch(page), autoRotate = options.orientation == Orientation.AUTO)).apply {
                if (landscape) {
                    postRotate(-90f)                         // (x, y) -> (y, -x)
                    postTranslate(0f, c.lw.toFloat())        // -> (y, lw - x)
                }
            }
            // The slices of the cell that fall inside the strip, taken in tmp-sized pieces.
            var sliceTop = maxOf(c.y, top)
            val end = min(c.y + c.h, top + count)
            while (sliceTop < end) {
                val sliceRows = min(end - sliceTop, tmp.height)
                tmp.eraseColor(Color.WHITE)
                val m = Matrix(cellMatrix).apply { postTranslate(0f, -(sliceTop - c.y).toFloat()) }
                source.render(page, tmp, m)
                canvas.drawBitmap(
                    tmp, Rect(0, 0, c.w, sliceRows),
                    Rect(c.x, sliceTop - top, c.x + c.w, sliceTop - top + sliceRows), null,
                )
                sliceTop += sliceRows
            }
        }
    }

    override fun close() = tmp.recycle()

    private companion object { const val MAX_MARGIN_FRACTION = 0.4f }
}
