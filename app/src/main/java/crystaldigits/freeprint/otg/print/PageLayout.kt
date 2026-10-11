package crystaldigits.freeprint.otg.print

import android.graphics.Matrix
import kotlin.math.max
import kotlin.math.min

/**
 * Where and how big a source page lands in an area (a whole sheet, or one cell of an n-up sheet).
 * The same placement drives the preview (scaled down) and the print job, so the preview shows what
 * will be printed.
 */
object PageLayout {
    /** [rotate] turns the page 90 degrees clockwise first; then it is scaled and shifted by ([tx], [ty]) pixels. */
    data class Placement(val rotate: Boolean, val scale: Float, val tx: Float, val ty: Float)

    /**
     * Pure maths, no Android types.
     *
     * Size: [FitMode.FIT] and [FitMode.FILL] scale to the area; [FitMode.ACTUAL] uses the page's physical size
     * ([unitsPerInch] source units per inch at [dpi]). Then [PrintOptions.scalePercent] multiplies that.
     *
     * Position: [PrintOptions.anchor] picks where the page sits in the spare room (or which part survives
     * cropping), then the millimetre offsets nudge it.
     *
     * With [autoRotate], a page whose orientation differs from the area's is turned first, so a landscape
     * page uses a portrait area fully instead of printing small.
     */
    fun place(
        srcW: Float, srcH: Float, areaW: Int, areaH: Int, o: PrintOptions,
        dpi: Float = PRINT_DPI.toFloat(), unitsPerInch: Float = 72f, autoRotate: Boolean = true,
    ): Placement {
        val rotate = autoRotate && (srcW > srcH) != (areaW > areaH) && srcW != srcH
        val w = if (rotate) srcH else srcW
        val h = if (rotate) srcW else srcH
        val base = when (o.fit) {
            FitMode.FIT -> min(areaW / w, areaH / h)
            FitMode.FILL -> max(areaW / w, areaH / h)
            FitMode.ACTUAL -> dpi / unitsPerInch
        }
        val scale = base * o.scalePercent / 100f
        val pxPerMm = dpi / MM_PER_INCH
        return Placement(
            rotate, scale,
            (areaW - w * scale) * o.anchor.fx + o.offsetXmm * pxPerMm,
            (areaH - h * scale) * o.anchor.fy + o.offsetYmm * pxPerMm,
        )
    }

    /** Matrix form of [place]: source units to area pixels. */
    fun matrix(
        srcW: Float, srcH: Float, areaW: Int, areaH: Int, o: PrintOptions,
        dpi: Float = PRINT_DPI.toFloat(), unitsPerInch: Float = 72f, autoRotate: Boolean = true,
    ): Matrix {
        val p = place(srcW, srcH, areaW, areaH, o, dpi, unitsPerInch, autoRotate)
        val m = Matrix()
        if (p.rotate) {
            m.setRotate(90f)                  // (x, y) -> (-y, x)
            m.postTranslate(srcH, 0f)         // -> (srcH - y, x)
        }
        m.postScale(p.scale, p.scale)
        m.postTranslate(p.tx, p.ty)
        return m
    }
}
