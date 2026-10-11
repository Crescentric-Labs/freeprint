package crystaldigits.freeprint.otg.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSelectionTest {
    private fun ok(text: String, parity: Parity = Parity.ALL, n: Int = 10): List<Int> =
        (PageSelection.parse(text, parity, n) as PageSelection.Pages).indices.map { it + 1 }

    private fun err(text: String, parity: Parity = Parity.ALL, n: Int = 10): String =
        (PageSelection.parse(text, parity, n) as PageSelection.Invalid).message

    @Test fun blankMeansAll() = assertEquals((1..10).toList(), ok(""))
    @Test fun blankWhitespaceMeansAll() = assertEquals((1..10).toList(), ok("  ,  "))
    @Test fun singlePagesAndRanges() = assertEquals(listOf(1, 2, 3, 5, 8, 9, 10), ok("1-3, 5, 8-10"))
    @Test fun openEnded() = assertEquals(listOf(8, 9, 10), ok("8-"))
    @Test fun openStart() = assertEquals(listOf(1, 2, 3), ok("-3"))
    @Test fun duplicatesAndOrderNormalised() = assertEquals(listOf(2, 3, 4, 7), ok("7, 3-4, 2-3"))
    @Test fun spaceSeparated() = assertEquals(listOf(1, 4), ok("1 4"))
    @Test fun odd() = assertEquals(listOf(1, 3, 5, 7, 9), ok("", Parity.ODD))
    @Test fun evenWithinRange() = assertEquals(listOf(4, 6), ok("3-7", Parity.EVEN))
    @Test fun lastPageOnly() = assertEquals(listOf(10), ok("10"))
    @Test fun zeroRejected() = assertTrue(err("0").contains("start at 1"))
    @Test fun pastEndRejected() = assertTrue(err("11").contains("past the end"))
    @Test fun rangePastEndRejected() = assertTrue(err("8-12").contains("past the end"))
    @Test fun backwardsRejected() = assertTrue(err("5-2").contains("backwards"))
    @Test fun garbageRejected() = assertTrue(err("abc").contains("not a valid"))
    @Test fun tooManyDashesRejected() = assertTrue(err("1-2-3").contains("not a valid"))
    @Test fun emptyAfterParityRejected() = assertTrue(err("2", Parity.ODD).contains("No pages"))
    @Test fun singlePageDocument() = assertEquals(listOf(1), ok("", n = 1))
    @Test fun zeroPageDocumentIsInvalid() = assertTrue(PageSelection.parse("", Parity.ALL, 0) is PageSelection.Invalid)
}
