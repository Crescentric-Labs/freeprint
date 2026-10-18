package crescentric.freeprint.otg.print

import android.graphics.Bitmap
import android.graphics.Matrix

/** Draws a whole sheet as the reader sees it, for previews and for saving a laid-out scan. */
object SheetRender {
    /**
     * Sheet [page] of [src] laid out by [o], [width] pixels wide. The printer's raster is the landscape sheet turned a quarter turn
     * counter-clockwise; it is turned back so the result reads the right way up.
     */
    fun compose(src: PageSource, o: PrintOptions, page: Int, width: Int): Bitmap {
        val scale = width.toFloat() / o.paper.widthPx
        return SheetComposer(src, o, scale, maxRows = (o.paper.heightPx * scale).toInt()).use { composer ->
            val out = Bitmap.createBitmap(composer.width, composer.height, Bitmap.Config.ARGB_8888)
            composer.renderRows(page.coerceAtMost(composer.sheetCount - 1), 0, composer.height, out)
            if (composer.landscape) Bitmap.createBitmap(out, 0, 0, out.width, out.height, Matrix().apply { postRotate(90f) }, true) else out
        }
    }
}
