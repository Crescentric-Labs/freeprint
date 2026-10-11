package crystaldigits.freeprint.otg

import java.nio.ByteBuffer

/**
 * One streaming IPP print job over USB. Wraps the native session; every call blocks on USB I/O,
 * so use it from a single background thread. Always [close] it.
 */
class PrintSession(fd: Int) : AutoCloseable {
    private var handle: Long = nativeOpen(fd)

    val isOpen: Boolean get() = handle != 0L && nativeIsOpen(handle)
    val error: String get() = nativeError(handle)

    fun beginJob(color: Boolean, media: String, quality: Int, copies: Int = 1) = nativeBeginJob(handle, color, media, quality, copies)

    fun beginPage(width: Int, height: Int, dpi: Int, pageWidthPt: Int, pageHeightPt: Int) =
        nativeBeginPage(handle, width, height, dpi, pageWidthPt, pageHeightPt)

    /** [rgba] must be a direct buffer holding [rows] full rows of RGBA pixels from position 0. */
    fun addBand(rgba: ByteBuffer, rows: Int) = nativeAddBand(handle, rgba, rows)

    fun endPage() = nativeEndPage(handle)

    /** Ends the job and returns the printer's reply (includes `IPP status: 0x0000` on success). */
    fun finish(): String = nativeFinish(handle)

    override fun close() {
        if (handle != 0L) nativeClose(handle)
        handle = 0L
    }

    private external fun nativeOpen(fd: Int): Long
    private external fun nativeIsOpen(handle: Long): Boolean
    private external fun nativeError(handle: Long): String
    private external fun nativeBeginJob(handle: Long, color: Boolean, media: String, quality: Int, copies: Int): Boolean
    private external fun nativeBeginPage(handle: Long, w: Int, h: Int, dpi: Int, ptW: Int, ptH: Int): Boolean
    private external fun nativeAddBand(handle: Long, rgba: ByteBuffer, rows: Int): Boolean
    private external fun nativeEndPage(handle: Long): Boolean
    private external fun nativeFinish(handle: Long): String
    private external fun nativeClose(handle: Long)

    companion object {
        init { System.loadLibrary("freeprint_native") }
    }
}
