package crescentric.freeprint.otg.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLayoutTest {
    private val a4w = 4960
    private val a4h = 7016
    private val eps = 0.01f

    private fun place(w: Float, h: Float, o: PrintOptions = PrintOptions(), aw: Int = a4w, ah: Int = a4h, autoRotate: Boolean = true, upi: Float = 72f) =
        PageLayout.place(w, h, aw, ah, o, 600f, upi, autoRotate)

    @Test fun a4PdfFitsExactly() {
        val p = place(595f, 842f)
        assertEquals(a4w / 595f, p.scale.coerceAtLeast(0f), 0.5f)
        assertTrue(p.tx >= -1 && p.ty >= -1)
    }

    @Test fun fitKeepsAspectAndCentres() {
        val p = place(100f, 100f, aw = 1000, ah = 2000)          // square into tall area: width limits
        assertEquals(10f, p.scale, eps)
        assertEquals(0f, p.tx, eps)
        assertEquals(500f, p.ty, eps)                             // (2000 - 1000) / 2
    }

    @Test fun fillCoversAndCropsCentred() {
        val p = place(100f, 100f, PrintOptions(fit = FitMode.FILL), aw = 1000, ah = 2000)
        assertEquals(20f, p.scale, eps)
        assertEquals(-500f, p.tx, eps)                            // overflow shared left and right
        assertEquals(0f, p.ty, eps)
    }

    @Test fun actualSizeUsesPhysicalSize() {
        // 72 points = 1 inch = 600 px at 600 dpi.
        val p = place(72f, 72f, PrintOptions(fit = FitMode.ACTUAL))
        assertEquals(600f / 72f, p.scale, eps)
    }

    @Test fun actualSizeImageAt300Dpi() {
        val p = place(300f, 300f, PrintOptions(fit = FitMode.ACTUAL), upi = 300f)
        assertEquals(2f, p.scale, eps)                            // 300 px at 300/in -> 1 in -> 600 px
    }

    @Test fun scalePercentMultipliesFit() {
        val full = place(100f, 100f, aw = 1000, ah = 1000).scale
        val half = place(100f, 100f, PrintOptions(scalePercent = 50), aw = 1000, ah = 1000)
        assertEquals(full / 2, half.scale, eps)
        assertEquals(250f, half.tx, eps)                          // still centred
        assertEquals(250f, half.ty, eps)
    }

    @Test fun scalePercentAbove100Overflows() {
        val p = place(100f, 100f, PrintOptions(scalePercent = 200), aw = 1000, ah = 1000)
        assertEquals(20f, p.scale, eps)
        assertEquals(-500f, p.tx, eps)
    }

    @Test fun scalePercentAppliesToActualSize() {
        val p = place(72f, 72f, PrintOptions(fit = FitMode.ACTUAL, scalePercent = 50))
        assertEquals(300f / 72f, p.scale, eps)
    }

    @Test fun anchorTopLeftIgnoresSpareRoom() {
        val p = place(100f, 100f, PrintOptions(anchor = Anchor.TOP_LEFT, scalePercent = 50), aw = 1000, ah = 1000)
        assertEquals(0f, p.tx, eps)
        assertEquals(0f, p.ty, eps)
    }

    @Test fun anchorBottomRightPushesToCorner() {
        val p = place(100f, 100f, PrintOptions(anchor = Anchor.BOTTOM_RIGHT, scalePercent = 50), aw = 1000, ah = 1000)
        assertEquals(500f, p.tx, eps)                             // 1000 - 500
        assertEquals(500f, p.ty, eps)
    }

    @Test fun offsetsAreMillimetres() {
        val mm = 600f / 25.4f
        val p = place(100f, 100f, PrintOptions(anchor = Anchor.TOP_LEFT, offsetXmm = 10, offsetYmm = -5), aw = 1000, ah = 1000)
        assertEquals(10 * mm, p.tx, eps)
        assertEquals(-5 * mm, p.ty, eps)
    }

    @Test fun landscapePageRotatesIntoPortraitArea() {
        val p = place(842f, 595f)
        assertTrue(p.rotate)
        assertEquals(a4w / 595f, p.scale, 0.05f)                  // rotated: its 595 side spans the width
    }

    @Test fun autoRotateCanBeDisabled() {
        assertFalse(place(842f, 595f, autoRotate = false).rotate)
    }

    @Test fun squarePageNeverRotates() {
        assertFalse(place(100f, 100f).rotate)
    }
}
