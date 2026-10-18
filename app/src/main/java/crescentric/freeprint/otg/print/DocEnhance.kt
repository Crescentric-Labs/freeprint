package crescentric.freeprint.otg.print

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * "Optimize for print" for photographed documents and notes: evens out uneven light and shadows, turns the grey paper white and
 * deepens the ink, so the page prints crisp and uses less ink than a grey photo of paper would.
 *
 * How: the brightness of the paper is estimated on a coarse grid (the brightest thing nearby is the paper, not the ink), every pixel
 * is divided by that estimate (which flattens shadows and whitens the paper), and a levels curve then clips the near-white to white and
 * the near-black to black. Colour is kept; each channel is treated the same way.
 */
object DocEnhance {
    /** Cells along the longer side of the picture for the paper estimate. Strokes are thinner than a cell, so ink barely moves it. */
    const val CELLS = 32
    /** After flattening, values at or below this become black and at or above [WHITE_POINT] become white. */
    const val BLACK_POINT = 30
    const val WHITE_POINT = 215
    private const val FADE_FROM = 40
    private const val FADE_TO = 100
    private const val GAMMA = 1.5
    private const val STRIP_ROWS = 64

    /** Enhances [bitmap] in place, estimating the paper from the bitmap itself. It must be mutable. Works a strip at a time. */
    fun apply(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 2 || h < 2) return
        val strip = IntArray(w * STRIP_ROWS)
        val builder = Background.Builder(w, h)
        for (y0 in 0 until h step STRIP_ROWS) {
            val rows = min(STRIP_ROWS, h - y0)
            bitmap.getPixels(strip, 0, w, 0, y0, w, rows)
            builder.addRows(strip, y0, rows)
        }
        val paper = builder.build()
        val sampler = paper.sampler(w)
        val rowPaper = IntArray(w)
        for (y0 in 0 until h step STRIP_ROWS) {
            val rows = min(STRIP_ROWS, h - y0)
            bitmap.getPixels(strip, 0, w, 0, y0, w, rows)
            for (yy in 0 until rows) {
                sampler.row((y0 + yy + 0.5f) / h, rowPaper)
                val base = yy * w
                for (x in 0 until w) strip[base + x] = pixel(strip[base + x], rowPaper[x])
            }
            bitmap.setPixels(strip, 0, w, 0, y0, w, rows)
        }
    }

    /**
     * Enhances [bitmap] in place using a paper colour supplied per pixel by [paperAt] (x, y in the bitmap; packed rgb, or [KEEP] to
     * leave that pixel alone). Used when the paper estimate comes from the whole page but only a piece of it is in [bitmap].
     */
    fun applyWith(bitmap: Bitmap, paperAt: (Int, Int) -> Int) {
        val w = bitmap.width
        val h = bitmap.height
        val strip = IntArray(w * STRIP_ROWS)
        for (y0 in 0 until h step STRIP_ROWS) {
            val rows = min(STRIP_ROWS, h - y0)
            bitmap.getPixels(strip, 0, w, 0, y0, w, rows)
            for (yy in 0 until rows) for (x in 0 until w) {
                val paper = paperAt(x, y0 + yy)
                if (paper != KEEP) strip[yy * w + x] = pixel(strip[yy * w + x], paper)
            }
            bitmap.setPixels(strip, 0, w, 0, y0, w, rows)
        }
    }

    /** What [applyWith]'s lookup returns for a pixel that is to be left as it is. */
    const val KEEP = -1

    /** One pixel ([argb]) given the estimated paper colour [paper] (packed rgb) at that spot. Alpha is kept. */
    fun pixel(argb: Int, paper: Int): Int =
        (argb and 0xFF000000.toInt()) or (channel(argb, paper, 16) shl 16) or (channel(argb, paper, 8) shl 8) or channel(argb, paper, 0)

    private fun channel(argb: Int, paper: Int, shift: Int): Int {
        val v = (argb shr shift) and 0xFF
        val flat = min(255, v * 255 / DIVISOR[(paper shr shift) and 0xFF])
        return CURVE[((flat - BLACK_POINT) * 255 / (WHITE_POINT - BLACK_POINT)).coerceIn(0, 255)]
    }

    /**
     * What to divide by for each paper estimate. Dark surroundings (a table around the page) have a dark estimate; the flattening is
     * faded out there (the divisor tends to 255, which changes nothing) so they stay dark instead of being amplified into noise.
     */
    private val DIVISOR = IntArray(256) { estimate ->
        val trust = ((estimate - FADE_FROM) / (FADE_TO - FADE_FROM).toFloat()).coerceIn(0f, 1f)
        (255 + (estimate - 255) * trust).toInt().coerceAtLeast(1)
    }

    /** Levels stretch followed by a gamma that deepens mid-greys, so faint pencil and pale ink print readable. */
    private val CURVE = IntArray(256) { i -> (255.0 * Math.pow(i / 255.0, GAMMA) + 0.5).toInt().coerceIn(0, 255) }

    /** A smooth, coarse map of how bright the paper is across the picture. */
    class Background private constructor(private val w: Int, private val h: Int, private val px: IntArray) {

        /** The paper colour (packed rgb) at ([u], [v]) in 0..1 across and down, by bilinear interpolation between grid cells. */
        fun at(u: Float, v: Float): Int {
            val x = (u * w - 0.5f).coerceIn(0f, (w - 1).toFloat())
            val y = (v * h - 0.5f).coerceIn(0f, (h - 1).toFloat())
            val x0 = x.toInt(); val y0 = y.toInt()
            val x1 = min(x0 + 1, w - 1); val y1 = min(y0 + 1, h - 1)
            val fx = x - x0; val fy = y - y0
            fun ch(shift: Int): Int {
                val a = (px[y0 * w + x0] shr shift) and 0xFF
                val b = (px[y0 * w + x1] shr shift) and 0xFF
                val c = (px[y1 * w + x0] shr shift) and 0xFF
                val d = (px[y1 * w + x1] shr shift) and 0xFF
                val top = a + (b - a) * fx
                val bottom = c + (d - c) * fx
                return (top + (bottom - top) * fy + 0.5f).toInt().coerceIn(0, 255)
            }
            return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        /** Samples whole rows of the paper map for a picture [width] pixels wide, reusing the column maths between rows. */
        inner class Sampler(width: Int) {
            private val x0 = IntArray(width)
            private val x1 = IntArray(width)
            private val fx = FloatArray(width)

            init {
                for (x in 0 until width) {
                    val gx = ((x + 0.5f) / width * w - 0.5f).coerceIn(0f, (w - 1).toFloat())
                    x0[x] = gx.toInt(); x1[x] = min(x0[x] + 1, w - 1); fx[x] = gx - x0[x]
                }
            }

            /** Fills [out] with the paper colour for every pixel of the row at [v] (0..1 down the picture). */
            fun row(v: Float, out: IntArray) {
                val gy = (v * h - 0.5f).coerceIn(0f, (h - 1).toFloat())
                val y0 = gy.toInt(); val y1 = min(y0 + 1, h - 1); val fy = gy - y0
                for (x in 0 until out.size) {
                    val a = px[y0 * w + x0[x]]; val b = px[y0 * w + x1[x]]
                    val c = px[y1 * w + x0[x]]; val d = px[y1 * w + x1[x]]
                    val f = fx[x]
                    out[x] = (mix(a, b, c, d, f, fy, 16) shl 16) or (mix(a, b, c, d, f, fy, 8) shl 8) or mix(a, b, c, d, f, fy, 0)
                }
            }

            private fun mix(a: Int, b: Int, c: Int, d: Int, fx: Float, fy: Float, shift: Int): Int {
                val ca = (a shr shift) and 0xFF; val cb = (b shr shift) and 0xFF
                val cc = (c shr shift) and 0xFF; val cd = (d shr shift) and 0xFF
                val top = ca + (cb - ca) * fx
                val bottom = cc + (cd - cc) * fx
                return (top + (bottom - top) * fy + 0.5f).toInt().coerceIn(0, 255)
            }
        }

        fun sampler(width: Int) = Sampler(width)

        /** Averages the picture into grid cells, a few rows at a time. */
        class Builder(private val width: Int, private val height: Int) {
            private val scale = CELLS.toFloat() / max(width, height)
            private val gw = max(2, (width * scale).toInt())
            private val gh = max(2, (height * scale).toInt())
            private val sum = LongArray(gw * gh * 3)
            private val count = IntArray(gw * gh)

            /** Adds [rows] rows of [pixels] (width ints per row), the first of which is row [y0] of the picture. */
            fun addRows(pixels: IntArray, y0: Int, rows: Int) {
                for (yy in 0 until rows) {
                    val cy = min(gh - 1, (y0 + yy) * gh / height)
                    for (x in 0 until width) {
                        val cx = min(gw - 1, x * gw / width)
                        val c = pixels[yy * width + x]
                        val cell = cy * gw + cx
                        sum[cell * 3] += ((c shr 16) and 0xFF).toLong()
                        sum[cell * 3 + 1] += ((c shr 8) and 0xFF).toLong()
                        sum[cell * 3 + 2] += (c and 0xFF).toLong()
                        count[cell]++
                    }
                }
            }

            fun build(): Background {
                val cells = IntArray(gw * gh) { i ->
                    val n = max(count[i], 1)
                    (((sum[i * 3] / n).toInt()) shl 16) or (((sum[i * 3 + 1] / n).toInt()) shl 8) or (sum[i * 3 + 2] / n).toInt()
                }
                // The paper is the brightest thing around: take the brightest of each cell's neighbours, then smooth.
                return Background(gw, gh, blur(grow(grow(cells, gw, gh), gw, gh), gw, gh))
            }
        }

        private companion object {
            /** Each cell takes the brightest value of itself and its 8 neighbours, per channel. */
            fun grow(src: IntArray, w: Int, h: Int): IntArray = filter(src, w, h) { values -> values.max() }

            /** Each cell becomes the average of itself and its 8 neighbours, per channel. */
            fun blur(src: IntArray, w: Int, h: Int): IntArray = filter(src, w, h) { values -> values.sum() / values.size }

            private inline fun filter(src: IntArray, w: Int, h: Int, op: (List<Int>) -> Int): IntArray {
                val out = IntArray(w * h)
                val values = ArrayList<Int>(9)
                for (y in 0 until h) for (x in 0 until w) {
                    var packed = 0
                    for (shift in intArrayOf(16, 8, 0)) {
                        values.clear()
                        for (dy in -1..1) for (dx in -1..1) {
                            val nx = x + dx; val ny = y + dy
                            if (nx in 0 until w && ny in 0 until h) values += (src[ny * w + nx] shr shift) and 0xFF
                        }
                        packed = packed or (op(values).coerceIn(0, 255) shl shift)
                    }
                    out[y * w + x] = packed
                }
                return out
            }
        }
    }
}
