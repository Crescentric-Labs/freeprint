package crescentric.freeprint.otg.print

/** Which cached sheet thumbnails are still right after a sheet is taken out of the pages to print. */
object ThumbRemap {
    /**
     * The sheet number under which the thumbnail cached as [key] can be kept once sheet [removed] is gone, or null when it
     * has to be drawn again.
     *
     * - The removed sheet's own thumbnail is gone, and sheets before it are untouched.
     * - Sheets after it shift down by one. That is only the same picture with one page per sheet; with several pages per sheet
     *   the pages after the removed one regroup, so those sheets look different.
     * - The first page can decide the look of every sheet (sheet orientation in Auto, and the grid for several pages per
     *   sheet). If the removed sheet holds the first page and [firstPageDecidesLayout], nothing can be kept.
     */
    fun remap(key: Int, removed: Int, pagesPerSheet: Int, firstPageDecidesLayout: Boolean): Int? = when {
        removed == 0 && firstPageDecidesLayout -> null
        key == removed -> null
        key < removed -> key
        pagesPerSheet == 1 -> key - 1
        else -> null
    }
}
