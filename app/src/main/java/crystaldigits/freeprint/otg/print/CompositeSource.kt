package crystaldigits.freeprint.otg.print

import android.graphics.Bitmap
import android.graphics.Matrix

/**
 * Several documents (PDFs and images, in any mix) joined into one run of pages, in list order.
 * Page size, units and rendering are delegated to the document a page belongs to. Closing it closes all of them.
 */
class CompositeSource(private val sources: List<PageSource>) : PageSource {
    /** firstPage[i] is the combined index of sources[i]'s first page. */
    private val firstPage = IntArray(sources.size + 1).also { starts ->
        sources.forEachIndexed { i, s -> starts[i + 1] = starts[i] + s.pageCount }
    }

    override val pageCount get() = firstPage.last()

    /** The source holding combined page [index] and that page's index inside it. */
    private fun locate(index: Int): Pair<PageSource, Int> {
        require(index in 0 until pageCount) { "page $index of $pageCount" }
        var i = sources.lastIndex
        while (firstPage[i] > index) i--
        return sources[i] to (index - firstPage[i])
    }

    override fun pageSize(index: Int) = locate(index).let { (s, p) -> s.pageSize(p) }
    override fun unitsPerInch(index: Int) = locate(index).let { (s, p) -> s.unitsPerInch(p) }
    override fun render(index: Int, bitmap: Bitmap, matrix: Matrix) = locate(index).let { (s, p) -> s.render(p, bitmap, matrix) }

    override fun close() { sources.forEach { runCatching { it.close() } } }

    companion object {
        /**
         * Opens every entry of [files] (uri to mime type). If one fails, the ones already opened are closed
         * and the exception is rethrown, so nothing leaks.
         */
        fun open(context: android.content.Context, files: List<Pair<android.net.Uri, String?>>): CompositeSource {
            val opened = ArrayList<PageSource>()
            try {
                files.forEach { (uri, mime) -> opened += PageSource.open(context, uri, mime) }
            } catch (e: Exception) {
                opened.forEach { runCatching { it.close() } }
                throw e
            }
            return CompositeSource(opened)
        }
    }
}
