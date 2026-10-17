package crescentric.freeprint.otg.print

import org.junit.Assert.assertEquals
import org.junit.Test

class MarginTest {
    @Test fun automaticIsNoneForOnePagePerSheet() = assertEquals(0, PrintOptions().effectiveMarginMm)

    @Test fun automaticIsSmallWhenPagesShareASheet() =
        assertEquals(AUTO_NUP_MARGIN_MM, PrintOptions(perSheet = PerSheet.FOUR).effectiveMarginMm)

    @Test fun chosenMarginWinsOverAutomatic() {
        assertEquals(13, PrintOptions(marginMm = 13).effectiveMarginMm)
        assertEquals(13, PrintOptions(perSheet = PerSheet.SIX, marginMm = 13).effectiveMarginMm)
    }

    @Test fun zeroIsAChoiceNotAutomatic() =
        assertEquals(0, PrintOptions(perSheet = PerSheet.FOUR, marginMm = 0).effectiveMarginMm)
}
