package crescentric.freeprint.otg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTest {
    private val sample = """
        # Changelog

        Some introduction that is not a release.

        ## [Unreleased]

        ## [2.0.1] - 2026-10-19

        ### Added
        - Share to the app.
        - Open with the app.

        ### Fixed
        - A crash at start-up that
          happened from a share.

        ## [0.1.0] - 2026-10-11

        First working version.

        ### Added
        - Printing.

        [2.0.1]: https://example.invalid/2.0.1
    """.trimIndent()

    @Test fun readsReleasesInOrderAndSkipsAnEmptyUnreleased() {
        val r = Changelog.parse(sample)
        assertEquals(listOf("2.0.1", "0.1.0"), r.map { it.version })
        assertEquals("2026-10-19", r[0].date)
    }

    @Test fun readsSectionsAndEntries() {
        val r = Changelog.parse(sample)[0]
        assertEquals(listOf("Added", "Fixed"), r.sections.map { it.title })
        assertEquals(listOf("Share to the app.", "Open with the app."), r.sections[0].items)
    }

    @Test fun joinsAnEntryContinuedOnTheNextLine() {
        assertEquals("A crash at start-up that happened from a share.", Changelog.parse(sample)[0].sections[1].items.single())
    }

    @Test fun plainLinesUnderAReleaseAreItsNoteAndLinkDefinitionsAreIgnored() {
        val r = Changelog.parse(sample)[1]
        assertEquals("First working version.", r.note)
        assertTrue(Changelog.parse(sample).none { it.sections.any { s -> s.items.any { i -> i.contains("example.invalid") } } })
    }

    @Test fun splitsBoldAndCodeRunsAndDropsTheMarkers() {
        val s = Changelog.spans("A floating dock between **Print** and **Scan**, ids like `a.b.c` stay.")
        assertEquals(listOf("A floating dock between ", "Print", " and ", "Scan", ", ids like ", "a.b.c", " stay."), s.map { it.text })
        assertEquals(listOf(false, true, false, true, false, false, false), s.map { it.bold })
        assertEquals(listOf(false, false, false, false, false, true, false), s.map { it.code })
    }

    @Test fun anUnclosedMarkerIsKeptAsWritten() {
        assertEquals("2 ** 3 and a ` tick", Changelog.spans("2 ** 3 and a ` tick").joinToString("") { it.text })
        assertEquals(1, Changelog.spans("no markers at all").size)
    }

    @Test fun readsTheRealChangelogHeadings() {
        val text = java.io.File("../CHANGELOG.md").takeIf { it.exists() }?.readText() ?: return
        val r = Changelog.parse(text)
        assertTrue(r.isNotEmpty())
        assertTrue(r.all { it.version.isNotBlank() })
    }
}
