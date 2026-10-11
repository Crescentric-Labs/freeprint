package crystaldigits.freeprint.otg.scan

import androidx.annotation.StringRes
import crystaldigits.freeprint.otg.HttpSession
import crystaldigits.freeprint.otg.R

/** A scan problem shown to the user: a translated headline plus optional technical detail (English). */
class ScanMessage(@StringRes val res: Int, val args: List<Any> = emptyList(), val detail: String = "")

/** Thrown for failures that already have a user-facing message. */
class ScanException(val msg: ScanMessage) : Exception(msg.detail.ifEmpty { "scan error ${msg.res}" })

/**
 * Talks eSCL (AirScan) to the printer's scanner over an open [HttpSession]. Blocking; one scan at a time.
 *
 * A scan is: POST the settings to /eSCL/ScanJobs (the reply's Location names the job), GET <job>/NextDocument (which
 * waits while the glass is scanned and then returns the JPEG), then DELETE the job to tidy up.
 */
class EsclClient(private val http: HttpSession) {

    fun capabilities(): EsclCapabilities {
        val r = http.request("GET", "/eSCL/ScannerCapabilities", timeoutMs = 10_000) ?: throw transport()
        if (r.status != 200) throw ScanException(ScanMessage(R.string.scan_err_not_scanner, detail = "HTTP ${r.status}"))
        return EsclCapabilities.parse(r.text) ?: throw ScanException(ScanMessage(R.string.scan_err_not_scanner, detail = "unreadable capabilities"))
    }

    /** Scans one flatbed page and returns the JPEG exactly as the scanner sent it. */
    fun scanPage(settings: ScanSettings, caps: EsclCapabilities): ByteArray {
        val create = http.request(
            "POST", "/eSCL/ScanJobs", "text/xml", EsclXml.scanSettings(settings, caps).toByteArray(), timeoutMs = 15_000,
        ) ?: throw transport()
        when (create.status) {
            201 -> {}
            503, 409 -> throw ScanException(ScanMessage(R.string.scan_err_busy, detail = "HTTP ${create.status}"))
            else -> throw ScanException(ScanMessage(R.string.scan_err_rejected, listOf(create.status), create.text.take(300)))
        }
        val job = EsclXml.jobPath(create.header("Location") ?: throw ScanException(ScanMessage(R.string.scan_err_rejected, listOf(201), "no job location")))

        try {
            // The scanner holds this request open while the glass is scanned, so each USB read waits a long time.
            val doc = http.request("GET", "$job/NextDocument", timeoutMs = SCAN_WAIT_MS) ?: throw transport()
            when (doc.status) {
                200 -> {}
                503 -> throw ScanException(ScanMessage(R.string.scan_err_busy, detail = "HTTP 503"))
                404 -> throw ScanException(ScanMessage(R.string.scan_err_nodata, detail = "HTTP 404"))
                else -> throw ScanException(ScanMessage(R.string.scan_err_rejected, listOf(doc.status), doc.text.take(300)))
            }
            if (!isJpeg(doc.body)) throw ScanException(ScanMessage(R.string.scan_err_bad_image, detail = "${doc.body.size} bytes, ${doc.header("Content-Type")}"))
            return doc.body
        } finally {
            runCatching { http.request("DELETE", job, timeoutMs = 8_000) }      // best effort: a finished job may already be gone
        }
    }

    private fun transport() = ScanException(ScanMessage(R.string.scan_err_transfer, detail = http.lastError.trim()))

    companion object {
        private const val SCAN_WAIT_MS = 150_000          // a 600 dpi colour page can take a minute or more
        fun isJpeg(b: ByteArray) = b.size > 4 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()
    }
}
