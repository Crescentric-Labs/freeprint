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
import crystaldigits.freeprint.otg.UsbWatcher
import android.graphics.Bitmap
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One scanned page kept on disk as the scanner's own JPEG. */
class ScanPage(val file: File, val widthPx: Int, val heightPx: Int, val gray: Boolean, val dpi: Int)

/** A decoded preview of one scanned page, with the page number it shows so the UI can animate in the right direction. */
class ScanPreviewImage(val index: Int, val bitmap: Bitmap)

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

    /** The page shown in the preview, as an index into [pages]. */
    var pageIndex by mutableIntStateOf(0); private set
    var preview by mutableStateOf<ScanPreviewImage?>(null); private set
    /** Larger picture for the full-screen viewer; null while it is closed. */
    var largePreview by mutableStateOf<Bitmap?>(null); private set
    private var largeOpen = false
    private var previewJob: Job? = null

    /** Follows the printer being plugged in or out, so the scanner card is always current. */
    private val usbWatcher = UsbWatcher(app) { refresh() }

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
            result.onSuccess { pages = pages + it; pageIndex = pages.lastIndex; renderPreview() }.onFailure { message = messageFor(it) }
        }
    }

    fun selectPage(index: Int) {
        pageIndex = index.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        renderPreview()
    }

    fun removePage(index: Int) {
        pages.getOrNull(index)?.file?.delete()
        pages = pages.filterIndexed { i, _ -> i != index }
        saved = false
        selectPage(if (index <= pageIndex) pageIndex - 1 else pageIndex)
    }

    /** Moves a page earlier (-1) or later (+1); the preview follows it. */
    fun movePage(index: Int, delta: Int) {
        val to = index + delta
        if (index !in pages.indices || to !in pages.indices) return
        pages = pages.toMutableList().apply { add(to, removeAt(index)) }
        selectPage(to)
    }

    fun clearPages() {
        pages.forEach { it.file.delete() }
        pages = emptyList()
        saved = false
        pageIndex = 0
        preview = null
        largePreview = null
    }

    /** Opens or closes the full-screen viewer; while it is open the larger picture is kept up to date. */
    fun setLargePreviewOpen(open: Boolean) {
        largeOpen = open
        if (!open) largePreview = null
        renderPreview()
    }

    /** Decodes the current page for the preview (or the viewer), at the size that is actually shown. */
    private fun renderPreview() {
        val page = pages.getOrNull(pageIndex)
        if (page == null) { preview = null; largePreview = null; return }
        val index = pageIndex
        val large = largeOpen
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.IO) {
            val bmp = decode(page.file, if (large) LARGE_SIDE else PREVIEW_SIDE) ?: return@launch
            if (large) largePreview = bmp else preview = ScanPreviewImage(index, bmp)
        }
    }

    /** A JPEG decoded no larger than needed: a 600 dpi A4 colour page would otherwise be 140 MB. */
    private fun decode(file: File, longestSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > longestSide) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun dismissMessage() { message = null }

    override fun onCleared() { usbWatcher.stop() }

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

    private companion object {
        const val PREVIEW_SIDE = 1100
        const val LARGE_SIDE = 2200
    }

    /** Keeps the chosen settings inside what this scanner offers (resolution list, colour modes). */
    private fun fitToCapabilities(s: ScanSettings, c: EsclCapabilities): ScanSettings {
        val dpi = if (s.dpi in c.resolutions) s.dpi else c.resolutions.minByOrNull { kotlin.math.abs(it - 300) } ?: 300
        val color = if (s.color && c.supportsColor) true else if (!s.color && c.supportsGray) false else c.supportsColor
        return s.copy(dpi = dpi, color = color)
    }
}
