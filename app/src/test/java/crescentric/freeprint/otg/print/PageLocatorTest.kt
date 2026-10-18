package crescentric.freeprint.otg.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageLocatorTest {
    private val counts = listOf(3, 1, 2)

    @Test fun findsTheFileAndThePageInsideIt() {
        assertEquals(0 to 0, PageLocator.locate(counts, 0))
        assertEquals(0 to 2, PageLocator.locate(counts, 2))
        assertEquals(1 to 0, PageLocator.locate(counts, 3))
        assertEquals(2 to 1, PageLocator.locate(counts, 5))
    }

    @Test fun outOfRangeIsNull() {
        assertNull(PageLocator.locate(counts, 6))
        assertNull(PageLocator.locate(counts, -1))
        assertNull(PageLocator.locate(emptyList(), 0))
    }
}
