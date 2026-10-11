package crystaldigits.freeprint.otg

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import crystaldigits.freeprint.otg.print.JobExecutor
import crystaldigits.freeprint.otg.print.SheetComposer
import crystaldigits.freeprint.otg.print.PageSource
import crystaldigits.freeprint.otg.print.PageSelection
import crystaldigits.freeprint.otg.print.Parity
import crystaldigits.freeprint.otg.print.PrintOptions
import crystaldigits.freeprint.otg.print.SelectedPages

class Doc(val uri: Uri, val name: String, val mime: String?, val pageCount: Int)

sealed interface JobState {
    data object Idle : JobState
    data class Printing(val page: Int, val pages: Int) : JobState
    data class Done(val message: String) : JobState
    data class Failed(val message: String) : JobState
    data object Cancelled : JobState
}

/** State and actions for the print screen. USB and rendering work always runs on Dispatchers.IO. */
class PrintViewModel(app: Application) : AndroidViewModel(app) {
    private val usb = UsbPrinter(app)
    private var device: UsbDevice? = null

    var printerName by mutableStateOf<String?>(null); private set
    var hasPermission by mutableStateOf(false); private set

    var doc by mutableStateOf<Doc?>(null); private set
    var pageIndex by mutableIntStateOf(0); private set
    var options by mutableStateOf(PrintOptions()); private set
    var rangeText by mutableStateOf(""); private set
    var parity by mutableStateOf(Parity.ALL); private set
    var preview by mutableStateOf<Bitmap?>(null); private set
    var job by mutableStateOf<JobState>(JobState.Idle); private set
    var diagnostics by mutableStateOf(""); private set

    private var previewSource: PageSource? = null
    private val sourceLock = Mutex()          // PdfRenderer is not thread-safe
    private var previewJob: Job? = null

    /** The pages chosen by the range text and odd/even filter. */
    val selection: PageSelection get() = PageSelection.parse(rangeText, parity, doc?.pageCount ?: 0)
    val selectedCount get() = (selection as? PageSelection.Pages)?.indices?.size ?: 0

    /** Sheets the selected pages will use with the current pages-per-sheet setting. */
    val sheetCount get() = ((selectedCount + options.perSheet.n - 1) / options.perSheet.n).coerceAtLeast(1)

    val canPrint get() = doc != null && hasPermission && job !is JobState.Printing && selection is PageSelection.Pages

    fun refreshPrinter() {
        device = usb.findPrinter()
        printerName = device?.let { listOfNotNull(it.manufacturerName, it.productName).joinToString(" ").ifBlank { "USB printer" } }
        hasPermission = device?.let { usb.hasPermission(it) } ?: false
    }

    fun requestPermission() {
        val d = device ?: return
        usb.requestPermission(d) { refreshPrinter() }
    }

    fun updateOptions(o: PrintOptions) { options = o; pageIndex = pageIndex.coerceIn(0, sheetCount - 1); renderPreview() }
    fun setRange(text: String) { rangeText = text; pageIndex = pageIndex.coerceIn(0, sheetCount - 1); renderPreview() }
    fun updateParity(p: Parity) { parity = p; pageIndex = pageIndex.coerceIn(0, sheetCount - 1); renderPreview() }

    fun setPage(i: Int) { pageIndex = i.coerceIn(0, sheetCount - 1); renderPreview() }

    fun open(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            job = JobState.Idle
            runCatching {
                withContext(Dispatchers.IO) {
                    val mime = ctx.contentResolver.getType(uri)
                    val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: uri.lastPathSegment ?: "document"
                    val src = PageSource.open(ctx, uri, mime)
                    sourceLock.withLock { previewSource?.close(); previewSource = src }
                    Doc(uri, name, mime, src.pageCount)
                }
            }.onSuccess { doc = it; pageIndex = 0; rangeText = ""; parity = Parity.ALL; renderPreview() }
                .onFailure { job = JobState.Failed("Could not open the file: ${it.message}") }
        }
    }

    fun clearDoc() {
        doc = null
        preview = null
        pageIndex = 0
        rangeText = ""
        parity = Parity.ALL
        job = JobState.Idle
        viewModelScope.launch(Dispatchers.IO) {
            sourceLock.withLock {
                previewSource?.close()
                previewSource = null
            }
        }
    }

    /** Re-renders the on-screen preview with the same layout matrix the print job will use. */
    private fun renderPreview() {
        if (doc == null) return
        val o = options
        val page = pageIndex
        val chosen = (selection as? PageSelection.Pages)?.indices
        if (chosen == null) { preview = null; return }
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.IO) {
            val bmp = sourceLock.withLock {
                val src = previewSource ?: return@withLock null
                val scale = PREVIEW_WIDTH.toFloat() / o.paper.widthPx
                SheetComposer(SelectedPages(src, chosen), o, scale, maxRows = (o.paper.heightPx * scale).toInt()).use { composer ->
                    val out = Bitmap.createBitmap(composer.width, composer.height, Bitmap.Config.ARGB_8888)
                    composer.renderRows(page.coerceAtMost(composer.sheetCount - 1), 0, composer.height, out)
                    // Show a landscape sheet the way it reads (the raster itself is always portrait).
                    if (composer.landscape) Bitmap.createBitmap(out, 0, 0, out.width, out.height, Matrix().apply { postRotate(-90f) }, true) else out
                }
            }
            if (bmp != null) preview = bmp
        }
    }

    fun print() {
        val d = doc ?: return
        val dev = device ?: return
        val opts = options
        val chosen = (selection as? PageSelection.Pages)?.indices ?: return
        val ctx = getApplication<Application>()
        job = JobState.Printing(0, sheetCount)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    PageSource.open(ctx, d.uri, d.mime).use { src ->
                        JobExecutor.execute(usb, dev, SelectedPages(src, chosen), opts) { p, n -> job = JobState.Printing(p, n) }
                    }
                }.getOrElse { JobState.Failed("Printing failed: ${it.message}") }
            }
            job = result
        }
    }

    /** Loads a bundled multi-page PDF (handy for checking layouts without a printer). */
    fun openSample() {
        val ctx = getApplication<Application>()
        val f = java.io.File(ctx.cacheDir, "sample-multi.pdf")
        ctx.assets.open("sample-multi.pdf").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        open(Uri.fromFile(f))
    }

    // ---- diagnostics (spike tools kept for troubleshooting) ----

    fun describePrinter() { diagnostics += (device?.let { usb.describe(it) } ?: "No USB printer found.") + "\n" }
    fun clearDiagnostics() { diagnostics = "" }

    fun printerAttributes() = withPrinter { NativeIpp.getPrinterAttributes(it) }

    fun printTestPage() {
        val doc = getApplication<Application>().assets.open("test-a4.pwg").use { it.readBytes() }
        withPrinter { NativeIpp.printPwgRaster(it, doc) }
    }

    private fun withPrinter(block: (Int) -> String) {
        val dev = device ?: return run { diagnostics += "No USB printer found.\n" }
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                val conn = usb.open(dev) ?: return@withContext "openDevice failed"
                try { block(conn.fileDescriptor) } finally { conn.close() }
            }
            diagnostics += out + "\n"
        }
    }

    override fun onCleared() {
        runCatching { previewSource?.close() }
    }

    private companion object { const val PREVIEW_WIDTH = 720 }
}
