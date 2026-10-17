package crescentric.freeprint.otg.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadableOnDarkTest {
    @Test fun aBrightAccentIsLeftAlone() {
        val bright = Color(0xFF8AB4F8)                       // a light blue, easy to see on black
        assertEquals(bright, readableOnDark(bright))
    }

    @Test fun aDarkAccentIsLightenedUntilItCanBeSeen() {
        val dark = Color(0xFF0B3D91)                         // a deep blue that vanishes on black
        val out = readableOnDark(dark)
        assertTrue(out.luminance() >= MIN_LUMINANCE_ON_DARK)
        assertTrue(out.luminance() > dark.luminance())
    }

    @Test fun lighteningKeepsTheHueRatherThanTurningWhite() {
        val out = readableOnDark(Color(0xFF0B3D91))
        assertTrue("still bluish", out.blue > out.red && out.blue > out.green)
        assertTrue("not plain white", out != Color.White)
    }

    @Test fun blackStillEndsUpVisible() {
        assertTrue(readableOnDark(Color.Black).luminance() > 0.1f)
    }
}
