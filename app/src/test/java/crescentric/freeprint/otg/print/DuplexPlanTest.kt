package crescentric.freeprint.otg.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplexPlanTest {
    private fun plan(sheets: Int, mode: TwoSided = TwoSided.LONG_EDGE, reverse: Boolean = false) = DuplexPlan.of(sheets, mode, reverse)!!

    @Test fun copiesRepeatTheFrontsAndBacksInStep() {
        val p = DuplexPlan.of(4, TwoSided.LONG_EDGE, false, copies = 2)!!
        assertEquals(listOf(0, 2, 0, 2), p.fronts)
        assertEquals(listOf(1, 3, 1, 3), p.backs)
    }

    @Test fun anOddCopyGetsABlankBackSoLaterCopiesStayAligned() {
        val p = DuplexPlan.of(3, TwoSided.LONG_EDGE, false, copies = 2)!!
        assertEquals(listOf(0, 2, 0, 2), p.fronts)
        assertEquals(listOf(1, DuplexPlan.BLANK, 1, DuplexPlan.BLANK), p.backs)
        assertFalse(p.unpairedLast)
        // one copy keeps the plain behaviour: no blank, and the last front is reported as unpaired
        val one = DuplexPlan.of(3, TwoSided.LONG_EDGE, false, copies = 1)!!
        assertEquals(listOf(1), one.backs)
        assertTrue(one.unpairedLast)
    }

    @Test fun reversingBacksReversesTheWholeStack() {
        val p = DuplexPlan.of(4, TwoSided.LONG_EDGE, true, copies = 2)!!
        assertEquals(listOf(3, 1, 3, 1), p.backs)
    }

    @Test fun offMeansNoPlan() = assertNull(DuplexPlan.of(6, TwoSided.OFF, false))

    @Test fun oneSideNeedsNoTurningOver() {
        assertNull(DuplexPlan.of(1, TwoSided.LONG_EDGE, false))
        assertNull(DuplexPlan.of(0, TwoSided.LONG_EDGE, false))
    }

    @Test fun frontsAreEvenIndexesAndBacksOdd() {
        val p = plan(6)
        assertEquals(listOf(0, 2, 4), p.fronts)
        assertEquals(listOf(1, 3, 5), p.backs)
        assertFalse(p.unpairedLast)
    }

    @Test fun everySideIsPrintedExactlyOnce() {
        for (n in 2..13) for (rev in listOf(false, true)) {
            val p = plan(n, reverse = rev)
            assertEquals("n=$n", (0 until n).toList(), (p.fronts + p.backs).sorted())
        }
    }

    @Test fun oddCountLeavesTheLastFrontWithoutABack() {
        val p = plan(5)
        assertEquals(listOf(0, 2, 4), p.fronts)
        assertEquals(listOf(1, 3), p.backs)
        assertTrue(p.unpairedLast)
    }

    @Test fun reversedBacksArePrintedLastToFirst() {
        val p = plan(6, reverse = true)
        assertEquals(listOf(5, 3, 1), p.backs)
        assertTrue(p.reversed)
        assertEquals(listOf(0, 2, 4), p.fronts)               // fronts are never reversed
    }

    @Test fun shortEdgeTurnsTheBacksAndLongEdgeDoesNot() {
        assertTrue(plan(4, TwoSided.SHORT_EDGE).rotateBacks)
        assertFalse(plan(4, TwoSided.LONG_EDGE).rotateBacks)
    }

    @Test fun twoSidesGiveOneFrontAndOneBack() {
        val p = plan(2)
        assertEquals(listOf(0), p.fronts)
        assertEquals(listOf(1), p.backs)
        assertNotNull(p)
    }
}
