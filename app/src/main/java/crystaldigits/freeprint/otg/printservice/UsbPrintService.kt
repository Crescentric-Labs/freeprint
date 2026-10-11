package crystaldigits.freeprint.otg.printservice

import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintJobId
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import crystaldigits.freeprint.otg.JobState
import crystaldigits.freeprint.otg.UsbPrinter
import crystaldigits.freeprint.otg.print.FitMode
import crystaldigits.freeprint.otg.print.JobExecutor
import crystaldigits.freeprint.otg.print.Orientation
import crystaldigits.freeprint.otg.print.PdfSource
import crystaldigits.freeprint.otg.print.PrintOptions
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
                    else -> job.fail("Printing failed.")
                }
            }
        }
    }

    private fun run(job: PrintJob, id: PrintJobId, options: PrintOptions, copies: Int): JobState {
        val usb = UsbPrinter(this)
        val device = usb.findPrinter() ?: return JobState.Failed("The USB printer is not connected.")
        if (!usb.awaitPermission(device)) {
            return JobState.Failed("USB access was not allowed. Open OTG Print and allow access to the printer.")
        }
        val pfd = job.document.data ?: return JobState.Failed("The document could not be read.")
        return try {
            PdfSource(pfd).use { src ->                     // closes pfd
                JobExecutor.execute(usb, device, src, options, copies, cancelled = { id in cancelRequested })
            }
        } catch (e: Exception) {
            JobState.Failed("Could not read the document: ${e.message}")
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
