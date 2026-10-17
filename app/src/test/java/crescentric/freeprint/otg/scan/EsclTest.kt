package crescentric.freeprint.otg.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EsclTest {
    /**
     * Modelled on the Canon G2020's real response (namespaces, element names, the 2550 x 3508 platen, Grayscale8 and RGB24,
     * JPEG and PDF). The list of discrete resolutions was cut off in the capture, so it is typical eSCL rather than verified.
     */
    private val capabilities = """<?xml version="1.0" encoding="UTF-8"?><scan:ScannerCapabilities
 xmlns:scan="http://schemas.hp.com/imaging/escl/2011/05/03" xmlns:pwg="http://www.pwg.org/schemas/2010/12/sm"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><pwg:Version>2.63</pwg:Version>
<pwg:MakeAndModel>Canon G2020 series</pwg:MakeAndModel><scan:Manufacturer>Canon</scan:Manufacturer>
<scan:Platen><!-- FB start --><scan:PlatenInputCaps><scan:MinWidth>1</scan:MinWidth><scan:MaxWidth>2550</scan:MaxWidth>
<scan:MinHeight>1</scan:MinHeight><scan:MaxHeight>3508</scan:MaxHeight><scan:MaxScanRegions>1</scan:MaxScanRegions>
<scan:SettingProfiles><scan:SettingProfile><scan:ColorModes><scan:ColorMode>Grayscale8</scan:ColorMode>
<scan:ColorMode scan:default="true">RGB24</scan:ColorMode></scan:ColorModes>
<scan:ContentTypes><pwg:ContentType>Photo</pwg:ContentType><pwg:ContentType>Text</pwg:ContentType></scan:ContentTypes>
<scan:DocumentFormats><pwg:DocumentFormat>image/jpeg</pwg:DocumentFormat><pwg:DocumentFormat>application/pdf</pwg:DocumentFormat>
<scan:DocumentFormatExt scan:default="true">image/jpeg</scan:DocumentFormatExt></scan:DocumentFormats>
<scan:SupportedResolutions><scan:DiscreteResolutions>
<scan:DiscreteResolution><scan:XResolution>600</scan:XResolution><scan:YResolution>600</scan:YResolution></scan:DiscreteResolution>
<scan:DiscreteResolution><scan:XResolution>75</scan:XResolution><scan:YResolution>75</scan:YResolution></scan:DiscreteResolution>
<scan:DiscreteResolution><scan:XResolution>300</scan:XResolution><scan:YResolution>300</scan:YResolution></scan:DiscreteResolution>
<scan:DiscreteResolution><scan:XResolution>150</scan:XResolution><scan:YResolution>150</scan:YResolution></scan:DiscreteResolution>
</scan:DiscreteResolutions></scan:SupportedResolutions></scan:SettingProfile></scan:SettingProfiles>
</scan:PlatenInputCaps></scan:Platen></scan:ScannerCapabilities>"""

    private val caps = EsclCapabilities.parse(capabilities)!!

    @Test fun readsModelAndGlass() {
        assertEquals("Canon G2020 series", caps.model)
        assertEquals(2550, caps.maxWidth)
        assertEquals(3508, caps.maxHeight)
    }

    @Test fun resolutionsAreSortedAndUnique() = assertEquals(listOf(75, 150, 300, 600), caps.resolutions)

    @Test fun readsColourModesAndFormats() {
        assertTrue(caps.supportsColor && caps.supportsGray && caps.supportsJpeg)
        assertEquals(listOf("image/jpeg", "application/pdf"), caps.formats)
        assertFalse(caps.hasFeeder)
    }

    @Test fun missingResolutionsFallBackToCommonOnes() {
        val bare = capabilities.replace(Regex("<scan:SupportedResolutions>.*</scan:SupportedResolutions>", RegexOption.DOT_MATCHES_ALL), "")
        assertEquals(listOf(75, 150, 300, 600), EsclCapabilities.parse(bare)!!.resolutions)
    }

    @Test fun rejectsOtherDocuments() {
        assertNull(EsclCapabilities.parse("<html><body>404</body></html>"))
        assertNull(EsclCapabilities.parse("not xml at all"))
        assertNull(EsclCapabilities.parse(""))
    }

    @Test fun refusesADoctype() {
        // A scanner should never send one; the parser must not choke the app or fetch anything.
        val hostile = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]>$capabilities"""
        EsclCapabilities.parse(hostile)   // either null or parsed without resolving the entity; must not throw
    }

    @Test fun areaIsClampedToTheGlass() {
        val s = ScanSettings(area = ScanArea.FULL)
        assertEquals(2550, s.width(caps)); assertEquals(3508, s.height(caps))
        val a4 = ScanSettings(area = ScanArea.A4)
        assertEquals(2480, a4.width(caps)); assertEquals(3508, a4.height(caps))
    }

    @Test fun pixelSizeFollowsResolution() {
        val s = ScanSettings(area = ScanArea.A4, dpi = 600)
        assertEquals(4960, s.pixelWidth(caps)); assertEquals(7016, s.pixelHeight(caps))
        assertEquals(1240, ScanSettings(area = ScanArea.A4, dpi = 150).pixelWidth(caps))
    }

    @Test fun requestCarriesRegionModeAndResolution() {
        val xml = EsclXml.scanSettings(ScanSettings(ScanArea.A4, 300, color = true), caps)
        assertTrue(xml.contains("<pwg:Width>2480</pwg:Width>"))
        assertTrue(xml.contains("<pwg:Height>3508</pwg:Height>"))
        assertTrue(xml.contains("<scan:ColorMode>RGB24</scan:ColorMode>"))
        assertTrue(xml.contains("<scan:XResolution>300</scan:XResolution>"))
        assertTrue(xml.contains("<scan:InputSource>Platen</scan:InputSource>"))
        assertTrue(xml.contains("escl:ThreeHundredthsOfInches"))
        assertTrue(EsclXml.scanSettings(ScanSettings(color = false), caps).contains("Grayscale8"))
    }

    @Test fun requestIsWellFormedXml() {
        val xml = EsclXml.scanSettings(ScanSettings(), caps)
        javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(xml)))
    }

    @Test fun readsScannerState() {
        val status = """<scan:ScannerStatus xmlns:scan="http://schemas.hp.com/imaging/escl/2011/05/03" xmlns:pwg="http://www.pwg.org/schemas/2010/12/sm"><pwg:Version>2.63</pwg:Version><pwg:State>Idle</pwg:State></scan:ScannerStatus>"""
        assertEquals("Idle", EsclXml.scannerState(status))
        assertNull(EsclXml.scannerState("<x/>"))
    }

    @Test fun jobPathAcceptsAbsoluteAndRelativeLocations() {
        assertEquals("/eSCL/ScanJobs/7", EsclXml.jobPath("http://localhost/eSCL/ScanJobs/7"))
        assertEquals("/eSCL/ScanJobs/7", EsclXml.jobPath("/eSCL/ScanJobs/7"))
        assertEquals("/eSCL/ScanJobs/7", EsclXml.jobPath("http://192.168.1.5:80/eSCL/ScanJobs/7/"))
        assertNotNull(EsclXml.jobPath("http://localhost/eSCL/ScanJobs/7"))
    }
}
