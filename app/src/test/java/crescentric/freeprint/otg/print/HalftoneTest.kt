package crescentric.freeprint.otg.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HalftoneTest {
    private fun bits(out: ByteArray) = out.sumOf { Integer.bitCount(it.toInt() and 0xFF) }

    @Test fun whiteStaysWhiteAndBlackStaysBlack() {
        val w = 16
        val white = IntArray(w * 4) { 0xFFFFFFFF.toInt() }
        val black = IntArray(w * 4) { 0xFF000000.toInt() }
        val a = ByteArray(2 * 4); Halftone(w).convert(white, 4, a, 0, 2)
        val b = ByteArray(2 * 4); Halftone(w).convert(black, 4, b, 0, 2)
        assertEquals(0, bits(a))
        assertEquals(w * 4, bits(b))
    }

    @Test fun midGreyGivesAboutHalfTheDots() {
        val w = 64; val rows = 64
        val grey = IntArray(w * rows) { 0xFF808080.toInt() }
        val out = ByteArray(w / 8 * rows); Halftone(w).convert(grey, rows, out, 0, w / 8)
        val share = bits(out) / (w * rows).toDouble()
        assertTrue("share=$share", share in 0.45..0.55)
    }

    @Test fun stripsJoinWithoutASeam() {
        val w = 64
        val grey = IntArray(w * 32) { 0xFF808080.toInt() }
        val whole = ByteArray(w / 8 * 32); Halftone(w).convert(grey, 32, whole, 0, w / 8)
        val h = Halftone(w)
        val split = ByteArray(w / 8 * 32)
        h.convert(grey.copyOfRange(0, w * 16), 16, split, 0, w / 8)
        h.convert(grey.copyOfRange(w * 16, w * 32), 16, split, 16 * w / 8, w / 8)
        assertTrue(whole.contentEquals(split))
    }

    @Test fun darkTextBecomesBlackBitsInTheRightPlace() {
        val w = 16
        val px = IntArray(w * 1) { if (it == 3) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val out = ByteArray(2); Halftone(w).convert(px, 1, out, 0, 2)
        assertEquals(0x10, out[0].toInt() and 0xFF)
    }
}
