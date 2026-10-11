package crystaldigits.freeprint.otg.scan

import androidx.annotation.StringRes
import crystaldigits.freeprint.otg.R
import org.w3c.dom.Element
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.min

/**
 * What the scanner says it can do, from eSCL `GET /eSCL/ScannerCapabilities`. Sizes are in 1/300 inch, the eSCL default
 * unit. Only the flatbed ("Platen") is read; the G2020 has no document feeder.
 */
data class EsclCapabilities(
    val model: String,
    val maxWidth: Int,
    val maxHeight: Int,
    val resolutions: List<Int>,
    val colorModes: List<String>,
    val formats: List<String>,
    val hasFeeder: Boolean,
) {
    val supportsColor get() = "RGB24" in colorModes
    val supportsGray get() = "Grayscale8" in colorModes
    val supportsJpeg get() = formats.any { it.equals("image/jpeg", ignoreCase = true) }

    companion object {
        private val FALLBACK_RESOLUTIONS = listOf(75, 150, 300, 600)

        /** Null if [xml] is not a scanner capabilities document. */
        fun parse(xml: String): EsclCapabilities? = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // A scanner has no business sending a DOCTYPE; refuse it where the parser lets us.
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            }
            val root = factory.newDocumentBuilder().parse(org.xml.sax.InputSource(StringReader(xml))).documentElement
            if (root.localName != "ScannerCapabilities") return null

            val platen = root.first("PlatenInputCaps") ?: root.first("Platen") ?: return null
            val resolutions = platen.all("DiscreteResolution")
                .mapNotNull { it.first("XResolution")?.textContent?.trim()?.toIntOrNull() }
                .distinct().sorted()
            EsclCapabilities(
                model = root.first("MakeAndModel")?.textContent?.trim().orEmpty(),
                maxWidth = platen.first("MaxWidth")?.textContent?.trim()?.toIntOrNull() ?: 2550,
                maxHeight = platen.first("MaxHeight")?.textContent?.trim()?.toIntOrNull() ?: 3508,
                resolutions = resolutions.ifEmpty { FALLBACK_RESOLUTIONS },
                colorModes = platen.all("ColorMode").map { it.textContent.trim() }.distinct(),
                formats = (platen.all("DocumentFormat") + platen.all("DocumentFormatExt")).map { it.textContent.trim() }.distinct(),
                hasFeeder = root.first("Adf") != null,
            )
        }.getOrNull()

        private fun Element.first(local: String): Element? = all(local).firstOrNull()
        private fun Element.all(local: String): List<Element> =
            getElementsByTagNameNS("*", local).let { n -> (0 until n.length).map { n.item(it) as Element } }
    }
}

/** The part of the glass to scan, as a width and height in 1/300 inch, anchored at the top-left corner. */
enum class ScanArea(@StringRes val labelRes: Int, val width: Int, val height: Int) {
    FULL(R.string.scan_area_full, Int.MAX_VALUE, Int.MAX_VALUE),   // clamped to what the scanner reports
    A4(R.string.scan_area_a4, 2480, 3508),
    LETTER(R.string.scan_area_letter, 2550, 3300),
    A5(R.string.scan_area_a5, 1748, 2480),
    PHOTO_5X7(R.string.scan_area_5x7, 1500, 2100),
    PHOTO_4X6(R.string.scan_area_4x6, 1200, 1800),
}

data class ScanSettings(
    val area: ScanArea = ScanArea.A4,
    val dpi: Int = 300,
    val color: Boolean = true,
) {
    /** The area clamped to the glass, in 1/300 inch. */
    fun width(caps: EsclCapabilities) = min(area.width, caps.maxWidth)
    fun height(caps: EsclCapabilities) = min(area.height, caps.maxHeight)

    /** Expected size of the returned picture in pixels. */
    fun pixelWidth(caps: EsclCapabilities) = width(caps) * dpi / 300
    fun pixelHeight(caps: EsclCapabilities) = height(caps) * dpi / 300
}

object EsclXml {
    /** The body of `POST /eSCL/ScanJobs` for one flatbed page, returned as JPEG. */
    fun scanSettings(s: ScanSettings, caps: EsclCapabilities): String {
        val mode = if (s.color) "RGB24" else "Grayscale8"
        return """<?xml version="1.0" encoding="UTF-8"?>
<scan:ScanSettings xmlns:scan="http://schemas.hp.com/imaging/escl/2011/05/03" xmlns:pwg="http://www.pwg.org/schemas/2010/12/sm">
<pwg:Version>2.0</pwg:Version>
<pwg:ScanRegions><pwg:ScanRegion>
<pwg:ContentRegionUnits>escl:ThreeHundredthsOfInches</pwg:ContentRegionUnits>
<pwg:XOffset>0</pwg:XOffset><pwg:YOffset>0</pwg:YOffset>
<pwg:Width>${s.width(caps)}</pwg:Width><pwg:Height>${s.height(caps)}</pwg:Height>
</pwg:ScanRegion></pwg:ScanRegions>
<scan:InputSource>Platen</scan:InputSource>
<scan:ColorMode>$mode</scan:ColorMode>
<scan:XResolution>${s.dpi}</scan:XResolution>
<scan:YResolution>${s.dpi}</scan:YResolution>
<pwg:DocumentFormat>image/jpeg</pwg:DocumentFormat>
</scan:ScanSettings>
"""
    }

    /** The scanner's state from `GET /eSCL/ScannerStatus` ("Idle", "Processing", "Stopped"...), or null. */
    fun scannerState(xml: String): String? = Regex("<(?:\\w+:)?State>\\s*([^<\\s]+)\\s*</").find(xml)?.groupValues?.get(1)

    /** Path part of a job `Location` header, which may be absolute ("http://localhost/eSCL/ScanJobs/3") or already a path. */
    fun jobPath(location: String): String =
        location.trim().let { l -> if (l.startsWith("/")) l else l.substringAfter("://", l).let { "/" + it.substringAfter('/', "") } }.trimEnd('/')
}
