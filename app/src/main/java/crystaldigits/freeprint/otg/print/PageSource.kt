package crystaldigits.freeprint.otg.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlin.math.max

/** Something printable made of pages. Not thread-safe: use from one thread at a time. */
interface PageSource : AutoCloseable {
    val pageCount: Int

    /** Page size in the source's own units (PDF points, image pixels). */
    fun pageSize(index: Int): Pair<Float, Float>

    /** Source units per inch: 72 for PDF points; images are assumed to be 300 dpi. */
    val unitsPerInch: Float get() = 72f

    /** Draws page [index] into [bitmap] (already white) through [matrix], source units to bitmap pixels. */
    fun render(index: Int, bitmap: Bitmap, matrix: Matrix)

    companion object {
        /** Opens a PDF or image; the type is taken from the content resolver. */
        fun open(context: Context, uri: Uri, mime: String?): PageSource =
            if (mime == "application/pdf") PdfSource(context, uri) else ImageSource(context, uri)
    }
}

/** A PDF. Takes ownership of [pfd] (it must be seekable) and closes it with the source. */
class PdfSource(private val pfd: ParcelFileDescriptor) : PageSource {
    constructor(context: Context, uri: Uri) : this(
        context.contentResolver.openFileDescriptor(uri, "r") ?: error("cannot open $uri"),
    )

    private val renderer = PdfRenderer(pfd)

    override val pageCount get() = renderer.pageCount

    override fun pageSize(index: Int): Pair<Float, Float> =
        renderer.openPage(index).use { it.width.toFloat() to it.height.toFloat() }

    override fun render(index: Int, bitmap: Bitmap, matrix: Matrix) {
        renderer.openPage(index).use { it.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT) }
    }

    override fun close() { renderer.close(); pfd.close() }
}

class ImageSource(context: Context, uri: Uri) : PageSource {
    // ImageDecoder applies EXIF rotation. Cap the longest side so a huge photo can't exhaust memory.
    private val bitmap: Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { dec, info, _ ->
        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = max(info.size.width, info.size.height)
        if (longest > MAX_SIDE) {
            val f = MAX_SIDE.toFloat() / longest
            dec.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
        }
    }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    override val pageCount get() = 1
    override val unitsPerInch get() = 300f
    override fun pageSize(index: Int) = bitmap.width.toFloat() to bitmap.height.toFloat()
    override fun render(index: Int, bitmap: Bitmap, matrix: Matrix) {
        Canvas(bitmap).drawBitmap(this.bitmap, matrix, paint)
    }
    override fun close() = bitmap.recycle()

    private companion object { const val MAX_SIDE = 7000 }
}
