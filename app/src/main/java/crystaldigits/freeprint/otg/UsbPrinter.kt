package crystaldigits.freeprint.otg

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Finds a USB printer, asks for permission, and opens it for libusb. */
class UsbPrinter(private val context: Context) {
    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    /** First attached device exposing any USB printer-class interface (any alt setting). */
    fun findPrinter(): UsbDevice? = manager.deviceList.values.firstOrNull { dev ->
        (0 until dev.interfaceCount).any { dev.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_PRINTER }
    }

    fun hasPermission(device: UsbDevice) = manager.hasPermission(device)

    fun requestPermission(device: UsbDevice, onResult: (Boolean) -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                context.unregisterReceiver(this)
                onResult(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(ACTION_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Must be mutable so the system can add EXTRA_DEVICE / EXTRA_PERMISSION_GRANTED.
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_PERMISSION).setPackage(context.packageName), flags
        )
        manager.requestPermission(device, pi)
    }

    /**
     * Blocks until permission is granted or refused (or [timeoutSec] passes); returns whether it is held.
     * For callers without a screen, such as the print service. Never call on the main thread.
     */
    fun awaitPermission(device: UsbDevice, timeoutSec: Long = 90): Boolean {
        if (hasPermission(device)) return true
        val latch = java.util.concurrent.CountDownLatch(1)
        android.os.Handler(context.mainLooper).post { requestPermission(device) { latch.countDown() } }
        latch.await(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)
        return hasPermission(device)
    }

    fun open(device: UsbDevice): UsbDeviceConnection? = manager.openDevice(device)

    /** Human-readable dump of every interface and endpoint as Android reports them. */
    fun describe(device: UsbDevice): String = buildString {
        appendLine("${device.manufacturerName} ${device.productName}  VID 0x%04x PID 0x%04x".format(device.vendorId, device.productId))
        appendLine("interfaces (incl. alt settings): ${device.interfaceCount}")
        for (i in 0 until device.interfaceCount) {
            val itf = device.getInterface(i)
            appendLine(
                "  IF ${itf.id} alt ${itf.alternateSetting} class ${itf.interfaceClass} " +
                    "sub ${itf.interfaceSubclass} proto ${itf.interfaceProtocol}"
            )
            for (e in 0 until itf.endpointCount) {
                val ep = itf.getEndpoint(e)
                val dir = if (ep.direction == UsbConstants.USB_DIR_IN) "IN " else "OUT"
                appendLine("      EP 0x%02x %s type %d maxpkt %d".format(ep.address, dir, ep.type, ep.maxPacketSize))
            }
        }
    }

    companion object {
        const val ACTION_PERMISSION = "crystaldigits.freeprint.otg.USB_PERMISSION"
    }
}
