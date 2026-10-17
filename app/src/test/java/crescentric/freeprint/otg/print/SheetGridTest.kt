package crescentric.freeprint.otg.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetGridTest {
    // A4 portrait sheet at 600 dpi with a portrait A4 page, no margins: sizes only matter in proportion.
    private fun grid(n: Int, rotate: Boolean = true, sheetW: Float = 4960f, sheetH: Float = 7016f, margin: Float = 0f, gutter: Float = 0f) =
        SheetGrid.best(n, sheetW, sheetH, margin, gutter, 595f, 842f, rotate)

    @Test fun oneIsOneCell() = assertEquals(SheetGrid.Grid(1, 1), grid(1))
    @Test fun twoStackAndTurnSideways() = assertEquals(SheetGrid.Grid(1, 2), grid(2))
    @Test fun fourIsTwoByTwo() = assertEquals(SheetGrid.Grid(2, 2), grid(4))
    @Test fun sixIsTwoColumnsThreeRows() = assertEquals(SheetGrid.Grid(2, 3), grid(6))
    @Test fun nineIsThreeByThree() = assertEquals(SheetGrid.Grid(3, 3), grid(9))
    @Test fun sixteenIsFourByFour() = assertEquals(SheetGrid.Grid(4, 4), grid(16))

    @Test fun everyCountGetsEnoughCells() {
        for (n in 1..20) assertTrue("n=$n", grid(n).cells >= n)
    }

    @Test fun fiveUsesSixCellsNotMore() {
        val g = grid(5)
        assertEquals(6, g.cells)
    }

    @Test fun sevenUsesEightCells() = assertEquals(8, grid(7).cells)

    @Test fun landscapeSheetSwapsTheGrid() {
        // The same 6 pages on a landscape sheet: three columns and two rows.
        assertEquals(SheetGrid.Grid(3, 2), grid(6, sheetW = 7016f, sheetH = 4960f))
    }

    @Test fun marginsAndGuttersStillGiveAValidGrid() {
        val g = grid(9, margin = 140f, gutter = 95f)
        assertEquals(SheetGrid.Grid(3, 3), g)
    }

    @Test fun withoutRotationThePortraitPageKeepsTallCells() {
        // Two portrait pages side by side beats stacking them when pages may not be turned.
        assertEquals(SheetGrid.Grid(2, 1), grid(2, rotate = false))
    }
}
