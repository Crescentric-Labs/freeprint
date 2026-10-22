package crescentric.freeprint.otg

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import crescentric.freeprint.otg.print.CompositeSource
import crescentric.freeprint.otg.print.DuplexPlan
import crescentric.freeprint.otg.print.JobExecutor
import crescentric.freeprint.otg.print.Orientation
import crescentric.freeprint.otg.print.SheetComposer
import crescentric.freeprint.otg.print.PageSource
import crescentric.freeprint.otg.print.PageSelection
import crescentric.freeprint.otg.print.FileSpec
import crescentric.freeprint.otg.print.PageLocator
import crescentric.freeprint.otg.print.Parity
import crescentric.freeprint.otg.print.PrintOptions
import crescentric.freeprint.otg.print.SelectedPages
import crescentric.freeprint.otg.print.SheetRender
import crescentric.freeprint.otg.print.TextPdf
import crescentric.freeprint.otg.print.ThumbRemap
import crescentric.freeprint.otg.print.TwoSided

/** A chosen file. [enhancedPages] are the pages (0-based, within this file) that are optimized for print. */
class Doc(val uri: Uri, val name: String, val mime: String?, val pageCount: Int, val enhancedPages: Set<Int> = emptySet()) {
    val isImage get() = mime?.startsWith("image/") == true
    fun withEnhancedPages(pages: Set<Int>) = Doc(uri, name, mime, pageCount, pages)
    val spec get() = FileSpec(uri, mime, enhancedPages)
}

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
    /** The "USB OTG • ..." line to show for a printer without IPP that is driven with its own page language (beta); null for a normal printer. */
    @get:StringRes var hostLabel by mutableStateOf<Int?>(null); private set
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
    var largeImage by mutableStateOf<PreviewImage?>(null); private set
    val largePreview: Bitmap? get() = largeImage?.bitmap
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
            // The sheet on show has priority: thumbnails wait while it is being drawn
            while (previewJob?.isActive == true) delay(30)
            val bmp = runCatching {
                sourceLock.withLock { previewSource?.let { composeSheet(SelectedPages(it, chosen), o, sheet, THUMB_WIDTH) } }
            }.getOrNull()
            withContext(Dispatchers.Main) {
                thumbPending.remove(sheet)
                if (staleThumbs.remove(sheet)) { thumbVersion++; return@withContext }   // drawn from the old source; the strip asks again
                if (bmp != null && gen == thumbGeneration) { thumbCache.put(sheet, bmp); thumbVersion++ }
            }
        }
    }

    /**
     * Keeps the thumbnails that are still right after [sheet] is removed and drops the rest (see [ThumbRemap]), so deleting a
     * page redraws only what has to change instead of the whole strip.
     */
    private fun remapThumbsAfterRemoving(sheet: Int) {
        val old = thumbCache.snapshot()
        previewCache.evictAll()
        thumbGeneration++                       // anything still being drawn was drawn for the old numbering
        thumbPending.clear()
        thumbCache.evictAll()
        val firstPageDecidesLayout = options.orientation == Orientation.AUTO || options.perSheet.n > 1
        for ((key, bitmap) in old) {
            ThumbRemap.remap(key, sheet, options.perSheet.n, firstPageDecidesLayout)?.let { thumbCache.put(it, bitmap) }
        }
        thumbVersion++
    }

    /** Drops every thumbnail; called when the document, the options or the page selection change. */
    private fun invalidateThumbs() {
        previewCache.evictAll()
        thumbGeneration++
        thumbCache.evictAll()
        thumbPending.clear()
        thumbVersion++
    }

    /** How many sides the printer will actually be sent when Print is tapped: every copy of every sheet, plus any blank backs. */
    val printTotal: Int get() = duplexPlan?.let { it.fronts.size + it.backs.size } ?: (sheetCount * options.copies)

    /** True while a running job is held between sheets; see [togglePause]. */
    var paused by mutableStateOf(false); private set
    @Volatile private var pauseFlag = false

    /** Holds the running job after the sheet being sent, or lets it carry on. A long pause may make the printer give up on the job. */
    fun togglePause() {
        if (job !is JobState.Printing) return
        pauseFlag = !pauseFlag
        paused = pauseFlag
    }

    val canPrint get() = doc != null && hasPermission && job !is JobState.Printing && job !is JobState.NeedsFlip && selection is PageSelection.Pages

    /** How the sheets split into front and back passes, or null when two-sided printing is off or there is only one side. */
    val duplexPlan: DuplexPlan? get() = DuplexPlan.of(sheetCount, options.twoSided, options.reverseBacks, options.copies)

    /** Follows the printer being plugged in or out, so the status card is always current. */
    private val usbWatcher = UsbWatcher(app) { refreshPrinter() }

    fun refreshPrinter() {
        device = usb.findPrinter()
        hostLabel = device?.let {
            when {
                crescentric.freeprint.otg.print.QpdlPrinter.matches(it) -> R.string.printer_mode_spl
                crescentric.freeprint.otg.print.CaptPrinter.matches(it) -> R.string.printer_mode_capt
                else -> null
            }
        }
        printerName = device?.let { listOfNotNull(it.manufacturerName, it.productName).joinToString(" ").ifBlank { "USB printer" } }
        hasPermission = device?.let { usb.hasPermission(it) } ?: false
    }

    fun requestPermission() {
        val d = device ?: return
        usb.requestPermission(d) { refreshPrinter() }
    }

    fun updateOptions(o: PrintOptions) { options = o; invalidateThumbs(); pageIndex = pageIndex.coerceIn(0, sheetCount - 1); renderPreview() }
    fun setRange(text: String) { changeSelection { rangeText = text } }
    fun updateParity(p: Parity) { changeSelection { parity = p } }

    /**
     * Applies a change to the range or the odd/even filter. If it leaves fewer pages chosen than before, the earlier choice is kept
     * as the restore point, so the Restore button also covers pages dropped here, not only ones removed with the strip's delete button.
     */
    private fun changeSelection(apply: () -> Unit) {
        val before = (selection as? PageSelection.Pages)?.indices?.size
        val old = RestorePoint(rangeText, parity, before ?: 0)
        apply()
        val after = (selection as? PageSelection.Pages)?.indices?.size
        if (before != null && after != null) {
            val point = restorePoint ?: if (after < before) old.also { restorePoint = it } else null
            val dropped = if (point == null) 0 else point.pageCount - after
            if (dropped > 0) removedCount = dropped else forgetRestorePoint()
        }
        invalidateThumbs(); pageIndex = pageIndex.coerceIn(0, sheetCount - 1); renderPreview()
    }

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
        val before = restorePoint ?: RestorePoint(rangeText, parity, chosen.size).also { restorePoint = it }
        removedCount = before.pageCount - kept.size
        rangeText = PageSelection.format(kept)
        parity = Parity.ALL                      // the list is now explicit, so an odd/even filter would only confuse
        remapThumbsAfterRemoving(sheet)
        pageIndex = (if (sheet < pageIndex) pageIndex - 1 else pageIndex).coerceIn(0, sheetCount - 1)
        renderPreview()
    }

    /** Bumped when the user moves to another sheet, so the strip centres it; not bumped when a sheet is removed. */
    var pageScrollTick by mutableIntStateOf(0); private set

    /** The selection as it was before pages were removed with the strip's delete button, so it can be put back. */
    private class RestorePoint(val range: String, val parity: Parity, val pageCount: Int)
    private var restorePoint: RestorePoint? = null

    /** How many pages the strip's delete button has taken out since the selection last changed some other way. */
    var removedCount by mutableIntStateOf(0); private set

    private fun forgetRestorePoint() { restorePoint = null; removedCount = 0 }

    /** Puts back every page removed with the strip's delete button. */
    fun restoreRemoved() {
        val r = restorePoint ?: return
        forgetRestorePoint()
        rangeText = r.range
        parity = r.parity
        invalidateThumbs()
        pageIndex = pageIndex.coerceIn(0, sheetCount - 1)
        renderPreview()
    }

    fun setPage(i: Int) {
        pageScrollTick++
        pageIndex = i.coerceIn(0, sheetCount - 1); renderPreview() }

    fun open(uri: Uri) = replaceFiles(listOf(uri))

    /** Bumped each time files arrive from outside (the share sheet, "open with"), so the screens can come back to the print tab. */
    var externalOpenTick by mutableIntStateOf(0); private set

    /** Files handed to the app by another app: they replace what was chosen and the print tab is shown. */
    fun openFromOutside(uris: List<Uri>, text: String? = null, subject: String? = null) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            // Shared text, and shared text files, become a PDF first so they print like everything else
            val ready = withContext(Dispatchers.IO) {
                val out = ArrayList<Uri>()
                if (!text.isNullOrBlank()) out += Uri.fromFile(TextPdf.render(ctx, text, subject))
                for (u in uris) {
                    val type = runCatching { ctx.contentResolver.getType(u) }.getOrNull()
                    if (type != null && type.startsWith("text/")) {
                        val body = runCatching { ctx.contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                        val shownName = runCatching {
                            ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                        }.getOrNull()
                        if (!body.isNullOrBlank()) out += Uri.fromFile(TextPdf.render(ctx, body, null, fileName = shownName)) else out += u
                    } else out += u
                }
                out
            }
            if (ready.isNotEmpty()) { replaceFiles(ready); externalOpenTick++ }
        }
    }

    /** Replaces the chosen files. */
    fun replaceFiles(uris: List<Uri>) = loadFiles(uris, append = false)

    /** Adds files after the ones already chosen. */
    fun addFiles(uris: List<Uri>) = loadFiles(uris, append = true)

    fun removeFile(index: Int) {
        if (index !in files.indices) return
        applyFiles(files.filterIndexed { i, _ -> i != index }, keepPage = true)
    }

    /** Moves the file at [from] to position [to] (a drag in the file list). */
    fun moveFileTo(from: Int, to: Int) {
        if (from !in files.indices || to !in files.indices || from == to) return
        applyFiles(files.toMutableList().apply { add(to, removeAt(from)) }, keepPage = true)
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
        // A file:// address (a PDF built from a scan, a file opened with the app) has no content type from the resolver; go by its extension
        val mime = ctx.contentResolver.getType(uri)
            ?: MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase().takeIf { it.isNotEmpty() }
                ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: uri.lastPathSegment ?: "document"
        PageSource.open(ctx, uri, mime).use { return Doc(uri, name, mime, it.pageCount) }
    }

    /** Makes [list] the current files, rebuilding the preview source. Page numbers change, so any page range is reset. */
    private fun applyFiles(list: List<Doc>, keepPage: Boolean) {
        val ctx = getApplication<Application>()
        forgetRestorePoint()
        files = list
        rangeText = ""
        parity = Parity.ALL
        invalidateThumbs()
        pageIndex = if (keepPage) pageIndex.coerceIn(0, sheetCount - 1) else 0
        if (list.isEmpty()) {
            previewImage = null
            largeImage = null
            job = JobState.Idle
        }
        reopenSource(list)
    }

    /** The pages on [sheet] as (file index, page inside that file); several pages share a sheet when printing n-up. */
    private fun pagesOnSheet(sheet: Int): List<Pair<Int, Int>> {
        val chosen = (selection as? PageSelection.Pages)?.indices ?: return emptyList()
        val n = options.perSheet.n
        val counts = files.map { it.pageCount }
        return (sheet * n until minOf((sheet + 1) * n, chosen.size)).mapNotNull { PageLocator.locate(counts, chosen[it]) }
    }

    /** True when every page on [sheet] is optimized for print. */
    fun isSheetEnhanced(sheet: Int): Boolean {
        enhanceVersion
        val pages = pagesOnSheet(sheet)
        return pages.isNotEmpty() && pages.all { (f, p) -> p in files[f].enhancedPages }
    }

    /** True when [sheet] has any page that "optimize for print" can be switched on or off for. */
    fun canEnhanceSheet(sheet: Int) = pagesOnSheet(sheet).isNotEmpty()

    /**
     * Turns "optimize for print" on for the pages on [sheet], or off again if they all already have it. Only those pages change,
     * not the rest of their file, and the original is never altered.
     */
    fun toggleEnhanceSheet(sheet: Int) {
        val pages = pagesOnSheet(sheet).takeIf { it.isNotEmpty() } ?: return
        val turnOn = !isSheetEnhanced(sheet)
        files = files.mapIndexed { i, d ->
            val mine = pages.filter { it.first == i }.map { it.second }
            if (mine.isEmpty()) d else d.withEnhancedPages(if (turnOn) d.enhancedPages + mine else d.enhancedPages - mine.toSet())
        }
        enhanceVersion++
        // Only this sheet looks different now, so only its thumbnail and preview are drawn again; the rest of the strip stays put
        reopenSource(files) { invalidateSheet(sheet); renderPreview() }
    }

    /** Bumped when a page's optimized state changes, so composables reading [isSheetEnhanced] redraw. */
    private var enhanceVersion by mutableIntStateOf(0)

    /** Drops the cached thumbnail and previews of one [sheet] so they are drawn again, leaving every other sheet's as they are. */
    private fun invalidateSheet(sheet: Int) {
        thumbCache.remove(sheet)
        previewCache.remove(previewKey(sheet, false))
        previewCache.remove(previewKey(sheet, true))
        // A thumbnail of this sheet being drawn right now was drawn from the old source: throw it away when it lands, and draw it again
        if (sheet in thumbPending) staleThumbs += sheet
        thumbPending.remove(sheet)
        thumbVersion++
    }
    private val staleThumbs = HashSet<Int>()       // main thread only

    /**
     * Opens the files as the preview source on a background thread, then runs [onInstalled] on the main thread. By default that
     * redraws every thumbnail and the preview; pass something narrower when only part of the picture changed.
     */
    private fun reopenSource(list: List<Doc>, onInstalled: () -> Unit = { invalidateThumbs(); renderPreview() }) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val source = if (list.isEmpty()) null else runCatching {
                CompositeSource.open(ctx, list.map { it.spec })
            }.getOrNull()
            sourceLock.withLock { previewSource?.close(); previewSource = source }
            if (list.isNotEmpty() && source == null) {
                job = JobState.Failed(list.joinToString(", ") { it.name }, R.string.err_open_file)
            } else {
                // Thumbnails requested while the old source was still installed show the old order or look; drop what changed
                // now that the new source is in place, then draw it again.
                withContext(Dispatchers.Main) { onInstalled() }
            }
        }
    }

    fun clearDoc() = applyFiles(emptyList(), keepPage = false)

    /** Puts printing back to how the app starts: no files, no page choice, default settings, no job or two-sided state. */
    fun resetAll() {
        pendingBacks = null
        pauseFlag = false; paused = false
        options = PrintOptions()
        job = JobState.Idle
        clearDoc()
    }

    /** Opens or closes the full-screen viewer; while it is open only the large preview is kept up to date. */
    fun setLargePreviewOpen(open: Boolean) {
        largeOpen = open
        if (!open) largeImage = null
        renderPreview()
    }

    /** Re-renders the preview (small, or large while the viewer is open) with the same layout the print job uses. */
    private fun renderPreview() {
        if (doc == null) return
        val o = options
        val page = pageIndex
        val chosen = (selection as? PageSelection.Pages)?.indices
        if (chosen == null) { previewImage = null; largeImage = null; return }
        val large = largeOpen
        previewJob?.cancel()
        val gen = thumbGeneration
        val width = if (large) LARGE_PREVIEW_WIDTH else PREVIEW_WIDTH
        fun show(bmp: Bitmap) { if (large) largeImage = PreviewImage(page, bmp) else previewImage = PreviewImage(page, bmp) }

        // A sheet that was drawn ahead of time appears at once, so swiping onto it (an optimized page can take a moment to draw) does not wait
        previewCache.get(previewKey(page, large))?.let { show(it) }
        previewJob = viewModelScope.launch(Dispatchers.IO) {
            if (previewCache.get(previewKey(page, large)) == null) {
                val bmp = sourceLock.withLock {
                    val src = previewSource ?: return@withLock null
                    composeSheet(SelectedPages(src, chosen), o, page, width)
                }
                if (bmp != null) {
                    if (gen == thumbGeneration) previewCache.put(previewKey(page, large), bmp)
                    show(bmp)
                }
            }
            // Then draw the neighbours quietly, so the next swipe either way is instant
            for (next in listOf(page + 1, page - 1)) {
                if (!isActive || gen != thumbGeneration || next !in 0 until sheetCount) continue
                if (previewCache.get(previewKey(next, large)) != null) continue
                val bmp = sourceLock.withLock {
                    val src = previewSource ?: return@withLock null
                    composeSheet(SelectedPages(src, chosen), o, next, width)
                }
                if (bmp != null && gen == thumbGeneration) previewCache.put(previewKey(next, large), bmp)
            }
        }
    }

    /** Previews drawn already (the sheet on show and its neighbours), in the small and the full-screen size. */
    private val previewCache = object : LruCache<Int, Bitmap>(PREVIEW_CACHE_BYTES) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }
    private fun previewKey(sheet: Int, large: Boolean) = if (large) sheet + LARGE_KEY_OFFSET else sheet

    /** One sheet as the reader sees it, [width] pixels wide. Caller holds [sourceLock]. */
    private fun composeSheet(src: PageSource, o: PrintOptions, page: Int, width: Int): Bitmap = SheetRender.compose(src, o, page, width)

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
            // Copies are collated by repeating the sheet list, so they do not depend on the printer honouring a copies setting
            val sheets = if (opts.copies > 1) List(opts.copies) { (0 until sheetCount).toList() }.flatten() else null
            runJob(fs, chosen, opts, dev, sheets = sheets, rotate180 = false) { it }
        } else {
            runJob(fs, chosen, opts, dev, plan.fronts, rotate180 = false) { result ->
                if (result is JobState.Done) {
                    pendingBacks = PendingBacks(fs, chosen, opts, plan, dev)
                    JobState.NeedsFlip(plan.fronts.size, plan.backs.size, plan.unpairedLast, plan.reversed, opts.twoSided)
                } else {
                    // A failure here can be the printer's reply going missing after it printed the fronts all the same,
                    // so keep the backs available; the user decides whether to carry on (see resumeAfterFailedFronts)
                    if (result is JobState.Failed) pendingBacks = PendingBacks(fs, chosen, opts, plan, dev)
                    result
                }
            }
        }
    }

    /** True when the front sides reported a failure but the back sides can still be printed on the user's say-so. */
    val backsAfterFailure: Boolean get() = job is JobState.Failed && pendingBacks != null

    /** The fronts reported a failure but came out fine: go on to the flip instructions, then the backs, as if they had succeeded. */
    fun resumeAfterFailedFronts() {
        val p = pendingBacks ?: return
        job = JobState.NeedsFlip(p.plan.fronts.size, p.plan.backs.size, p.plan.unpairedLast, p.plan.reversed, p.options.twoSided)
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
        pauseFlag = false; paused = false
        job = JobState.Printing(0, sheets?.size ?: sheetCount)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    CompositeSource.open(ctx, fs.map { it.spec }).use { src ->
                        JobExecutor.execute(usb, dev, SelectedPages(src, chosen), opts, sheets = sheets, rotate180 = rotate180, paused = { pauseFlag }) { p, n ->
                            job = JobState.Printing(p, n)
                        }
                    }
                }.getOrElse { JobState.Failed(it.message.orEmpty()) }
            }
            pauseFlag = false; paused = false
            job = finish(result)
        }
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
        const val PREVIEW_CACHE_BYTES = 72 * 1024 * 1024   // the sheet on show and its neighbours: about 3 at full-screen size, many more small ones
        const val LARGE_KEY_OFFSET = 1_000_000
        const val THUMB_CACHE_SIZE = 80          // about 18 MB of thumbnails
    }
}
