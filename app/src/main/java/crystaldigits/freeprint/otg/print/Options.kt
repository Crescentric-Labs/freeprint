package crystaldigits.freeprint.otg.print

/** Paper sizes the G2020 advertises. Pixel sizes are at 600 dpi, the printer's only resolution. */
enum class Paper(val label: String, val media: String, val widthPt: Int, val heightPt: Int, val widthPx: Int, val heightPx: Int) {
    A4("A4", "iso_a4_210x297mm", 595, 842, 4960, 7016),
    LETTER("Letter", "na_letter_8.5x11in", 612, 792, 5100, 6600),
    A5("A5", "iso_a5_148x210mm", 420, 595, 3496, 4961),
    LEGAL("Legal", "na_legal_8.5x14in", 612, 1008, 5100, 8400),
    INDEX_4X6("4 x 6 in", "na_index-4x6_4x6in", 288, 432, 2400, 3600),
}

/** IPP `print-quality` values: 3 draft, 4 normal, 5 high. */
enum class Quality(val label: String, val ipp: Int) { DRAFT("Draft", 3), NORMAL("Normal", 4), HIGH("High", 5) }

enum class FitMode(val label: String) {
    /** Whole page visible, white margins where the aspect ratios differ. */
    FIT("Fit"),
    /** Page covers the whole sheet, the overflow is cropped. */
    FILL("Fill (crop)"),
    /** The page's real physical size (PDF points, or an image assumed to be 300 dpi). */
    ACTUAL("Actual size"),
}

/** Where the page sits on the sheet when there is spare room (Fit) or overflow (Fill). */
enum class Anchor(val label: String, val fx: Float, val fy: Float) {
    TOP_LEFT("Top left", 0f, 0f), TOP("Top", .5f, 0f), TOP_RIGHT("Top right", 1f, 0f),
    LEFT("Left", 0f, .5f), CENTER("Centre", .5f, .5f), RIGHT("Right", 1f, .5f),
    BOTTOM_LEFT("Bottom left", 0f, 1f), BOTTOM("Bottom", .5f, 1f), BOTTOM_RIGHT("Bottom right", 1f, 1f),
}

/** Pages per sheet. [cols] x [rows] is the grid on a portrait sheet; 2-up stacks two sideways pages. */
enum class PerSheet(val n: Int, val cols: Int, val rows: Int) {
    ONE(1, 1, 1), TWO(2, 1, 2), FOUR(4, 2, 2), SIX(6, 2, 3), NINE(9, 3, 3);
    val label get() = n.toString()
}

/** Sheet orientation. Auto follows the first page; landscape turns the content 90 degrees counter-clockwise. */
enum class Orientation(val label: String) { AUTO("Auto"), PORTRAIT("Portrait"), LANDSCAPE("Landscape") }

enum class PageOrder(val label: String) { ACROSS("Across, then down"), DOWN("Down, then across") }

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
