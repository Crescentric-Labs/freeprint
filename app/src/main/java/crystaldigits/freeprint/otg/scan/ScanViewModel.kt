package crystaldigits.freeprint.otg.scan

import android.app.Application
import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import crystaldigits.freeprint.otg.HttpSession
import crystaldigits.freeprint.otg.R
import crystaldigits.freeprint.otg.UsbPrinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One scanned page kept on disk as the scanner's own JPEG. */
class ScanPage(val file: File, val widthPx: Int, val heightPx: Int, val gray: Boolean, val dpi: Int)

enum class ScannerStatus { NO_PRINTER, NEEDS_PERMISSION, LOADING, READY, ERROR }

/** State and actions for the scan screen. USB work runs on Dispatchers.IO, one operation at a time. */
class ScanViewModel(app: Application) : AndroidViewModel(app) {
    private val usb = UsbPrinter(app)
    private var device: UsbDevice? = null
    private val dir = File(app.cacheDir, "scans")

    var status by mutableStateOf(ScannerStatus.NO_PRINTER); private set
    var caps by mutableStateOf<EsclCapabilities?>(null); private set
    var settings by mutableStateOf(ScanSettings()); private set
    var pages by mutableStateOf<List<ScanPage>>(emptyList()); private set
    var scanning by mutableStateOf(false); private set
    var message by mutableStateOf<ScanMessage?>(null); private set
    var saved by mutableStateOf(false); private set

    init {
        // Pages from an earlier run are no longer on screen, so they would only take up space.
        dir.deleteRecursively()
    }

    /** Looks for the printer again and, if it is usable and the scanner is not yet known, asks what it can do. */
    fun refresh() {
        device = usb.findPrinter()
        val d = device
        when {
            d == null -> { status = ScannerStatus.NO_PRINTER; caps = null }
            !usb.hasPermission(d) -> status = ScannerStatus.NEEDS_PERMISSION
            caps == null && status != ScannerStatus.LOADING -> loadCapabilities()
            caps != null -> status = ScannerStatus.READY
        }
    }

    fun requestPermission() {
        val d = device ?: return
        usb.requestPermission(d) { refresh() }
    }

    fun loadCapabilities() {
        if (status == ScannerStatus.LOADING || scanning) return
        status = ScannerStatus.LOADING
        message = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { withClient { it.capabilities() } } }
            result.onSuccess { c ->
                caps = c
                settings = fitToCapabilities(settings, c)
                status = ScannerStatus.READY
            }.onFailure { e ->
                status = ScannerStatus.ERROR
                message = messageFor(e)
            }
        }
    }

    fun updateSettings(s: ScanSettings) { settings = s }

    /** Scans one page and adds it after the existing ones. */
    fun scan() {
        val c = caps ?: return
        if (scanning) return
        val s = settings
        scanning = true
        message = null
        saved = false
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val jpeg = withClient { it.scanPage(s, c) }
                    dir.mkdirs()
                    val file = File(dir, "scan-${System.currentTimeMillis()}.jpg").also { it.writeBytes(jpeg) }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, bounds)
                    ScanPage(file, bounds.outWidth.coerceAtLeast(1), bounds.outHeight.coerceAtLeast(1), gray = !s.color, dpi = s.dpi)
                }
            }
            scanning = false
            result.onSuccess { pages = pages + it }.onFailure { message = messageFor(it) }
        }
    }

    fun removePage(index: Int) {
        pages.getOrNull(index)?.file?.delete()
        pages = pages.filterIndexed { i, _ -> i != index }
        saved = false
    }

    fun movePage(index: Int, delta: Int) {
        val to = index + delta
        if (index !in pages.indices || to !in pages.indices) return
        pages = pages.toMutableList().apply { add(to, removeAt(index)) }
    }

    fun clearPages() {
        pages.forEach { it.file.delete() }
        pages = emptyList()
        saved = false
    }

    fun dismissMessage() { message = null }

    // ---- output ----

    /** A PDF of all pages, one per page, written to the cache; returns it. Call off the main thread. */
    fun buildPdf(): File {
        dir.mkdirs()
        val file = File(dir, "scan-${System.currentTimeMillis()}.pdf")
        file.outputStream().use { out ->
            JpegPdf.write(pages.map { PdfPageImage(it.file.readBytes(), it.widthPx, it.heightPx, it.gray, it.dpi) }, out)
        }
        return file
    }

    /** Copies a file into the place the user picked with the system file dialog. */
    fun copyTo(source: File, target: Uri, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(target)!!.use { out -> source.inputStream().use { it.copyTo(out) } }
                }.isSuccess
            }
            saved = ok
            if (!ok) message = ScanMessage(R.string.scan_err_save)
            done(ok)
        }
    }

    /** Builds the PDF in the background and hands it to [done] on the main thread. */
    fun withPdf(done: (File) -> Unit) {
        if (pages.isEmpty()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { buildPdf() } }
                .onSuccess(done)
                .onFailure { message = ScanMessage(R.string.scan_err_save, detail = it.message.orEmpty()) }
        }
    }

    // ---- plumbing ----

    /**
     * Opens the printer, tries each of its two IPP-over-USB interfaces until one claims, and runs [block] on an eSCL
     * client. Throws [ScanException] with a user-facing message when the printer cannot be used.
     */
    private fun <T> withClient(block: (EsclClient) -> T): T {
        val dev = usb.findPrinter() ?: throw ScanException(ScanMessage(R.string.scan_err_no_printer))
        if (!usb.hasPermission(dev)) throw ScanException(ScanMessage(R.string.scan_err_no_permission))
        val conn = usb.open(dev) ?: throw ScanException(ScanMessage(R.string.scan_err_cannot_open))
        try {
            var why = ""
            for (which in 0..1) {
                HttpSession(conn.fileDescriptor, which).use { http ->
                    if (http.isOpen) return block(EsclClient(http))
                    why = http.openError.trim()
                }
            }
            throw ScanException(ScanMessage(R.string.scan_err_cannot_open, detail = why))
        } finally {
            conn.close()
        }
    }

    private fun messageFor(e: Throwable): ScanMessage =
        (e as? ScanException)?.msg ?: ScanMessage(R.string.scan_err_transfer, detail = e.message.orEmpty())

    /** Keeps the chosen settings inside what this scanner offers (resolution list, colour modes). */
    private fun fitToCapabilities(s: ScanSettings, c: EsclCapabilities): ScanSettings {
        val dpi = if (s.dpi in c.resolutions) s.dpi else c.resolutions.minByOrNull { kotlin.math.abs(it - 300) } ?: 300
        val color = if (s.color && c.supportsColor) true else if (!s.color && c.supportsGray) false else c.supportsColor
        return s.copy(dpi = dpi, color = color)
    }
}
