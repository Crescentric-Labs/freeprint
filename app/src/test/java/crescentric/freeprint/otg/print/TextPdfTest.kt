package crescentric.freeprint.otg.print

import org.junit.Assert.assertEquals
import org.junit.Test

class TextPdfTest {
    @Test fun aSharedFileKeepsItsNameWithoutTheExtension() {
        assertEquals("Garden-meeting-notes", TextPdf.pdfName("Garden-meeting-notes.txt", null))
        assertEquals("notes.v2", TextPdf.pdfName("notes.v2.md", "A subject"))
    }

    @Test fun theSubjectNamesSharedTextWhenThereIsNoFile() {
        assertEquals("Garden meeting", TextPdf.pdfName(null, "Garden meeting"))
    }

    @Test fun withNeitherItIsSharedText() {
        assertEquals("Shared text", TextPdf.pdfName(null, null))
        assertEquals("Shared text", TextPdf.pdfName("", "  "))
        assertEquals("Shared text", TextPdf.pdfName(".hidden", null))
    }

    @Test fun charactersAFileNameCannotHoldAreReplacedAndLongNamesCut() {
        assertEquals("a b c", TextPdf.pdfName("a/b:c.txt", null))
        assertEquals(40, TextPdf.pdfName("x".repeat(100) + ".txt", null).length)
    }

    @Test fun aNameWithoutAnExtensionIsKept() {
        assertEquals("README", TextPdf.pdfName("README", null))
    }
}
