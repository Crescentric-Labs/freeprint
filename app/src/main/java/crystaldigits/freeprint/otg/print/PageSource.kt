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
import android.util.Size
import kotlin.math.ceil
import kotlin.math.max

/** Something printable made of pages. Not thread-safe: use from one thread at a time. */
interface PageSource : AutoCloseable {
    val pageCount: Int

    /** Page size in the source's own units (PDF points, image pixels). */
    fun pageSize(index: Int): Pair<Float, Float>

    /** Source units per inch for page [index]: 72 for PDF points; images are assumed to be 300 dpi. */
    fun unitsPerInch(index: Int): Float = 72f

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

/**
 * An image as a single page. Nothing big is held: the constructor only measures the image, and each render decodes
 * it at the resolution that render needs (small for previews and thumbnails, up to [MAX_SIDE] when printing). The most
 * recent decode is shared across all images in [DecodedCache], so a job of many photos holds at most one at a time.
 */
class ImageSource(private val context: Context, private val uri: Uri) : PageSource {
    /** Size in pixels after EXIF rotation, as the picture is meant to be seen. */
    private val width: Int
    private val height: Int
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    init {
        var raw = Size(1, 1)
        // Decode a 1/16 version just to learn the real orientation: ImageDecoder applies EXIF rotation to the pixels.
        val probe = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            raw = info.size
            dec.setTargetSize((info.size.width / 16).coerceAtLeast(1), (info.size.height / 16).coerceAtLeast(1))
        }
        val turned = raw.width != raw.height && (probe.width > probe.height) != (raw.width > raw.height)
        width = if (turned) raw.height else raw.width
        height = if (turned) raw.width else raw.height
        probe.recycle()
    }

    override val pageCount get() = 1
    override fun unitsPerInch(index: Int) = 300f
    override fun pageSize(index: Int) = width.toFloat() to height.toFloat()

    override fun render(index: Int, bitmap: Bitmap, matrix: Matrix) {
        // Pixels this render actually needs along the longest side, never more than the image has.
        val needed = ceil(max(width, height) * matrix.mapRadius(1f)).toInt().coerceIn(16, minOf(MAX_SIDE, max(width, height)))
        val decoded = DecodedCache.get(uri, needed) { decode(needed) }
        // The decoded bitmap may be smaller than the image; scale it back up to source units before the caller's matrix.
        val toSource = Matrix(matrix).apply { preScale(width / decoded.width.toFloat(), height / decoded.height.toFloat()) }
        Canvas(bitmap).drawBitmap(decoded, toSource, paint)
    }

    private fun decode(longest: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val f = longest.toFloat() / max(info.size.width, info.size.height)
            if (f < 1f) dec.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
        }

    override fun close() {}

    private companion object { const val MAX_SIDE = 7000 }
}

/** Single-entry cache of the last decoded image, shared by every [ImageSource]. Evicted bitmaps are left to the GC. */
private object DecodedCache {
    private var key: String? = null
    private var bitmap: Bitmap? = null

    @Synchronized
    fun get(uri: Uri, longest: Int, decode: () -> Bitmap): Bitmap {
        val k = "$uri#$longest"
        bitmap?.let { if (k == key) return it }
        return decode().also { key = k; bitmap = it }
    }
}
