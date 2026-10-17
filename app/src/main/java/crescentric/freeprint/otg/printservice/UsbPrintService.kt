package crescentric.freeprint.otg.printservice

import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintJobId
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import crescentric.freeprint.otg.AppLocale
import crescentric.freeprint.otg.JobState
import crescentric.freeprint.otg.R
import crescentric.freeprint.otg.UsbPrinter
import crescentric.freeprint.otg.print.FitMode
import crescentric.freeprint.otg.print.JobExecutor
import crescentric.freeprint.otg.print.Orientation
import crescentric.freeprint.otg.print.PdfSource
import crescentric.freeprint.otg.print.PrintOptions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Lets any app print to the USB printer through the system print dialog. The framework hands us a PDF
 * that is already laid out for the chosen paper, so the job is "Fit, centred, one page per sheet".
 * Jobs run one at a time on a background thread.
 */
class UsbPrintService : PrintService() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cancelRequested = ConcurrentHashMap.newKeySet<PrintJobId>()

    /** PrintService.attachBaseContext is final, so strings are read through a context in the app's chosen language. */
    val localized: android.content.Context by lazy { AppLocale.wrap(this) }

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession = UsbDiscoverySession(this)

    override fun onRequestCancelPrintJob(job: PrintJob) {
        cancelRequested.add(job.id)
        if (!job.isStarted) job.cancel()            // still queued: nothing running to stop
    }

    override fun onPrintJobQueued(job: PrintJob) {
        job.start()
        val id = job.id
        val info = job.info
        val attrs = info.attributes
        val copies = info.copies.coerceAtLeast(1)
        val options = PrintOptions(
            paper = PrinterProfile.paperFor(attrs.mediaSize),
            color = attrs.colorMode != PrintAttributes.COLOR_MODE_MONOCHROME,
            fit = FitMode.FIT,
            orientation = Orientation.AUTO,
        )
        worker.execute {
            val result = run(job, id, options, copies)
            cancelRequested.remove(id)
            main.post {
                when (result) {
                    is JobState.Done -> job.complete()
                    JobState.Cancelled -> job.cancel()
                    is JobState.Failed -> job.fail(result.message.lineSequence().first())
                    else -> job.fail(localized.getString(R.string.svc_failed))
                }
            }
        }
    }

    private fun run(job: PrintJob, id: PrintJobId, options: PrintOptions, copies: Int): JobState {
        val usb = UsbPrinter(this)
        val device = usb.findPrinter() ?: return JobState.Failed(localized.getString(R.string.svc_no_printer))
        if (!usb.awaitPermission(device)) {
            return JobState.Failed(localized.getString(R.string.svc_no_access))
        }
        val pfd = job.document.data ?: return JobState.Failed(localized.getString(R.string.svc_doc_unreadable))
        return try {
            PdfSource(pfd).use { src ->                     // closes pfd
                JobExecutor.execute(usb, device, src, options, copies, cancelled = { id in cancelRequested })
            }
        } catch (e: Exception) {
            JobState.Failed(localized.getString(R.string.svc_read_failed, e.message.orEmpty()))
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
