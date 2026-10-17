package crystaldigits.freeprint.otg

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.annotation.StringRes
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
import crystaldigits.freeprint.otg.print.CompositeSource
import crystaldigits.freeprint.otg.print.DuplexPlan
import crystaldigits.freeprint.otg.print.JobExecutor
import crystaldigits.freeprint.otg.print.SheetComposer
import crystaldigits.freeprint.otg.print.PageSource
import crystaldigits.freeprint.otg.print.PageSelection
import crystaldigits.freeprint.otg.print.Parity
import crystaldigits.freeprint.otg.print.PrintOptions
import crystaldigits.freeprint.otg.print.SelectedPages
import crystaldigits.freeprint.otg.print.TwoSided

class Doc(val uri: Uri, val name: String, val mime: String?, val pageCount: Int)

/** A rendered preview together with the sheet it shows, so the UI can tell which way the sheet changed. */
class PreviewImage(val sheet: Int, val bitmap: Bitmap)

sealed interface JobState {
    data object Idle : JobState
    data class Printing(val page: Int, val pages: Int) : JobState
    data class Done(val message: String) : JobState
    /** [message] is technical detail (English); [headline] is the localized title, or null for the generic one. */
    data class Failed(val message: String, @StringRes val headline: Int? = null) : JobState
    data object Cancelled : JobState

    /**
     * Manual two-sided printing: the front sides are done and the user must turn the stack over before the backs print.
     * [unpairedLast] is true when the final front has no back; [reversed] when the backs print last-to-first.
     */
    data class NeedsFlip(val fronts: Int, val backs: Int, val unpairedLast: Boolean, val reversed: Boolean, val twoSided: TwoSided) : JobState
}

/** State and actions for the print screen. USB and rendering work always runs on Dispatchers.IO. */
class PrintViewModel(app: Application) : AndroidViewModel(app) {
    private val usb = UsbPrinter(app)
    private var device: UsbDevice? = null

    var printerName by mutableStateOf<String?>(null); private set
    var hasPermission by mutableStateOf(false); private set

    /** The chosen files, in print order. PDFs and images can be mixed. */
    var files by mutableStateOf<List<Doc>>(emptyList()); private set

    /** Summary of every file together: the first file's identity with the combined page count. Null when empty. */
    val doc: Doc? get() = files.firstOrNull()?.let { Doc(it.uri, it.name, it.mime, files.sumOf { f -> f.pageCount }) }
    var pageIndex by mutableIntStateOf(0); private set
    var options by mutableStateOf(PrintOptions()); private set
    var rangeText by mutableStateOf(""); private set
    var parity by mutableStateOf(Parity.ALL); private set
    var previewImage by mutableStateOf<PreviewImage?>(null); private set
    val preview: Bitmap? get() = previewImage?.bitmap
    /** High-resolution sheet for the full-screen viewer; null while the viewer is closed. */
    var largePreview by mutableStateOf<Bitmap?>(null); private set
    private var largeOpen = false
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

    // ---- sheet thumbnails for the strip above the preview ----
    private val thumbCache = LruCache<Int, Bitmap>(THUMB_CACHE_SIZE)
    private val thumbPending = HashSet<Int>()          // main thread only
    private var thumbGeneration = 0
    /** Bumped whenever a thumbnail arrives or the cache is cleared, so composables that read [thumbnail] redraw. */
    var thumbVersion by mutableIntStateOf(0); private set

    /** The cached thumbnail of [sheet], or null while it has not been rendered yet. */
    fun thumbnail(sheet: Int): Bitmap? { thumbVersion; return thumbCache.get(sheet) }

    /** Original page numbers on [sheet] for the strip label: "3", or "5-8" when several pages share a sheet. */
    fun sheetLabel(sheet: Int): String {
        val idx = (selection as? PageSelection.Pages)?.indices ?: return "${sheet + 1}"
        val n = options.perSheet.n
        val part = idx.subList((sheet * n).coerceAtMost(idx.size), ((sheet + 1) * n).coerceAtMost(idx.size))
        return when {
            part.isEmpty() -> "${sheet + 1}"
            part.size == 1 -> "${part[0] + 1}"
            else -> "${part.first() + 1}\u2013${part.last() + 1}"
        }
    }

    /** Renders [sheet]'s thumbnail in the background if it is not cached; safe to call repeatedly. */
    fun requestThumbnail(sheet: Int) {
        if (thumbCache.get(sheet) != null || !thumbPending.add(sheet)) return
        val gen = thumbGeneration
        val o = options
        val chosen = (selection as? PageSelection.Pages)?.indices
        if (chosen == null) { thumbPending.remove(sheet); return }
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = runCatching {
                sourceLock.withLock { previewSource?.let { composeSheet(SelectedPages(it, chosen), o, sheet, THUMB_WIDTH) } }
            }.getOrNull()
            withContext(Dispatchers.Main) {
                thumbPending.remove(sheet)
                if (bmp != null && gen == thumbGeneration) { thumbCache.put(sheet, bmp); thumbVersion++ }
            }
        }
    }

    /** Drops every thumbnail; called when the document, the options or the page selection change. */
    private fun invalidateThumbs() {
        thumbGeneration++
        thumbCache.evictAll()
        thumbPending.clear()
        thumbVersion++
    }

    val canPrint get() = doc != null && hasPermission && job !is JobState.Printing && job !is JobState.NeedsFlip && selection is PageSelection.Pages

    /** How the sheets split into front and back passes, or null when two-sided printing is off or there is only one side. */
    val duplexPlan: DuplexPlan? get() = DuplexPlan.of(sheetCount, options.twoSided, options.reverseBacks)

    /** Follows the printer being plugged in or out, so the status card is always current. */
    private val usbWatcher = UsbWatcher(app) { refreshPrinter() }

    fun refreshPrinter() {
        device = usb.findPrinter()
        printerName = device?.let { listOfNotNull(it.manufacturerName, it.productName).joinToString(" ").ifBlank { "USB printer" } }
        hasPermission = device?.let { usb.hasPermission(it) } ?: false
    }

    fun requestPermission() {
        val d = device ?: return
        usb.requestPermission(d) { refreshPrinter() }
    }

    fun updateOptions(o: PrintOptions) { options = o; invalidateThumbs(); pageIndex = pageIndex.coerceIn(0, sheetCount - 1); renderPreview() }
    fun setRange(text: String) { rangeText = text; invalidateThumbs(); pageIndex = pageIndex.coerceIn(0, sheetCount - 1); renderPreview() }
    fun updateParity(p: Parity) { parity = p; invalidateThumbs(); pageIndex = pageIndex.coerceIn(0, sheetCount - 1); renderPreview() }

    /**
     * Takes the pages on [sheet] out of the pages to print. The file is untouched; the chosen pages are rewritten as a range
     * (for example "1-3, 5, 8-10"), so the user can edit or clear it to bring pages back. With several pages per sheet this
     * removes all of that sheet's pages. The last remaining sheet cannot be removed.
     */
    fun removeSheet(sheet: Int) {
        val chosen = (selection as? PageSelection.Pages)?.indices ?: return
        val n = options.perSheet.n
        val from = (sheet * n).coerceAtMost(chosen.size)
        val to = ((sheet + 1) * n).coerceAtMost(chosen.size)
        val kept = chosen.filterIndexed { i, _ -> i < from || i >= to }
        if (kept.isEmpty() || kept.size == chosen.size) return
        rangeText = PageSelection.format(kept)
        parity = Parity.ALL                      // the list is now explicit, so an odd/even filter would only confuse
        invalidateThumbs()
        pageIndex = (if (sheet < pageIndex) pageIndex - 1 else pageIndex).coerceIn(0, sheetCount - 1)
        renderPreview()
    }

    fun setPage(i: Int) { pageIndex = i.coerceIn(0, sheetCount - 1); renderPreview() }

    fun open(uri: Uri) = replaceFiles(listOf(uri))

    /** Replaces the chosen files. */
    fun replaceFiles(uris: List<Uri>) = loadFiles(uris, append = false)

    /** Adds files after the ones already chosen. */
    fun addFiles(uris: List<Uri>) = loadFiles(uris, append = true)

    fun removeFile(index: Int) {
        if (index !in files.indices) return
        applyFiles(files.filterIndexed { i, _ -> i != index }, keepPage = true)
    }

    /** Moves file [index] earlier (delta -1) or later (delta +1) in the print order. */
    fun moveFile(index: Int, delta: Int) {
        val to = index + delta
        if (index !in files.indices || to !in files.indices) return
        applyFiles(files.toMutableList().apply { add(to, removeAt(index)) }, keepPage = true)
    }

    private fun loadFiles(uris: List<Uri>, append: Boolean) {
        if (uris.isEmpty()) return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            job = JobState.Idle
            val failed = mutableListOf<String>()
            val loaded = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching { describe(ctx, uri) }.getOrElse { failed += (uri.lastPathSegment ?: uri.toString()); null }
                }
            }
            if (failed.isNotEmpty()) job = JobState.Failed(failed.joinToString(", "), R.string.err_open_file)
            if (loaded.isNotEmpty()) applyFiles(if (append) files + loaded else loaded, keepPage = append)
        }
    }

    /** Reads a file's name, type and page count (opening it proves it is readable). */
    private fun describe(ctx: Application, uri: Uri): Doc {
        val mime = ctx.contentResolver.getType(uri)
        val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: uri.lastPathSegment ?: "document"
        PageSource.open(ctx, uri, mime).use { return Doc(uri, name, mime, it.pageCount) }
    }

    /** Makes [list] the current files, rebuilding the preview source. Page numbers change, so any page range is reset. */
    private fun applyFiles(list: List<Doc>, keepPage: Boolean) {
        val ctx = getApplication<Application>()
        files = list
        rangeText = ""
        parity = Parity.ALL
        invalidateThumbs()
        pageIndex = if (keepPage) pageIndex.coerceIn(0, sheetCount - 1) else 0
        if (list.isEmpty()) {
            previewImage = null
            largePreview = null
            job = JobState.Idle
        }
        viewModelScope.launch(Dispatchers.IO) {
            val source = if (list.isEmpty()) null else runCatching {
                CompositeSource.open(ctx, list.map { it.uri to it.mime })
            }.getOrNull()
            sourceLock.withLock { previewSource?.close(); previewSource = source }
            if (list.isNotEmpty() && source == null) {
                job = JobState.Failed(list.joinToString(", ") { it.name }, R.string.err_open_file)
            } else {
                // Thumbnails requested while the old source was still installed show the old order; drop them
                // now that the new source is in place, then draw everything again.
                withContext(Dispatchers.Main) { invalidateThumbs(); renderPreview() }
            }
        }
    }

    fun clearDoc() = applyFiles(emptyList(), keepPage = false)

    /** Opens or closes the full-screen viewer; while it is open only the large preview is kept up to date. */
    fun setLargePreviewOpen(open: Boolean) {
        largeOpen = open
        if (!open) largePreview = null
        renderPreview()
    }

    /** Re-renders the preview (small, or large while the viewer is open) with the same layout the print job uses. */
    private fun renderPreview() {
        if (doc == null) return
        val o = options
        val page = pageIndex
        val chosen = (selection as? PageSelection.Pages)?.indices
        if (chosen == null) { previewImage = null; largePreview = null; return }
        val large = largeOpen
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.IO) {
            val bmp = sourceLock.withLock {
                val src = previewSource ?: return@withLock null
                composeSheet(SelectedPages(src, chosen), o, page, if (large) LARGE_PREVIEW_WIDTH else PREVIEW_WIDTH)
            }
            if (bmp != null) { if (large) largePreview = bmp else previewImage = PreviewImage(page, bmp) }
        }
    }

    /** One sheet as the reader sees it, [width] pixels wide. Caller holds [sourceLock]. */
    private fun composeSheet(src: PageSource, o: PrintOptions, page: Int, width: Int): Bitmap {
        val scale = width.toFloat() / o.paper.widthPx
        return SheetComposer(src, o, scale, maxRows = (o.paper.heightPx * scale).toInt()).use { composer ->
            val out = Bitmap.createBitmap(composer.width, composer.height, Bitmap.Config.ARGB_8888)
            composer.renderRows(page.coerceAtMost(composer.sheetCount - 1), 0, composer.height, out)
            // The raster is the landscape sheet turned a quarter turn counter-clockwise; turn it back clockwise to show it as read.
            if (composer.landscape) Bitmap.createBitmap(out, 0, 0, out.width, out.height, Matrix().apply { postRotate(90f) }, true) else out
        }
    }

    /** What the second pass of a two-sided job needs, captured when the first pass starts so nothing changes in between. */
    private class PendingBacks(val files: List<Doc>, val chosen: List<Int>, val options: PrintOptions, val plan: DuplexPlan, val device: UsbDevice)
    private var pendingBacks: PendingBacks? = null

    /** Prints the document. With two-sided printing on, only the front sides print now and [job] becomes [JobState.NeedsFlip]. */
    fun print() {
        val fs = files.takeIf { it.isNotEmpty() } ?: return
        val dev = device ?: return
        val opts = options
        val chosen = (selection as? PageSelection.Pages)?.indices ?: return
        val plan = duplexPlan
        pendingBacks = null
        if (plan == null) {
            runJob(fs, chosen, opts, dev, sheets = null, rotate180 = false) { it }
        } else {
            runJob(fs, chosen, opts, dev, plan.fronts, rotate180 = false) { result ->
                if (result is JobState.Done) {
                    pendingBacks = PendingBacks(fs, chosen, opts, plan, dev)
                    JobState.NeedsFlip(plan.fronts.size, plan.backs.size, plan.unpairedLast, plan.reversed, opts.twoSided)
                } else result
            }
        }
    }

    /** The user has turned the stack over: print the back sides, turned half a turn when flipping on the short edge. */
    fun continueTwoSided() {
        val p = pendingBacks ?: return
        pendingBacks = null
        runJob(p.files, p.chosen, p.options, p.device, p.plan.backs, p.plan.rotateBacks) { it }
    }

    /** Gives up on the back sides; the printed fronts stay with the user. */
    fun cancelTwoSided() {
        pendingBacks = null
        job = JobState.Idle
    }

    /** Streams [sheets] (null for all) of the chosen pages to the printer and publishes the outcome through [finish]. */
    private fun runJob(
        fs: List<Doc>, chosen: List<Int>, opts: PrintOptions, dev: UsbDevice,
        sheets: List<Int>?, rotate180: Boolean, finish: (JobState) -> JobState,
    ) {
        val ctx = getApplication<Application>()
        job = JobState.Printing(0, sheets?.size ?: sheetCount)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    CompositeSource.open(ctx, fs.map { it.uri to it.mime }).use { src ->
                        JobExecutor.execute(usb, dev, SelectedPages(src, chosen), opts, sheets = sheets, rotate180 = rotate180) { p, n ->
                            job = JobState.Printing(p, n)
                        }
                    }
                }.getOrElse { JobState.Failed(it.message.orEmpty()) }
            }
            job = finish(result)
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

    /**
     * Asks the printer which scanner endpoints it serves (eSCL on the same HTTP-over-USB link as printing, and its
     * admin page), on each of its two IPP interfaces. The answer decides how scanning can be done.
     */
    fun scannerProbe() = withPrinter { fd ->
        buildString {
            for (which in 0..1) {
                HttpSession(fd, which).use { http ->
                    appendLine("--- interface #$which: ${if (http.isOpen) "claimed" else http.openError}")
                    if (!http.isOpen) return@use
                    for (path in listOf("/eSCL/ScannerCapabilities", "/eSCL/ScannerStatus", "/")) {
                        val r = http.request("GET", path, timeoutMs = 8000)
                        if (r == null) { appendLine("GET $path -> failed: ${http.lastError.trim()}"); continue }
                        appendLine("GET $path -> ${r.status} ${r.header("Content-Type").orEmpty()} (${r.body.size} bytes)")
                        if (r.status == 200) appendLine(r.text.take(1800))
                    }
                }
            }
        }
    }

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
        usbWatcher.stop()
        runCatching { previewSource?.close() }
    }

    private companion object {
        const val PREVIEW_WIDTH = 720
        const val LARGE_PREVIEW_WIDTH = 2000     // enough detail to zoom in on a phone screen
        const val THUMB_WIDTH = 200
        const val THUMB_CACHE_SIZE = 80          // about 18 MB of thumbnails
    }
}
