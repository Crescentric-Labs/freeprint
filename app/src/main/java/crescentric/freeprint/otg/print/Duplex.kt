package crescentric.freeprint.otg.print

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
 *
 * With several copies the stack holds every copy's fronts one after another, and the backs must line up with them. When a copy
 * has an odd number of sides its last front has no back, so that slot is filled with [BLANK] to keep the following copies aligned.
 */
class DuplexPlan(
    val fronts: List<Int>,
    val backs: List<Int>,
    val rotateBacks: Boolean,
    val reversed: Boolean,
    val unpairedLast: Boolean,
) {
    companion object {
        /** A sheet number meaning "print nothing": a blank back for a front that has none, so later copies stay in step. */
        const val BLANK = -1

        /** Null when two-sided printing is off or there is only one side, so nothing needs turning over. */
        fun of(sheetCount: Int, twoSided: TwoSided, reverseBacks: Boolean, copies: Int = 1): DuplexPlan? {
            if (twoSided == TwoSided.OFF || sheetCount < 2) return null
            val n = copies.coerceAtLeast(1)
            val odd = sheetCount % 2 == 1
            val fronts = (0 until sheetCount step 2).toList()
            val backs = (1 until sheetCount step 2).toList() + if (odd && n > 1) listOf(BLANK) else emptyList()
            val allBacks = List(n) { backs }.flatten()
            return DuplexPlan(
                fronts = List(n) { fronts }.flatten(),
                backs = if (reverseBacks) allBacks.reversed() else allBacks,
                rotateBacks = twoSided == TwoSided.SHORT_EDGE,
                reversed = reverseBacks,
                unpairedLast = odd && n == 1,
            )
        }
    }
}
