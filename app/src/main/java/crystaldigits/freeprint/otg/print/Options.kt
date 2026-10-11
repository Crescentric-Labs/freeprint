package crystaldigits.freeprint.otg.print

import androidx.annotation.StringRes
import crystaldigits.freeprint.otg.R

/** Paper sizes the G2020 advertises. Pixel sizes are at 600 dpi, the printer's only resolution. */
enum class Paper(val label: String, val media: String, val widthPt: Int, val heightPt: Int, val widthPx: Int, val heightPx: Int) {
    A4("A4", "iso_a4_210x297mm", 595, 842, 4960, 7016),
    LETTER("Letter", "na_letter_8.5x11in", 612, 792, 5100, 6600),
    A5("A5", "iso_a5_148x210mm", 420, 595, 3496, 4961),
    LEGAL("Legal", "na_legal_8.5x14in", 612, 1008, 5100, 8400),
    INDEX_4X6("4 x 6 in", "na_index-4x6_4x6in", 288, 432, 2400, 3600),
}

/** IPP `print-quality` values: 3 draft, 4 normal, 5 high. */
enum class Quality(@StringRes val labelRes: Int, val ipp: Int) {
    DRAFT(R.string.quality_draft, 3), NORMAL(R.string.quality_normal, 4), HIGH(R.string.quality_high, 5)
}

enum class FitMode(@StringRes val labelRes: Int) {
    /** Whole page visible, white margins where the aspect ratios differ. */
    FIT(R.string.fit_fit),
    /** Page covers the whole sheet, the overflow is cropped. */
    FILL(R.string.fit_fill),
    /** The page's real physical size (PDF points, or an image assumed to be 300 dpi). */
    ACTUAL(R.string.fit_actual),
}

/** Where the page sits on the sheet when there is spare room (Fit) or overflow (Fill). */
enum class Anchor(@StringRes val labelRes: Int, val fx: Float, val fy: Float) {
    TOP_LEFT(R.string.anchor_top_left, 0f, 0f), TOP(R.string.anchor_top, .5f, 0f), TOP_RIGHT(R.string.anchor_top_right, 1f, 0f),
    LEFT(R.string.anchor_left, 0f, .5f), CENTER(R.string.anchor_center, .5f, .5f), RIGHT(R.string.anchor_right, 1f, .5f),
    BOTTOM_LEFT(R.string.anchor_bottom_left, 0f, 1f), BOTTOM(R.string.anchor_bottom, .5f, 1f), BOTTOM_RIGHT(R.string.anchor_bottom_right, 1f, 1f),
}

/** Pages per sheet. [cols] x [rows] is the grid on a portrait sheet; 2-up stacks two sideways pages. */
enum class PerSheet(val n: Int, val cols: Int, val rows: Int) {
    ONE(1, 1, 1), TWO(2, 1, 2), FOUR(4, 2, 2), SIX(6, 2, 3), NINE(9, 3, 3);
    val label get() = n.toString()
}

/** Sheet orientation. Auto follows the first page; landscape turns the content 90 degrees counter-clockwise. */
enum class Orientation(@StringRes val labelRes: Int) {
    AUTO(R.string.orient_auto), PORTRAIT(R.string.orient_portrait), LANDSCAPE(R.string.orient_landscape)
}

enum class PageOrder(@StringRes val labelRes: Int) { ACROSS(R.string.order_across), DOWN(R.string.order_down) }

data class PrintOptions(
    val paper: Paper = Paper.A4,
    val quality: Quality = Quality.NORMAL,
    val color: Boolean = true,
    val fit: FitMode = FitMode.FIT,
    /** Multiplies the size chosen by [fit]; above 100 the page overflows its area and is cropped. */
    val scalePercent: Int = 100,
    val anchor: Anchor = Anchor.CENTER,
    /** Extra shift in millimetres after anchoring; positive is right / down. */
    val offsetXmm: Int = 0,
    val offsetYmm: Int = 0,
    val orientation: Orientation = Orientation.AUTO,
    val perSheet: PerSheet = PerSheet.ONE,
    val order: PageOrder = PageOrder.ACROSS,
    /** Hairline around each page when several share a sheet. */
    val border: Boolean = false,
)

const val PRINT_DPI = 600
const val MIN_SCALE_PERCENT = 10
const val MAX_SCALE_PERCENT = 400
const val MM_PER_INCH = 25.4f
