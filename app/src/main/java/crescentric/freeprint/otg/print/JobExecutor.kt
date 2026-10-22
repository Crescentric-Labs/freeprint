package crescentric.freeprint.otg.print

import android.hardware.usb.UsbDevice
import crescentric.freeprint.otg.JobState
import crescentric.freeprint.otg.PrintSession
import crescentric.freeprint.otg.UsbPrinter

/** Runs one complete job against a USB printer: open, stream, finish, check the printer's verdict. Blocking. */
object JobExecutor {
    private val SUCCESS = Regex("IPP status: 0x000[0-2]")

    fun execute(
        usb: UsbPrinter, device: UsbDevice, source: PageSource, options: PrintOptions, copies: Int = 1,
        sheets: List<Int>? = null, rotate180: Boolean = false,
        cancelled: () -> Boolean = { false }, paused: () -> Boolean = { false }, progress: (Int, Int) -> Unit = { _, _ -> },
    ): JobState {
        val conn = usb.open(device) ?: return JobState.Failed("Could not open the printer.")
        try {
            if (QpdlPrinter.matches(device)) {
                // A host-based Samsung laser: no IPP, the page language goes straight to the USB printer port.
                val err = QpdlPrinter.open(conn, device).use { out ->
                    QpdlRunner.run(source, options, out, copies, sheets, rotate180, cancelled = cancelled, paused = paused, progress = progress)
                }
                return when (err) {
                    null -> JobState.Done("Sent to the printer.")
                    JobRunner.CANCELLED -> JobState.Cancelled
                    else -> JobState.Failed(err)
                }
            }
            PrintSession(conn.fileDescriptor).use { session ->
                if (!session.isOpen) return JobState.Failed("Printer connection failed: ${session.error}")
                when (val err = JobRunner.run(source, options, session, copies, sheets, rotate180, cancelled, paused, progress)) {
                    null -> {}
                    JobRunner.CANCELLED -> return JobState.Cancelled
                    else -> return JobState.Failed(err)
                }
                val reply = session.finish()
                return if (SUCCESS.containsMatchIn(reply)) JobState.Done("Sent to the printer.")
                else JobState.Failed("The printer rejected the job.\n$reply")
            }
        } catch (e: Exception) {
            return JobState.Failed(e.message ?: e.toString())
        } finally {
            conn.close()
        }
    }
}
