package crystaldigits.freeprint.otg.printservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrinterDiscoverySession
import androidx.core.content.ContextCompat
import crystaldigits.freeprint.otg.R
import crystaldigits.freeprint.otg.UsbPrinter

/** Tells the print framework whether a USB printer is attached, and keeps that current as it is plugged in or out. */
class UsbDiscoverySession(private val service: UsbPrintService) : PrinterDiscoverySession() {
    private val usb = UsbPrinter(service)
    private val printerId: PrinterId = service.generatePrinterId(PrinterProfile.LOCAL_ID)
    private var present = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    /** Adds the printer if one is attached, removes it if it has gone. */
    private fun refresh() {
        val dev = usb.findPrinter()
        if (dev != null) {
            val name = listOfNotNull(dev.manufacturerName, dev.productName).joinToString(" ").ifBlank { "USB printer" }
            val info = PrinterInfo.Builder(printerId, name, PrinterInfo.STATUS_IDLE)
                .setDescription(service.localized.getString(R.string.svc_printer_desc))
                .setCapabilities(PrinterProfile.capabilities(printerId))
                .build()
            addPrinters(listOf(info))
            present = true
        } else if (present) {
            removePrinters(listOf(printerId))
            present = false
        }
    }

    override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(service, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    override fun onStopPrinterDiscovery() {
        runCatching { service.unregisterReceiver(receiver) }
    }

    override fun onValidatePrinters(printerIds: MutableList<PrinterId>) = refresh()
    override fun onStartPrinterStateTracking(printerId: PrinterId) = refresh()
    override fun onStopPrinterStateTracking(printerId: PrinterId) {}
    override fun onDestroy() { runCatching { service.unregisterReceiver(receiver) } }
}
