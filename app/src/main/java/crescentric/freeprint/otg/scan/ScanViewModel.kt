package crescentric.freeprint.otg.scan

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.provider.DocumentsContract
import crescentric.freeprint.otg.print.CompositeSource
import crescentric.freeprint.otg.print.DocEnhance
import crescentric.freeprint.otg.print.FileSpec
import crescentric.freeprint.otg.print.PRINT_DPI
import crescentric.freeprint.otg.print.PrintOptions
import crescentric.freeprint.otg.print.SheetRender
import java.io.ByteArrayOutputStream
import android.hardware.usb.UsbDevice
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import crescentric.freeprint.otg.HttpSession
import crescentric.freeprint.otg.R
import crescentric.freeprint.otg.UsbPrinter
import crescentric.freeprint.otg.UsbWatcher
import android.graphics.Bitmap
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One scanned page kept on disk as the scanner's own JPEG. */
class ScanPage(val file: File, val widthPx: Int, val heightPx: Int, val gray: Boolean, val dpi: Int, val enhance: Boolean = false) {
    /** The same page with "optimize for print" switched on or off; the scanner's JPEG on disk is never altered. */
    fun withEnhance(on: Boolean) = ScanPage(file, widthPx, heightPx, gray, dpi, on)
}

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

    /** Pages deleted with the strip or the delete button, kept on disk so they can be restored until the pages are cleared. */
    private val trash = PageTrash<ScanPage>()
    var deletedCount by mutableIntStateOf(0); private set

    /** Bumped when the user moves to another page or a page is added, so the strip centres it; not bumped when one is deleted. */
    var pageScrollTick by mutableIntStateOf(0); private set

    /** The page shown in the preview, as an index into [pages]. */
    var pageIndex by mutableIntStateOf(0); private set
    var preview by mutableStateOf<ScanPreviewImage?>(null); private set
    /** Larger picture for the full-screen viewer; null while it is closed. */
    var largeImage by mutableStateOf<ScanPreviewImage?>(null); private set
    val largePreview: Bitmap? get() = largeImage?.bitmap
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
            result.onSuccess { pages = pages + it; pageIndex = pages.lastIndex; pageScrollTick++; renderPreview() }.onFailure { message = messageFor(it) }
        }
    }

    /** Turns "optimize for print" on or off for the page at [index]; only that page's picture is drawn again. */
    fun toggleEnhance(index: Int) {
        val p = pages.getOrNull(index) ?: return
        pages = pages.mapIndexed { i, d -> if (i == index) p.withEnhance(!p.enhance) else d }
        saved = false
        if (index == pageIndex) renderPreview()
    }

    /** The user moves to another page: show it and centre it in the strip. */
    fun selectPage(index: Int) {
        pageScrollTick++
        showPage(index)
    }

    private fun showPage(index: Int) {
        pageIndex = index.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        renderPreview()
    }

    /** Takes a page out of the list. Its file is kept, so [restorePages] can bring it back, until [clearPages]. */
    fun removePage(index: Int) {
        val page = pages.getOrNull(index) ?: return
        trash.add(index, page)
        deletedCount = trash.size
        pages = pages.filterIndexed { i, _ -> i != index }
        saved = false
        showPage(if (index <= pageIndex) pageIndex - 1 else pageIndex)
    }

    /** Puts every deleted page back where it was. */
    fun restorePages() {
        if (trash.isEmpty) return
        pages = trash.restoreInto(pages)
        trash.clear()
        deletedCount = 0
        saved = false
        showPage(pageIndex)
    }

    /** Moves a page earlier (-1) or later (+1); the preview follows it. */
    fun movePage(index: Int, delta: Int) {
        val to = index + delta
        if (index !in pages.indices || to !in pages.indices) return
        pages = pages.toMutableList().apply { add(to, removeAt(index)) }
        showPage(to)
    }

    fun clearPages() {
        (pages + trash.all()).forEach { it.file.delete() }
        trash.clear()
        deletedCount = 0
        pages = emptyList()
        saved = false
        pageIndex = 0
        preview = null
        largeImage = null
    }

    /** Opens or closes the full-screen viewer; while it is open the larger picture is kept up to date. */
    fun setLargePreviewOpen(open: Boolean) {
        largeOpen = open
        if (!open) largeImage = null
        renderPreview()
    }

    /** Decodes the current page for the preview (or the viewer), at the size that is actually shown. */
    private fun renderPreview() {
        val page = pages.getOrNull(pageIndex)
        if (page == null) { preview = null; largeImage = null; return }
        val index = pageIndex
        val large = largeOpen
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.IO) {
            val bmp = decode(page.file, if (large) LARGE_SIDE else PREVIEW_SIDE, page.enhance) ?: return@launch
            if (large) largeImage = ScanPreviewImage(index, bmp) else preview = ScanPreviewImage(index, bmp)
        }
    }

    /** A JPEG decoded no larger than needed: a 600 dpi A4 colour page would otherwise be 140 MB. */
    private fun decode(file: File, longestSide: Int, enhance: Boolean = false): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > longestSide) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample; inMutable = enhance })
            ?.also { if (enhance) DocEnhance.apply(it) }
    }

    fun dismissMessage() { message = null }

    override fun onCleared() { usbWatcher.stop() }

    // ---- output ----

    // ---- what to save: pages as scanned, or laid out on paper sheets ----

    /** When on, saving puts the pages on sheets of paper using [saveOptions] (pages per sheet, fit, margins...); when off, each page is saved as scanned. */
    var saveLayout by mutableStateOf(false); private set
    /** The layout settings used when [saveLayout] is on. Defaults to A4, one page per sheet, fitted, no margin. */
    var saveOptions by mutableStateOf(PrintOptions()); private set

    fun useSaveLayout(on: Boolean) { saveLayout = on }
    fun updateSaveOptions(o: PrintOptions) { saveOptions = o }

    /** How many pictures saving would produce: one per page as scanned, or one per sheet when laid out. */
    val outputCount: Int
        get() = if (!saveLayout) pages.size else ((pages.size + saveOptions.perSheet.n - 1) / saveOptions.perSheet.n).coerceAtLeast(1)

    /**
     * Every picture the output is made of, as JPEGs. As scanned, a page the user did not optimize is the scanner's own JPEG, byte for
     * byte; an optimized one is decoded, enhanced and encoded again. Laid out, each sheet is drawn at 300 dpi by the same code the
     * print preview and print job use. Call off the main thread.
     */
    private fun outputImages(laidOut: Boolean): List<PdfPageImage> {
        if (!laidOut) return pages.map { p ->
            if (!p.enhance) PdfPageImage(p.file.readBytes(), p.widthPx, p.heightPx, p.gray, p.dpi)
            else {
                val bmp = decodeFull(p.file).also { DocEnhance.apply(it) }
                try { PdfPageImage(encode(bmp), bmp.width, bmp.height, false, p.dpi) } finally { bmp.recycle() }
            }
        }
        val o = saveOptions
        val ctx = getApplication<Application>()
        val specs = pages.map { FileSpec(Uri.fromFile(it.file), "image/jpeg", if (it.enhance) setOf(0) else emptySet()) }
        return CompositeSource.open(ctx, specs).use { src ->
            val width = (o.paper.widthPx * SAVE_SCALE).toInt()
            val sheets = ((pages.size + o.perSheet.n - 1) / o.perSheet.n).coerceAtLeast(1)
            (0 until sheets).map { i ->
                val sheet = SheetRender.compose(src, o, i, width)
                if (!o.color) toGray(sheet)
                try { PdfPageImage(encode(sheet), sheet.width, sheet.height, false, (PRINT_DPI * SAVE_SCALE).toInt()) } finally { sheet.recycle() }
            }
        }
    }

    private fun toGray(bmp: Bitmap) {
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }
        val copy = bmp.copy(Bitmap.Config.ARGB_8888, false)
        Canvas(bmp).drawBitmap(copy, 0f, 0f, paint)
        copy.recycle()
    }

    private fun encode(bmp: Bitmap): ByteArray = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()

    /** A page decoded for editing, no larger than [FULL_SIDE] along its longer side. */
    private fun decodeFull(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > FULL_SIDE) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true })!!
    }

    /**
     * A PDF of the pages, written to the cache; returns it. [withLayout] follows the save settings; pass false for a plain
     * one-page-per-scan PDF (used when printing a copy, since the print screen has its own layout settings). Call off the main thread.
     */
    fun buildPdf(withLayout: Boolean = saveLayout): File {
        dir.mkdirs()
        val file = File(dir, "scan-${System.currentTimeMillis()}.pdf")
        val images = outputImages(withLayout)
        file.outputStream().use { out -> JpegPdf.write(images, out) }
        return file
    }

    /** Writes the pictures as JPEG files: into the single file [target], or, for several, into the folder [target] (a document tree). */
    fun saveImages(target: Uri, folder: Boolean, done: (Boolean) -> Unit = {}) {
        if (pages.isEmpty()) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    val images = outputImages(saveLayout)
                    if (!folder) {
                        resolver.openOutputStream(target)!!.use { it.write(images.first().jpeg) }
                    } else {
                        val parent = DocumentsContract.buildDocumentUriUsingTree(target, DocumentsContract.getTreeDocumentId(target))
                        val stamp = System.currentTimeMillis() / 1000
                        images.forEachIndexed { i, img ->
                            val doc = DocumentsContract.createDocument(resolver, parent, "image/jpeg", "scan-$stamp-${i + 1}.jpg")!!
                            resolver.openOutputStream(doc)!!.use { it.write(img.jpeg) }
                        }
                    }
                }.isSuccess
            }
            saved = ok
            if (!ok) message = ScanMessage(R.string.scan_err_save)
            done(ok)
        }
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
    fun withPdf(withLayout: Boolean = saveLayout, done: (File) -> Unit) {
        if (pages.isEmpty()) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { buildPdf(withLayout) } }
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
        const val FULL_SIDE = 5000          // longest side when a page is edited for saving
        const val SAVE_SCALE = 0.5f         // laid-out sheets are drawn at 300 dpi (the print raster is 600)
    }

    /** Keeps the chosen settings inside what this scanner offers (resolution list, colour modes). */
    private fun fitToCapabilities(s: ScanSettings, c: EsclCapabilities): ScanSettings {
        val dpi = if (s.dpi in c.resolutions) s.dpi else c.resolutions.minByOrNull { kotlin.math.abs(it - 300) } ?: 300
        val color = if (s.color && c.supportsColor) true else if (!s.color && c.supportsGray) false else c.supportsColor
        return s.copy(dpi = dpi, color = color)
    }
}
