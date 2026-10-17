package crescentric.freeprint.otg.print

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.annotation.StringRes
import crescentric.freeprint.otg.R

/** Which of the chosen pages to keep, by document page number. */
enum class Parity(@StringRes val labelRes: Int) { ALL(R.string.parity_all), ODD(R.string.parity_odd), EVEN(R.string.parity_even) }

/** Outcome of parsing a page-range string. Page numbers are 1-based in text, 0-based in [Pages.indices]. */
sealed interface PageSelection {
    data class Pages(val indices: List<Int>) : PageSelection
    /**
     * [message] is English, for logs and tests. The UI shows [errorRes] formatted with [args] in the user's language.
     */
    data class Invalid(val message: String, @StringRes val errorRes: Int, val args: List<Any> = emptyList()) : PageSelection

    companion object {
        /**
         * The reverse of [parse]: writes 0-based [indices] as a compact range string such as "1-3, 5, 8-10". Indices are
         * sorted and de-duplicated first. Empty input gives an empty string, which [parse] reads as "every page", so callers
         * must not pass an empty list when they mean "no pages".
         */
        fun format(indices: List<Int>): String {
            val pages = indices.map { it + 1 }.distinct().sorted()
            val parts = ArrayList<String>()
            var i = 0
            while (i < pages.size) {
                var j = i
                while (j + 1 < pages.size && pages[j + 1] == pages[j] + 1) j++
                parts += if (j == i) "${pages[i]}" else "${pages[i]}-${pages[j]}"
                i = j + 1
            }
            return parts.joinToString(", ")
        }

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
                        else -> return Invalid("\"$t\" is not a valid range.", R.string.err_bad_range, listOf(t))
                    }
                } catch (_: NumberFormatException) {
                    return Invalid("\"$t\" is not a valid page or range.", R.string.err_bad_range, listOf(t))
                }
                if (from < 1 || to < 1) return Invalid("Pages start at 1.", R.string.err_starts_at_one)
                if (from > to) return Invalid("\"$t\" runs backwards.", R.string.err_backwards, listOf(t))
                if (to > pageCount) return Invalid("Page $to is past the end ($pageCount page${if (pageCount == 1) "" else "s"}).", R.string.err_past_end, listOf(to, pageCount))
                wanted.addAll(from..to)
            }
            val kept = wanted.filter { parity == Parity.ALL || (parity == Parity.ODD) == (it % 2 == 1) }
            if (kept.isEmpty()) return Invalid("No pages match.", R.string.err_no_match)
            return Pages(kept.map { it - 1 })
        }
    }
}

/** A view of [source] showing only [indices], in order. Does not own or close [source]. */
class SelectedPages(private val source: PageSource, private val indices: List<Int>) : PageSource {
    override val pageCount get() = indices.size
    override fun unitsPerInch(index: Int) = source.unitsPerInch(indices[index])
    override fun pageSize(index: Int) = source.pageSize(indices[index])
    override fun render(index: Int, bitmap: Bitmap, matrix: Matrix) = source.render(indices[index], bitmap, matrix)
    override fun close() {}
}
