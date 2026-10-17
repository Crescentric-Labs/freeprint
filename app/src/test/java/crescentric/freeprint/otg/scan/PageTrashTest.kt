package crescentric.freeprint.otg.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTrashTest {
    private val original = listOf("a", "b", "c", "d", "e")

    /** Deletes the item at [index], as the scan screen does: remember its position, then drop it from the list. */
    private fun delete(list: List<String>, index: Int, trash: PageTrash<String>): List<String> {
        trash.add(index, list[index])
        return list.filterIndexed { i, _ -> i != index }
    }

    @Test fun oneDeletionComesBackWhereItWas() {
        val trash = PageTrash<String>()
        val after = delete(original, 2, trash)
        assertEquals(listOf("a", "b", "d", "e"), after)
        assertEquals(original, trash.restoreInto(after))
    }

    @Test fun severalDeletionsComeBackInTheirOriginalOrder() {
        val trash = PageTrash<String>()
        var list = delete(original, 1, trash)          // b
        list = delete(list, 2, trash)                  // d, which is index 2 in [a, c, d, e]
        list = delete(list, 0, trash)                  // a
        assertEquals(listOf("c", "e"), list)
        assertEquals(original, trash.restoreInto(list))
    }

    @Test fun deletingTheEndsWorks() {
        val trash = PageTrash<String>()
        var list = delete(original, 4, trash)
        list = delete(list, 0, trash)
        assertEquals(original, trash.restoreInto(list))
    }

    @Test fun pagesAddedAfterwardsStayAndTheDeletedOnesGoBackToTheirSlots() {
        val trash = PageTrash<String>()
        val list = delete(original, 1, trash) + "f"    // [a, c, d, e, f]
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), trash.restoreInto(list))
    }

    @Test fun aPositionPastTheEndIsClampedNotLost() {
        val trash = PageTrash<String>()
        trash.add(9, "z")
        assertEquals(listOf("a", "z"), trash.restoreInto(listOf("a")))
    }

    @Test fun clearEmptiesIt() {
        val trash = PageTrash<String>()
        trash.add(0, "a")
        assertEquals(1, trash.size)
        trash.clear()
        assertTrue(trash.isEmpty)
        assertEquals(original, trash.restoreInto(original))
    }

    @Test fun restoreDoesNotEmptyTheTrash() {
        val trash = PageTrash<String>()
        trash.add(0, "a")
        trash.restoreInto(listOf("b"))
        assertEquals(1, trash.size)
        assertEquals(listOf("a"), trash.all())
    }
}
