package crescentric.freeprint.otg.printservice

import android.print.PrintAttributes
import android.print.PrintAttributes.MediaSize
import android.print.PrintAttributes.Resolution
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import crescentric.freeprint.otg.print.Paper
import kotlin.math.abs

/** What the print framework is told about the USB printer, and how its job attributes map back to ours. */
object PrinterProfile {
    /** Paper sizes offered to the system print dialog, in the order they are listed. */
    private val SIZES = listOf(
        MediaSize.ISO_A4 to Paper.A4,
        MediaSize.NA_LETTER to Paper.LETTER,
        MediaSize.ISO_A5 to Paper.A5,
        MediaSize.NA_LEGAL to Paper.LEGAL,
        MediaSize.NA_INDEX_4X6 to Paper.INDEX_4X6,
    )

    private val RESOLUTION = Resolution("r600", "600 dpi", 600, 600)

    fun capabilities(id: PrinterId): PrinterCapabilitiesInfo =
        PrinterCapabilitiesInfo.Builder(id).apply {
            SIZES.forEachIndexed { i, (size, _) -> addMediaSize(size, i == 0) }
            addResolution(RESOLUTION, true)
            setColorModes(
                PrintAttributes.COLOR_MODE_COLOR or PrintAttributes.COLOR_MODE_MONOCHROME,
                PrintAttributes.COLOR_MODE_COLOR,
            )
            setDuplexModes(PrintAttributes.DUPLEX_MODE_NONE, PrintAttributes.DUPLEX_MODE_NONE)
            setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        }.build()

    /** Our paper for a media size the system chose; the nearest known size within 3%, else A4. */
    fun paperFor(size: MediaSize?): Paper {
        val s = size?.asPortrait() ?: return Paper.A4
        return SIZES.map { it.second to it.first }
            .minByOrNull { (_, ms) -> abs(ms.widthMils - s.widthMils) + abs(ms.heightMils - s.heightMils) }
            ?.takeIf { (_, ms) ->
                abs(ms.widthMils - s.widthMils) < ms.widthMils * 0.03 && abs(ms.heightMils - s.heightMils) < ms.heightMils * 0.03
            }?.first ?: Paper.A4
    }

    const val LOCAL_ID = "usb-printer"
}
