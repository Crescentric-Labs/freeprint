package crescentric.freeprint.otg.print

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import java.io.IOException
import java.io.OutputStream

/**
 * Printers that cannot do IPP-over-USB and instead take Samsung's own page language over a plain USB printer port
 * (class 7, subclass 1, protocol 2). Beta: only models in [SUPPORTED] are driven, because a different model may need a
 * different dialect and would print garbage.
 */
object QpdlPrinter {
    private const val SAMSUNG = 0x04e8

    /** (vendor id, product id) of the models this build has been written for. ML-2160 series: 0x04e8:0x330f. */
    private val SUPPORTED = setOf(SAMSUNG to 0x330f)

    /** The plain printer-port interface of [device], or null if it is not a supported model or has no usable port. */
    fun portOf(device: UsbDevice): UsbInterface? {
        if ((device.vendorId to device.productId) !in SUPPORTED) return null
        return (0 until device.interfaceCount).map { device.getInterface(it) }.firstOrNull { itf ->
            itf.interfaceClass == UsbConstants.USB_CLASS_PRINTER && itf.interfaceSubclass == 1 && itf.interfaceProtocol == 2 &&
                bulkOut(itf) != null
        }
    }

    fun matches(device: UsbDevice) = portOf(device) != null

    private fun bulkOut(itf: UsbInterface): UsbEndpoint? = (0 until itf.endpointCount).map { itf.getEndpoint(it) }.firstOrNull {
        it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT
    }

    /** Claims the port and returns a stream that writes to its bulk-out endpoint. Closing it releases the interface. */
    fun open(connection: UsbDeviceConnection, device: UsbDevice): OutputStream {
        val itf = portOf(device) ?: throw IOException("This printer has no usable USB printer port.")
        val endpoint = bulkOut(itf)!!
        if (!connection.claimInterface(itf, true)) throw IOException("Could not claim the printer's USB interface.")
        return UsbBulkStream(connection, itf, endpoint)
    }
}

/** Buffers into chunks that are a multiple of the 512-byte packet size, so every transfer but the last ends on a packet boundary. */
private class UsbBulkStream(
    private val connection: UsbDeviceConnection, private val itf: UsbInterface, private val endpoint: UsbEndpoint,
) : OutputStream() {
    private val chunk = ByteArray(CHUNK)
    private var filled = 0

    override fun write(b: Int) {
        chunk[filled++] = b.toByte()
        if (filled == CHUNK) flushChunk()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var at = off
        var left = len
        while (left > 0) {
            val n = minOf(left, CHUNK - filled)
            System.arraycopy(b, at, chunk, filled, n)
            filled += n; at += n; left -= n
            if (filled == CHUNK) flushChunk()
        }
    }

    override fun flush() = flushChunk()

    private fun flushChunk() {
        var sent = 0
        while (sent < filled) {
            val n = connection.bulkTransfer(endpoint, chunk, sent, filled - sent, TIMEOUT_MS)
            if (n <= 0) throw IOException("The printer stopped accepting data (USB transfer failed).")
            sent += n
        }
        filled = 0
    }

    override fun close() {
        try { flushChunk() } finally { connection.releaseInterface(itf) }
    }

    private companion object {
        const val CHUNK = 16 * 1024
        /** The printer stops reading while it prints a page, so a transfer can legitimately wait a long time. */
        const val TIMEOUT_MS = 60_000
    }
}
