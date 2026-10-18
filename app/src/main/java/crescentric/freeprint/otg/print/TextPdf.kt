package crescentric.freeprint.otg.print

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File

/**
 * Turns shared text (a note, a message, an address, a link) into a plain A4 PDF so it can be previewed and printed like any
 * document. Lines are wrapped to the page by [StaticLayout], so any script the phone can draw is laid out properly, and the text
 * runs onto further pages as needed. An optional [title] (for example the subject of a shared message) is set above it.
 */
object TextPdf {
    private const val PAGE_W = 595          // A4 in points
    private const val PAGE_H = 842
    private const val MARGIN = 54
    /** Beyond this a share is cut off, so a huge paste cannot fill the phone with pages. */
    private const val MAX_CHARS = 400_000

    fun render(context: Context, text: String, title: String? = null): File {
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11.5f; color = Color.BLACK; typeface = Typeface.SANS_SERIF }
        val head = TextPaint(body).apply { textSize = 16f; isFakeBoldText = true }
        val width = PAGE_W - 2 * MARGIN
        val content = text.take(MAX_CHARS)

        val doc = PdfDocument()
        var number = 0
        var page: PdfDocument.Page? = null
        var y = MARGIN.toFloat()
        fun newPage() {
            page?.let { doc.finishPage(it) }
            number++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, number).create())
            y = MARGIN.toFloat()
        }
        newPage()

        if (!title.isNullOrBlank()) {
            val t = StaticLayout.Builder.obtain(title, 0, title.length, head, width).setLineSpacing(2f, 1f).build()
            page!!.canvas.save(); page!!.canvas.translate(MARGIN.toFloat(), y); t.draw(page!!.canvas); page!!.canvas.restore()
            y += t.height + 14f
        }

        val layout = StaticLayout.Builder.obtain(content, 0, content.length, body, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(3f, 1f).build()
        for (i in 0 until layout.lineCount) {
            val height = (layout.getLineBottom(i) - layout.getLineTop(i)).toFloat()
            if (y + height > PAGE_H - MARGIN) newPage()
            val baseline = y + (layout.getLineBaseline(i) - layout.getLineTop(i))
            page!!.canvas.drawText(content, layout.getLineStart(i), layout.getLineEnd(i), MARGIN.toFloat(), baseline, body)
            y += height
        }
        page?.let { doc.finishPage(it) }

        // Named after the subject when there is one, so the file list shows something readable; the folder keeps names apart
        val name = title?.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")?.trim()?.take(40)?.takeIf { it.isNotEmpty() } ?: "Shared text"
        val dir = File(context.cacheDir, "shared/${System.currentTimeMillis()}").apply { mkdirs() }
        val file = File(dir, "$name.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
