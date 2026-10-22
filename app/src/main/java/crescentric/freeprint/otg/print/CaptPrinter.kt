package crescentric.freeprint.otg.print

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface

/**
 * Canon LBP laser printers that take Canon's CAPT language over a plain two-way USB printer port, with no IPP-over-USB.
 * Beta: only the LBP6000 family (LBP6000, 6018, 6020, 6030 ...) is tried, in the profile captdriver uses for the LBP6000/6018.
 */
object CaptPrinter {
    private const val CANON = 0x04a9
    private val SUPPORTED_NAME = Regex("""LBP\s?60\d\d""", RegexOption.IGNORE_CASE)

    class Port(val itf: UsbInterface, val input: UsbEndpoint, val output: UsbEndpoint)

    fun portOf(device: UsbDevice): Port? {
        if (device.vendorId != CANON) return null
        val name = listOfNotNull(device.productName, device.deviceName).joinToString(" ")
        if (!SUPPORTED_NAME.containsMatchIn(name)) return null
        val interfaces = (0 until device.interfaceCount).map { device.getInterface(it) }
        // A printer that offers IPP-over-USB is driven the normal way
        if (interfaces.any { it.interfaceClass == UsbConstants.USB_CLASS_PRINTER && it.interfaceSubclass == 1 && it.interfaceProtocol == 4 }) return null
        return interfaces.filter { it.interfaceClass == UsbConstants.USB_CLASS_PRINTER }
            .sortedBy { if (it.interfaceProtocol == 2) 0 else 1 }
            .firstNotNullOfOrNull { itf ->
                val eps = (0 until itf.endpointCount).map { itf.getEndpoint(it) }.filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
                val input = eps.firstOrNull { it.direction == UsbConstants.USB_DIR_IN }
                val output = eps.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
                if (input != null && output != null) Port(itf, input, output) else null
            }
    }

    fun matches(device: UsbDevice) = portOf(device) != null

    /** Claims the port; closing the result releases it. */
    fun open(connection: UsbDeviceConnection, device: UsbDevice): CaptLink {
        val port = portOf(device) ?: throw CaptException("This printer has no usable USB printer port.")
        if (!connection.claimInterface(port.itf, true)) throw CaptException("Could not claim the printer's USB interface.")
        return CaptLink(connection, port)
    }
}

class CaptLink(private val connection: UsbDeviceConnection, private val port: CaptPrinter.Port) : CaptTransport, AutoCloseable {
    override fun write(data: ByteArray, off: Int, len: Int) {
        var sent = 0
        while (sent < len) {
            val n = connection.bulkTransfer(port.output, data, off + sent, len - sent, WRITE_TIMEOUT_MS)
            if (n <= 0) throw CaptException("The printer stopped accepting data (USB transfer failed).")
            sent += n
        }
    }

    override fun read(buf: ByteArray, timeoutMs: Int): Int {
        val n = connection.bulkTransfer(port.input, buf, buf.size, timeoutMs)
        if (n < 0) throw CaptException("The printer did not answer in time.")
        return n
    }

    override fun close() { connection.releaseInterface(port.itf) }

    private companion object { const val WRITE_TIMEOUT_MS = 30_000 }
}
