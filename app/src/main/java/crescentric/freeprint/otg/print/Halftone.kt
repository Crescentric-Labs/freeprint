package crescentric.freeprint.otg.print

/**
 * Turns rendered strips into the 1-bit rows a monochrome laser prints, by Floyd-Steinberg error diffusion. The error
 * left over by the last row of a strip carries into the next one, so strips join without a seam.
 */
class Halftone(private val width: Int) {
    private var carry = IntArray(width + 2)
    private var next = IntArray(width + 2)

    /**
     * Converts [rows] rows of ARGB [pixels] (width x rows) into [out] starting at byte [outOffset], [stride] bytes per row,
     * 1 = black, most significant bit = leftmost pixel. Rows of [out] are written whole, so they must be zero beforehand.
     */
    fun convert(pixels: IntArray, rows: Int, out: ByteArray, outOffset: Int, stride: Int) {
        for (y in 0 until rows) {
            java.util.Arrays.fill(next, 0)
            val row = y * width
            val dest = outOffset + y * stride
            for (x in 0 until width) {
                val p = pixels[row + x]
                val a = p ushr 24
                // Transparent counts as white paper.
                val lum = (((((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 151 + (p and 0xFF) * 28) shr 8) * a + 255 * (255 - a)) / 255
                val wanted = lum + carry[x + 1] / 16
                val black = wanted < 128
                val error = wanted - if (black) 0 else 255
                if (black) out[dest + (x shr 3)] = (out[dest + (x shr 3)].toInt() or (0x80 shr (x and 7))).toByte()
                if (error != 0) {
                    carry[x + 2] += error * 7
                    next[x] += error * 3
                    next[x + 1] += error * 5
                    next[x + 2] += error
                }
            }
            val t = carry; carry = next; next = t
        }
    }
}
