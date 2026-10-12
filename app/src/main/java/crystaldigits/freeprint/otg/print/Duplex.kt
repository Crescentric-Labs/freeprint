package crystaldigits.freeprint.otg.print

/**
 * How the sheets of a job are split into two passes when the printer cannot print on both sides itself.
 * Sheet numbers are 0-based and count sides of paper, so with several pages per sheet one "sheet" here is one side.
 *
 * Pass 1 prints the fronts (sheets 0, 2, 4...). The user then turns the stack over and loads it the way blank paper
 * was loaded, and pass 2 prints the backs (sheets 1, 3, 5...).
 *
 * @property fronts sheets for pass 1, in print order
 * @property backs sheets for pass 2, in print order
 * @property rotateBacks turn every back 180 degrees: the sheet is flipped like a notepad (short edge), not a book page
 * @property reversed the backs are printed last-to-first, for printers whose paper comes out the other way round
 * @property unpairedLast the final front has no back side (an odd number of sides)
 */
class DuplexPlan(
    val fronts: List<Int>,
    val backs: List<Int>,
    val rotateBacks: Boolean,
    val reversed: Boolean,
    val unpairedLast: Boolean,
) {
    companion object {
        /** Null when two-sided printing is off or there is only one side, so nothing needs turning over. */
        fun of(sheetCount: Int, twoSided: TwoSided, reverseBacks: Boolean): DuplexPlan? {
            if (twoSided == TwoSided.OFF || sheetCount < 2) return null
            val fronts = (0 until sheetCount step 2).toList()
            val backs = (1 until sheetCount step 2).toList()
            return DuplexPlan(
                fronts = fronts,
                backs = if (reverseBacks) backs.reversed() else backs,
                rotateBacks = twoSided == TwoSided.SHORT_EDGE,
                reversed = reverseBacks,
                unpairedLast = sheetCount % 2 == 1,
            )
        }
    }
}
