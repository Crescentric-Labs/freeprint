package crescentric.freeprint.otg.scan

/**
 * Items deleted from a list, each with the position it had, so they can be put back where they were. Positions are the ones the
 * item had at the moment it was deleted, so restoring replays the deletions backwards.
 */
class PageTrash<T> {
    private val items = ArrayList<Pair<Int, T>>()

    val size get() = items.size
    val isEmpty get() = items.isEmpty()

    fun add(index: Int, item: T) { items += index to item }

    /** [list] with every deleted item back in its place. Does not empty the trash. */
    fun restoreInto(list: List<T>): List<T> {
        val result = list.toMutableList()
        for ((index, item) in items.asReversed()) result.add(index.coerceIn(0, result.size), item)
        return result
    }

    /** The deleted items themselves, in the order they were deleted. */
    fun all(): List<T> = items.map { it.second }

    fun clear() = items.clear()
}
