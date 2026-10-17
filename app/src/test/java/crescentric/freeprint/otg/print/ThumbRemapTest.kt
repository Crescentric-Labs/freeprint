package crescentric.freeprint.otg.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbRemapTest {
    private fun remap(key: Int, removed: Int, perSheet: Int = 1, firstDecides: Boolean = false) =
        ThumbRemap.remap(key, removed, perSheet, firstDecides)

    @Test fun theRemovedSheetsOwnThumbnailIsDropped() = assertNull(remap(3, 3))

    @Test fun sheetsBeforeTheRemovedOneAreKept() {
        assertEquals(0, remap(0, 4))
        assertEquals(3, remap(3, 4))
    }

    @Test fun sheetsAfterShiftDownByOneWithOnePagePerSheet() {
        assertEquals(3, remap(4, 3))
        assertEquals(8, remap(9, 3))
    }

    @Test fun sheetsAfterAreRedrawnWhenSeveralPagesShareASheet() {
        assertNull(remap(4, 3, perSheet = 4))
        assertEquals(1, remap(1, 3, perSheet = 4))               // but earlier sheets are still fine
    }

    @Test fun removingTheFirstPageRedrawsEverythingWhenItDecidesTheLayout() {
        for (key in 0..5) assertNull("key $key", remap(key, 0, firstDecides = true))
    }

    @Test fun removingTheFirstPageStillShiftsWhenItDoesNotDecideTheLayout() {
        assertEquals(0, remap(1, 0))
        assertNull(remap(0, 0))
    }

    @Test fun removingALaterPageNeverTouchesTheLayoutRule() {
        assertEquals(0, remap(0, 2, firstDecides = true))
        assertEquals(1, remap(2, 1, firstDecides = true))
    }
}
