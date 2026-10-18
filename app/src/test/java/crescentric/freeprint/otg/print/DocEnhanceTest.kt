package crescentric.freeprint.otg.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocEnhanceTest {
    private fun rgb(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun grey(argb: Int) = argb and 0xFF

    /** A 200 x 100 page whose lighting fades from 150 on the left to 210 on the right, with ink 90 levels darker in a band. */
    private fun page(): IntArray {
        val w = 200; val h = 100
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val paper = 150 + 60 * x / (w - 1)
            val ink = y in 45..47 && x in 20..180
            rgb(if (ink) paper - 90 else paper)
        }
    }

    private fun enhanced(): IntArray {
        val w = 200; val h = 100
        val px = page()
        val builder = DocEnhance.Background.Builder(w, h)
        builder.addRows(px, 0, h)
        val paper = builder.build()
        return IntArray(w * h) { i -> DocEnhance.pixel(px[i], paper.at(((i % w) + 0.5f) / w, ((i / w) + 0.5f) / h)) }
    }

    @Test fun uneven_lighting_becomes_even_white_paper() {
        val out = enhanced()
        // away from the ink, dim and bright ends of the page both come out (nearly) white
        for (x in listOf(5, 60, 120, 195)) assertTrue("x=$x ${grey(out[10 * 200 + x])}", grey(out[10 * 200 + x]) >= 245)
    }

    @Test fun ink_stays_dark() {
        val out = enhanced()
        for (x in listOf(40, 100, 160)) assertTrue("x=$x ${grey(out[46 * 200 + x])}", grey(out[46 * 200 + x]) <= 140)
    }

    @Test fun alpha_is_kept_and_levels_clip() {
        assertEquals(0xFF, DocEnhance.pixel(rgb(10), rgb(200)) ushr 24)
        assertEquals(0, grey(DocEnhance.pixel(rgb(10), rgb(200))))
        assertEquals(255, grey(DocEnhance.pixel(rgb(200), rgb(200))))
    }

    @Test fun dark_surroundings_are_not_blown_up() {
        // a near-black area has a near-black paper estimate; it must not be amplified into bright noise
        assertTrue(grey(DocEnhance.pixel(rgb(20), rgb(25))) < 20)
    }
}
