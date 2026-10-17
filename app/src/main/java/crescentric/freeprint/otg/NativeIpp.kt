package crescentric.freeprint.otg

/** JNI bridge to libusb. Both calls block on USB I/O, so call them off the main thread. */
object NativeIpp {
    init { System.loadLibrary("freeprint_native") }

    /** IPP Get-Printer-Attributes over IPP-over-USB. Returns a log including the raw reply. */
    external fun getPrinterAttributes(fd: Int): String

    /** IPP Print-Job with a PWG raster document (A4, colour, normal quality). */
    external fun printPwgRaster(fd: Int, data: ByteArray): String
}
