package crystaldigits.freeprint.otg.print

import android.hardware.usb.UsbDevice
import crystaldigits.freeprint.otg.JobState
import crystaldigits.freeprint.otg.PrintSession
import crystaldigits.freeprint.otg.UsbPrinter

/** Runs one complete job against a USB printer: open, stream, finish, check the printer's verdict. Blocking. */
object JobExecutor {
    private val SUCCESS = Regex("IPP status: 0x000[0-2]")

    fun execute(
        usb: UsbPrinter, device: UsbDevice, source: PageSource, options: PrintOptions, copies: Int = 1,
        cancelled: () -> Boolean = { false }, progress: (Int, Int) -> Unit = { _, _ -> },
    ): JobState {
        val conn = usb.open(device) ?: return JobState.Failed("Could not open the printer.")
        try {
            PrintSession(conn.fileDescriptor).use { session ->
                if (!session.isOpen) return JobState.Failed("Printer connection failed: ${session.error}")
                when (val err = JobRunner.run(source, options, session, copies, cancelled, progress)) {
                    null -> {}
                    JobRunner.CANCELLED -> return JobState.Cancelled
                    else -> return JobState.Failed(err)
                }
                val reply = session.finish()
                return if (SUCCESS.containsMatchIn(reply)) JobState.Done("Sent to the printer.")
                else JobState.Failed("The printer rejected the job.\n$reply")
            }
        } catch (e: Exception) {
            return JobState.Failed("Printing failed: ${e.message}")
        } finally {
            conn.close()
        }
    }
}
