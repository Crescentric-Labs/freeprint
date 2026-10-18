package crescentric.freeprint.otg.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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
        fun open(context: Context, uri: Uri, mime: String?, enhancedPages: Set<Int> = emptySet()): PageSource =
            if (mime == "application/pdf") PdfSource(context, uri, enhancedPages) else ImageSource(context, uri, 0 in enhancedPages)
    }
}

/**
 * A PDF. Takes ownership of [pfd] (it must be seekable) and closes it with the source.
 *
 * Pages whose index is in [enhancedPages] are optimized for print: each is drawn on its own layer, run through [DocEnhance] and multiplied onto the target, so only that page's
 * pixels change when several pages share a sheet. The paper estimate comes from a small render of the whole page (cached), not from
 * the strip being drawn, so every band of a page is treated the same and no seams show.
 */
class PdfSource(private val pfd: ParcelFileDescriptor, private val enhancedPages: Set<Int> = emptySet()) : PageSource {
    constructor(context: Context, uri: Uri, enhancedPages: Set<Int> = emptySet()) : this(
        context.contentResolver.openFileDescriptor(uri, "r") ?: error("cannot open $uri"),
        enhancedPages,
    )

    private val renderer = PdfRenderer(pfd)
    private val papers = HashMap<Int, DocEnhance.Background>()
    private val multiply = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY) }

    override val pageCount get() = renderer.pageCount

    override fun pageSize(index: Int): Pair<Float, Float> =
        renderer.openPage(index).use { it.width.toFloat() to it.height.toFloat() }

    override fun render(index: Int, bitmap: Bitmap, matrix: Matrix) {
        if (index !in enhancedPages) {
            renderer.openPage(index).use { it.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT) }
            return
        }
        val (pageW, pageH) = pageSize(index)
        val inverse = Matrix()
        if (!matrix.invert(inverse)) return
        val m = FloatArray(9).also { inverse.getValues(it) }
        val paper = paperFor(index, pageW, pageH)

        val layer = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            renderer.openPage(index).use { it.render(layer, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT) }
            DocEnhance.applyWith(layer) { x, y ->
                // Where this pixel falls on the page, as a fraction across and down; outside the page it is left white
                val sx = m[0] * (x + 0.5f) + m[1] * (y + 0.5f) + m[2]
                val sy = m[3] * (x + 0.5f) + m[4] * (y + 0.5f) + m[5]
                val u = sx / pageW
                val v = sy / pageH
                if (u < 0f || u > 1f || v < 0f || v > 1f) DocEnhance.KEEP else paper.at(u, v)
            }
            Canvas(bitmap).drawBitmap(layer, 0f, 0f, multiply)
        } finally {
            layer.recycle()
        }
    }

    /** The paper estimate for a page, from a small render of the whole page. */
    private fun paperFor(index: Int, pageW: Float, pageH: Float): DocEnhance.Background = papers.getOrPut(index) {
        val scale = PAPER_PROBE_SIDE / max(pageW, pageH)
        val w = max(2, (pageW * scale).toInt())
        val h = max(2, (pageH * scale).toInt())
        val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            renderer.openPage(index).use { it.render(small, null, Matrix().apply { setScale(w / pageW, h / pageH) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)
            DocEnhance.Background.Builder(w, h).also { it.addRows(px, 0, h) }.build()
        } finally {
            small.recycle()
        }
    }

    override fun close() { renderer.close(); pfd.close() }

    private companion object { const val PAPER_PROBE_SIDE = 256f }
}

/**
 * An image as a single page. Nothing big is held: the constructor only measures the image, and each render decodes
 * it at the resolution that render needs (small for previews and thumbnails, up to [MAX_SIDE] when printing). The most
 * recent decode is shared across all images in [DecodedCache], so a job of many photos holds at most one at a time.
 * With [enhance] on, each decode is run through [DocEnhance] ("optimize for print"); the original file is never touched.
 */
class ImageSource(private val context: Context, private val uri: Uri, private val enhance: Boolean = false) : PageSource {
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
        val decoded = DecodedCache.get(uri, needed, enhance) { decode(needed) }
        // The decoded bitmap may be smaller than the image; scale it back up to source units before the caller's matrix.
        val toSource = Matrix(matrix).apply { preScale(width / decoded.width.toFloat(), height / decoded.height.toFloat()) }
        Canvas(bitmap).drawBitmap(decoded, toSource, paint)
    }

    private fun decode(longest: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            if (enhance) dec.isMutableRequired = true
            val f = longest.toFloat() / max(info.size.width, info.size.height)
            if (f < 1f) dec.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
        }.also { if (enhance) DocEnhance.apply(it) }

    override fun close() {}

    private companion object { const val MAX_SIDE = 7000 }
}

/** Single-entry cache of the last decoded image, shared by every [ImageSource]. Evicted bitmaps are left to the GC. */
private object DecodedCache {
    private var key: String? = null
    private var bitmap: Bitmap? = null

    @Synchronized
    fun get(uri: Uri, longest: Int, enhanced: Boolean, decode: () -> Bitmap): Bitmap {
        val k = "$uri#$longest#$enhanced"
        bitmap?.let { if (k == key) return it }
        return decode().also { key = k; bitmap = it }
    }
}
