package crystaldigits.freeprint.otg.print

import android.graphics.Bitmap
import android.graphics.Matrix

/** Which of the chosen pages to keep, by document page number. */
enum class Parity(val label: String) { ALL("All"), ODD("Odd"), EVEN("Even") }

/** Outcome of parsing a page-range string. Page numbers are 1-based in text, 0-based in [Pages.indices]. */
sealed interface PageSelection {
    data class Pages(val indices: List<Int>) : PageSelection
    data class Invalid(val message: String) : PageSelection

    companion object {
        /**
         * Parses "1-3, 5, 8-" style text (comma or space separated; "a-" runs to the last page, "-b" starts at
         * page 1). Blank text means every page. The result is sorted and has no duplicates; [parity] then keeps
         * only odd or even page numbers.
         */
        fun parse(text: String, parity: Parity, pageCount: Int): PageSelection {
            val wanted = sortedSetOf<Int>()
            val tokens = text.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isEmpty()) wanted.addAll(1..pageCount)
            for (t in tokens) {
                val parts = t.split('-')
                val from: Int
                val to: Int
                try {
                    when {
                        parts.size == 1 -> { from = parts[0].toInt(); to = from }
                        parts.size == 2 -> {
                            from = if (parts[0].isEmpty()) 1 else parts[0].toInt()
                            to = if (parts[1].isEmpty()) pageCount else parts[1].toInt()
                        }
                        else -> return Invalid("\"$t\" is not a valid range.")
                    }
                } catch (_: NumberFormatException) {
                    return Invalid("\"$t\" is not a valid page or range.")
                }
                if (from < 1 || to < 1) return Invalid("Pages start at 1.")
                if (from > to) return Invalid("\"$t\" runs backwards.")
                if (to > pageCount) return Invalid("Page $to is past the end ($pageCount page${if (pageCount == 1) "" else "s"}).")
                wanted.addAll(from..to)
            }
            val kept = wanted.filter { parity == Parity.ALL || (parity == Parity.ODD) == (it % 2 == 1) }
            if (kept.isEmpty()) return Invalid("No pages match.")
            return Pages(kept.map { it - 1 })
        }
    }
}

/** A view of [source] showing only [indices], in order. Does not own or close [source]. */
class SelectedPages(private val source: PageSource, private val indices: List<Int>) : PageSource {
    override val pageCount get() = indices.size
    override val unitsPerInch get() = source.unitsPerInch
    override fun pageSize(index: Int) = source.pageSize(indices[index])
    override fun render(index: Int, bitmap: Bitmap, matrix: Matrix) = source.render(indices[index], bitmap, matrix)
    override fun close() {}
}
