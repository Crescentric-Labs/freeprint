package crystaldigits.freeprint.otg

/** One HTTP response from the printer's web endpoints. */
class HttpResult(val status: Int, val headers: String, val body: ByteArray) {
    val text: String get() = body.toString(Charsets.UTF_8)

    /** Value of response header [name] (case-insensitive), or null. */
    fun header(name: String): String? = headers.lineSequence()
        .firstOrNull { it.startsWith("$name:", ignoreCase = true) }?.substringAfter(':')?.trim()
}

/**
 * HTTP/1.1 over the printer's IPP-over-USB interface. Used for everything except printing: scanner capabilities, scan
 * jobs and the scanned pages. Blocking and not thread-safe; use from one background thread and always [close] it.
 *
 * [which] picks the 0th, 1st... IPP-over-USB interface, because the printer offers two.
 */
class HttpSession(fd: Int, which: Int = 0) : AutoCloseable {
    private var handle: Long = nativeOpen(fd, which)

    /** Empty when the interface was claimed; otherwise why it was not. */
    val openError: String = nativeError(handle)
    val isOpen: Boolean get() = handle != 0L && openError.isEmpty()

    /** Why the last [request] failed at the transport level (not an HTTP error status). */
    var lastError: String = ""
        private set

    /** Sends a request and waits up to [timeoutMs] for each USB read. Null if the transfer itself failed. */
    fun request(
        method: String, path: String, contentType: String? = null, body: ByteArray? = null, timeoutMs: Int = 15_000,
    ): HttpResult? {
        if (!isOpen) { lastError = openError; return null }
        val ok = nativeRequest(handle, method, path, contentType, body, timeoutMs)
        if (!ok) { lastError = nativeError(handle); return null }
        return HttpResult(nativeStatus(handle), nativeHeaders(handle), nativeBody(handle))
    }

    override fun close() {
        if (handle != 0L) nativeClose(handle)
        handle = 0L
    }

    private external fun nativeOpen(fd: Int, which: Int): Long
    private external fun nativeError(handle: Long): String
    private external fun nativeRequest(handle: Long, method: String, path: String, contentType: String?, body: ByteArray?, timeoutMs: Int): Boolean
    private external fun nativeStatus(handle: Long): Int
    private external fun nativeHeaders(handle: Long): String
    private external fun nativeBody(handle: Long): ByteArray
    private external fun nativeClose(handle: Long)

    companion object {
        init { System.loadLibrary("freeprint_native") }
    }
}
