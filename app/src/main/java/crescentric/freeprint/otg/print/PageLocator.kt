package crescentric.freeprint.otg.print

/** Finds which file a page of the combined document belongs to. */
object PageLocator {
    /** The file index and the page's index inside that file, for combined page [page], or null if it is out of range. */
    fun locate(pageCounts: List<Int>, page: Int): Pair<Int, Int>? {
        if (page < 0) return null
        var start = 0
        pageCounts.forEachIndexed { file, count ->
            if (page < start + count) return file to (page - start)
            start += count
        }
        return null
    }
}
