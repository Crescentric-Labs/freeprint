package crescentric.freeprint.otg.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class JpegPdfTest {
    // Not a decodable picture: the writer only embeds the bytes, so structure is what matters here.
    private fun fakeJpeg(n: Int, tag: Byte) = ByteArray(n) { tag }.also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte() }

    private fun pdf(vararg p: PdfPageImage): ByteArray = ByteArrayOutputStream().also { JpegPdf.write(p.toList(), it) }.toByteArray()

    @Test fun xrefOffsetsPointAtTheirObjects() {
        val bytes = pdf(PdfPageImage(fakeJpeg(500, 7), 2480, 3508, gray = false, dpi = 300), PdfPageImage(fakeJpeg(300, 9), 1240, 1754, gray = true, dpi = 150))
        val text = String(bytes, Charsets.ISO_8859_1)
        val startXref = text.substringAfterLast("startxref\n").substringBefore("\n").toInt()
        assertTrue(text.substring(startXref).startsWith("xref\n"))
        val lines = text.substring(startXref).lines()
        val count = lines[1].substringAfter(' ').toInt()
        assertEquals(1 + 2 + 3 * 2, count)                                  // free entry, catalog, tree, 2 pages x 3 objects
        for (i in 1 until count) {
            val off = lines[2 + i].substringBefore(' ').toInt()
            assertTrue("object $i at $off", text.substring(off).startsWith("$i 0 obj"))
        }
        assertTrue(text.trimEnd().endsWith("%%EOF"))
    }

    @Test fun pageSizeIsPixelsOverDpiInPoints() {
        val text = String(pdf(PdfPageImage(fakeJpeg(100, 1), 2480, 3508, gray = false, dpi = 300)), Charsets.ISO_8859_1)
        assertTrue(text.contains("/MediaBox [0 0 595.20 841.92]"))           // A4 at 300 dpi: 2480 / 300 * 72
    }

    @Test fun jpegBytesAreEmbeddedUntouched() {
        val jpeg = fakeJpeg(1000, 5)
        val bytes = pdf(PdfPageImage(jpeg, 100, 100, gray = true, dpi = 100))
        val at = String(bytes, Charsets.ISO_8859_1).indexOf("stream\n", String(bytes, Charsets.ISO_8859_1).indexOf("/DCTDecode")) + 7
        assertTrue(bytes.copyOfRange(at, at + jpeg.size).contentEquals(jpeg))
        assertTrue(String(bytes, Charsets.ISO_8859_1).contains("/DeviceGray"))
    }

    @Test fun colourPagesUseRgb() {
        assertTrue(String(pdf(PdfPageImage(fakeJpeg(50, 1), 10, 10, gray = false, dpi = 72)), Charsets.ISO_8859_1).contains("/DeviceRGB"))
    }

    @Test(expected = IllegalArgumentException::class) fun refusesAnEmptyDocument() { pdf() }
}
