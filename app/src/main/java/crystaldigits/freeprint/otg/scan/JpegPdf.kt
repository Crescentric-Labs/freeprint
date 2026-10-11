package crystaldigits.freeprint.otg.scan

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale

/** One scanned page: the JPEG exactly as the scanner sent it, and what is needed to place it on a PDF page. */
class PdfPageImage(val jpeg: ByteArray, val widthPx: Int, val heightPx: Int, val gray: Boolean, val dpi: Int)

/**
 * Writes a PDF that holds scanned JPEGs untouched (DCTDecode), one per page, sized so the picture fills the page at its
 * scan resolution. This keeps a scan the size of the JPEG instead of re-encoding it, and needs no PDF library.
 *
 * Layout: 1 catalog, 2 page tree, then per page three objects: page, content stream, image.
 */
object JpegPdf {
    fun write(pages: List<PdfPageImage>, out: OutputStream) {
        require(pages.isNotEmpty()) { "no pages" }
        val buf = ByteArrayOutputStream()
        val offsets = ArrayList<Int>()          // byte offset of object 1, 2, 3...
        fun put(s: String) = buf.write(s.toByteArray(Charsets.ISO_8859_1))
        fun obj(body: () -> Unit) {
            offsets += buf.size()
            put("${offsets.size} 0 obj\n"); body(); put("endobj\n")
        }

        put("%PDF-1.4\n%âãÏÓ\n")
        obj { put("<< /Type /Catalog /Pages 2 0 R >>\n") }
        obj {
            val kids = pages.indices.joinToString(" ") { "${3 + 3 * it} 0 R" }
            put("<< /Type /Pages /Count ${pages.size} /Kids [$kids] >>\n")
        }
        pages.forEachIndexed { i, p ->
            val w = String.format(Locale.ROOT, "%.2f", p.widthPx * 72.0 / p.dpi)
            val h = String.format(Locale.ROOT, "%.2f", p.heightPx * 72.0 / p.dpi)
            val content = "q $w 0 0 $h 0 0 cm /Im0 Do Q\n"
            obj { put("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $w $h] /Resources << /XObject << /Im0 ${5 + 3 * i} 0 R >> >> /Contents ${4 + 3 * i} 0 R >>\n") }
            obj { put("<< /Length ${content.length} >>\nstream\n$content" + "endstream\n") }
            obj {
                put("<< /Type /XObject /Subtype /Image /Width ${p.widthPx} /Height ${p.heightPx} " +
                    "/ColorSpace /${if (p.gray) "DeviceGray" else "DeviceRGB"} /BitsPerComponent 8 /Filter /DCTDecode /Length ${p.jpeg.size} >>\nstream\n")
                buf.write(p.jpeg)
                put("\nendstream\n")
            }
        }

        val xrefAt = buf.size()
        put("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { put(String.format(Locale.ROOT, "%010d 00000 n \n", it)) }
        put("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xrefAt\n%%EOF\n")
        buf.writeTo(out)
    }
}
