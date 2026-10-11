package crystaldigits.freeprint.otg.print

import android.graphics.Bitmap
import android.graphics.Matrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeSourceTest {
    /** A fake document: [pages] pages, each [w] x [h], measured in [upi] units per inch. */
    private class Fake(val pages: Int, val w: Float, val h: Float, val upi: Float) : PageSource {
        var closed = false
        override val pageCount get() = pages
        override fun pageSize(index: Int): Pair<Float, Float> { require(index in 0 until pages); return w to h }
        override fun unitsPerInch(index: Int) = upi
        override fun render(index: Int, bitmap: Bitmap, matrix: Matrix) {}
        override fun close() { closed = true }
    }

    private val pdf = Fake(3, 595f, 842f, 72f)
    private val photo = Fake(1, 4000f, 3000f, 300f)
    private val pdf2 = Fake(2, 612f, 792f, 72f)

    @Test fun pageCountIsTheSum() = assertEquals(6, CompositeSource(listOf(pdf, photo, pdf2)).pageCount)

    @Test fun pagesKeepListOrderAndEachSourcesOwnSizeAndUnits() {
        val c = CompositeSource(listOf(pdf, photo, pdf2))
        assertEquals(595f to 842f, c.pageSize(0))
        assertEquals(595f to 842f, c.pageSize(2))
        assertEquals(4000f to 3000f, c.pageSize(3))     // the photo
        assertEquals(300f, c.unitsPerInch(3), 0f)
        assertEquals(72f, c.unitsPerInch(2), 0f)
        assertEquals(612f to 792f, c.pageSize(4))
        assertEquals(612f to 792f, c.pageSize(5))
        assertEquals(72f, c.unitsPerInch(5), 0f)
    }

    @Test fun emptyDocumentsAreSkipped() {
        val c = CompositeSource(listOf(Fake(0, 1f, 1f, 72f), pdf2))
        assertEquals(2, c.pageCount)
        assertEquals(612f to 792f, c.pageSize(0))
    }

    @Test fun singleSourceBehavesLikeItself() {
        val c = CompositeSource(listOf(pdf))
        assertEquals(3, c.pageCount)
        assertEquals(595f to 842f, c.pageSize(2))
    }

    @Test fun outOfRangeIsRejected() {
        val c = CompositeSource(listOf(pdf))
        var failed = false
        try { c.pageSize(3) } catch (e: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }

    @Test fun closeClosesEverySource() {
        CompositeSource(listOf(pdf, photo, pdf2)).close()
        assertTrue(pdf.closed && photo.closed && pdf2.closed)
    }
}
