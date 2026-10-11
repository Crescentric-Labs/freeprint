package crystaldigits.freeprint.otg.print

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Chooses how many columns and rows to use when [n] pages share a sheet. Pure maths, no Android types. */
object SheetGrid {
    data class Grid(val cols: Int, val rows: Int) {
        val cells get() = cols * rows
    }

    /**
     * Picks the grid (with at least [n] cells) in which a page of [pageW] x [pageH] can be drawn largest, for a
     * sheet of [sheetW] x [sheetH] with an outer [margin] and a [gutter] between cells. With [canRotate] a page may
     * be turned a quarter turn to suit its cell. Ties go to the grid with fewer empty cells, then fewer columns.
     * Counts that do not fill a grid (3, 5, 7...) leave the last cells empty.
     */
    fun best(
        n: Int, sheetW: Float, sheetH: Float, margin: Float, gutter: Float,
        pageW: Float, pageH: Float, canRotate: Boolean,
    ): Grid {
        var best = Grid(1, n)
        var bestScale = -1f
        for (cols in 1..n) {
            val rows = ceil(n / cols.toDouble()).toInt()
            val cw = (sheetW - 2 * margin - (cols - 1) * gutter) / cols
            val ch = (sheetH - 2 * margin - (rows - 1) * gutter) / rows
            if (cw <= 0f || ch <= 0f) continue
            var scale = min(cw / pageW, ch / pageH)
            if (canRotate) scale = max(scale, min(cw / pageH, ch / pageW))
            val g = Grid(cols, rows)
            val better = when {
                scale > bestScale * (1f + EPS) -> true
                scale < bestScale * (1f - EPS) -> false
                g.cells != best.cells -> g.cells < best.cells
                else -> g.cols < best.cols
            }
            if (better) { best = g; bestScale = scale }
        }
        return best
    }

    private const val EPS = 1e-4f
}
