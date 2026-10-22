package crescentric.freeprint.otg.print

import java.io.IOException
import java.util.Calendar

/*
 * The CAPT conversation with a Canon LBP printer: commands, status polling, the job and page sequence.
 *
 * This is a Kotlin port, for the LBP6000 / LBP6018 profile, of capt-command.c, capt-status.c, generic-ops.c, paper.c and
 * prn_lbp2900.c from captdriver (https://github.com/mounaiban/captdriver), Copyright (C) 2013 Alexey Galakhov
 * <agalakhov@gmail.com> and Copyright (C) 2016 Alexei Gordeev <KP1533TM2@gmail.com>, licensed under the GNU General Public
 * License, version 3 or (at your option) any later version. It is used here under the GNU General Public License version 3.
 * The protocol itself was reverse engineered by Nicolas Boichat, Benoit Bolsee and Alexey Galakhov.
 */

/** Both directions of the printer's USB port. */
interface CaptTransport {
    /** Writes all of [len] bytes of [data] from [off] or throws. */
    fun write(data: ByteArray, off: Int, len: Int)

    /** Reads whatever the printer has ready (at least one byte) into [buf]; returns the count, or throws on timeout/failure. */
    fun read(buf: ByteArray, timeoutMs: Int): Int
}

class CaptException(message: String) : IOException(message)

/** One CAPT print job. Not thread safe; every call blocks on the printer. */
class CaptSession(
    private val port: CaptTransport,
    private val cancelled: () -> Boolean = { false },
    private val sleepMs: (Long) -> Unit = { Thread.sleep(it) },
) {
    /** The last lines of the conversation, for the error message when something goes wrong. */
    val trace = ArrayDeque<String>()

    private var job = 0
    private val io = ByteArray(0x10000)
    private var ioSize = 0

    // --- status ---------------------------------------------------------------------------------------------------------
    private class Status {
        val word = IntArray(7)
        var pageDecoding = 0
        var pagePrinting = 0
        var pageOut = 0
        var pageCompleted = 0
        var pageReceived = 0
        fun flag(set: Int, bit: Int) = word[set] and (1 shl bit) != 0
        val busy get() = flag(0, 7)
        val uninit get() = flag(0, 5) || flag(0, 4)
        val bufferFull get() = flag(0, 2)
        val noPaper1 get() = flag(0, 1)
        val noPaper2 get() = flag(1, 14)
        val printing get() = flag(1, 2)
        val processing1 get() = flag(1, 7)
    }

    private val status = Status()

    private fun log(line: String) {
        trace.addLast(line)
        while (trace.size > 40) trace.removeFirst()
    }

    private fun w(lo: Int, hi: Int) = (hi and 0xFF shl 8) or (lo and 0xFF)

    /** [payload] is the reply without its 4-byte header; the size limits below count the header, as the original does. */
    private fun decodeStatus(payload: ByteArray) {
        val total = payload.size + 4
        val s = payload.copyOf(64)
        fun at(i: Int) = w(s[i].toInt(), s[i + 1].toInt())
        status.word[0] = at(0)
        if (total <= 2) return
        status.word[1] = at(8)
        if (total <= 10) return
        status.word[2] = at(10); status.word[3] = at(12)
        status.pageDecoding = at(14); status.pagePrinting = at(16); status.pageOut = at(18); status.pageCompleted = at(20)
        status.pageReceived = at(34)
        status.word[4] = at(24); status.word[5] = at(30); status.word[6] = at(38)
    }

    /** CHKSTATUS (0xE0A0): the plain status. */
    private fun getStatus(): Status = download(CHKSTATUS)

    /** CHKXSTATUS (0xA0A8): the extended status, which the LBP6000 profile polls exclusively. */
    private fun getXStatusOnly(): Status = download(CHKXSTATUS)

    private fun download(cmd: Int): Status {
        val reply = sendRecv(cmd, null)
        decodeStatus(reply)
        log("status p=%d/%d/%d/%d rx=%d f=%04x %04x".format(status.pageDecoding, status.pagePrinting, status.pageOut, status.pageCompleted, status.pageReceived, status.word[0], status.word[1]))
        return status
    }

    private fun waitReady() {
        var tries = 0
        while (getStatus().busy) { pause("the printer stayed busy", ++tries) }
    }

    private fun waitXReadyOnly() {
        var tries = 0
        while (getXStatusOnly().busy) { pause("the printer stayed busy", ++tries) }
    }

    private fun pause(what: String, tries: Int, limit: Int = 180) {
        if (cancelled()) throw CaptCancelled()
        if (tries > limit) throw CaptException("Timed out: $what.")
        sleepMs(1000)
    }

    // --- commands -------------------------------------------------------------------------------------------------------
    private fun copyCmd(cmd: Int, data: ByteArray?, at: Int): Int {
        val size = data?.size ?: 0
        if (at + 4 + size > io.size) throw CaptException("Command too large.")
        data?.copyInto(io, at + 4)
        io[at] = cmd.toByte(); io[at + 1] = (cmd shr 8).toByte()
        io[at + 2] = (size + 4).toByte(); io[at + 3] = ((size + 4) shr 8).toByte()
        return at + 4 + size
    }

    private fun sendBuffer() {
        log("send %04x len %d".format(w(io[0].toInt(), io[1].toInt()), ioSize))
        var at = 0
        while (at < ioSize) {
            val n = minOf(4096, ioSize - at)
            port.write(io, at, n)
            at += n
        }
    }

    private fun send(cmd: Int, data: ByteArray? = null) {
        ioSize = copyCmd(cmd, data, 0)
        sendBuffer()
    }

    /** Sends a command and returns the reply's payload (the bytes after its 4-byte header). */
    private fun sendRecv(cmd: Int, data: ByteArray?): ByteArray {
        send(cmd, data)
        ioSize = 0
        while (true) {
            if (cancelled()) throw CaptCancelled()
            val n = port.read(chunk, READ_TIMEOUT_MS)
            if (n <= 0) throw CaptException("No reply from the printer to command %04X.".format(cmd))
            System.arraycopy(chunk, 0, io, ioSize, n)
            ioSize += n
            if (ioSize < 6) continue
            if (w(io[0].toInt(), io[1].toInt()) != cmd) {
                throw CaptException("Unexpected reply to %04X: %s".format(cmd, hex(io, minOf(ioSize, 16))))
            }
            val declared = w(io[2].toInt(), io[3].toInt())
            if (declared == ioSize || bcd(io[2].toInt(), io[3].toInt()) == ioSize) break
            if (declared < ioSize) throw CaptException("Reply to %04X is longer than it says: %s".format(cmd, hex(io, minOf(ioSize, 16))))
        }
        log("recv %04x len %d".format(cmd, ioSize))
        return io.copyOfRange(4, ioSize)
    }

    private val chunk = ByteArray(4096)

    private fun bcd(lo: Int, hi: Int): Int {
        val a = (hi shr 4) and 0xF; val b = hi and 0xF; val c = (lo shr 4) and 0xF; val d = lo and 0xF
        return if (a > 9 || b > 9 || c > 9 || d > 9) w(lo, hi) else a * 1000 + b * 100 + c * 10 + d
    }

    private fun hex(b: ByteArray, n: Int) = (0 until n).joinToString(" ") { "%02X".format(b[it]) }

    // --- the job --------------------------------------------------------------------------------------------------------
    private fun jobStart(fg: Int, page: Int) {
        val now = Calendar.getInstance()
        val buf = ByteArray(32 + 40)
        val head = intArrayOf(
            0x00, 0x00, 0x00, 0x00, page and 0xFF, page shr 8 and 0xFF, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            fg, 0x01, job and 0xFF, job shr 8 and 0xFF,
            0xC4, 0xFF,                                    // -60
            0x88, 0xFF,                                    // -120
            (now.get(Calendar.YEAR) - 1900) and 0xFF, (now.get(Calendar.YEAR) - 1900) shr 8 and 0xFF,
            now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH),
            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND),
            0x01,
        )
        for (i in head.indices) buf[i] = head[i].toByte()
        sendRecv(JOB_SETUP, buf)
    }

    /** Introduces the job to the printer (the LBP6000 / LBP6018 sequence). */
    fun jobPrologue() {
        sendRecv(IDENT, null)
        sleepMs(1000)
        status.word.fill(0); status.pageDecoding = 0; status.pagePrinting = 0; status.pageOut = 0; status.pageCompleted = 0; status.pageReceived = 0
        getXStatusOnly()
        sendRecv(START_0, null)
        val begin = sendRecv(JOB_BEGIN, MAGIC_0)
        if (begin.size < 4) throw CaptException("The printer's job-begin reply was too short.")
        job = w(begin[2].toInt(), begin[3].toInt())
        sendRecv(GPIO, GPIO_INIT)
        waitXReadyOnly()
        sendRecv(LBP6000_SETUP_0, byteArrayOf(0x01, 0x00))
        waitXReadyOnly()
        jobStart(1, 0)
        waitXReadyOnly()
    }

    /** Page geometry. All sizes are in 600 dpi pixels except [lineSize], which is bytes per line as the printer wants it. */
    class Page(
        val sizeCode: Int, val paperWidth: Int, val paperHeight: Int, val lineSize: Int, val numLines: Int,
        val marginHeight: Int = 13, val marginWidth: Int = 13, val mediaType: Int = 1, val mediaAdapt: Int = 0x11,
        val tonerSave: Int = 0, val darkness: Int = 7,
    )

    fun pagePrologue(page: Page) {
        val fm = when (page.mediaType) { 0, 1, 2 -> 0x01; 3 -> 0x02; 4 -> 0x13; 5 -> 0x14; 6 -> 0x1C; else -> 0x01 }
        fun lo(v: Int) = v and 0xFF
        fun hi(v: Int) = (v shr 8) and 0xFF
        val params = intArrayOf(
            0x00, 0x00, 0x30, 0x2A, page.sizeCode, 0x00, 0x00, 0x00,
            page.darkness shl 2, 0x1C, 0x1C, 0x1C, page.mediaType, page.mediaAdapt, 0x04, 0x00,
            0x01, 0x01, 0x02, page.tonerSave, 0x00, 0x00,
            lo(page.marginHeight), hi(page.marginHeight), lo(page.marginWidth), hi(page.marginWidth),
            lo(page.lineSize), hi(page.lineSize), lo(page.numLines), hi(page.numLines),
            lo(page.paperWidth), hi(page.paperWidth), lo(page.paperHeight), hi(page.paperHeight),
            0x00, 0x00, fm, 0x00, 0x00, 0x00,
        ).let { a -> ByteArray(a.size) { a[it].toByte() } }

        val st = getXStatusOnly()
        if (st.uninit) {
            sendRecv(START_1, null)
            sendRecv(START_2, null)
            sendRecv(START_3, null)
            waitXReadyOnly()
            sendRecv(UPLOAD_2, MAGIC_2)
            waitXReadyOnly()
        }
        var tries = 0
        while (getXStatusOnly().bufferFull) pause("the printer's buffer stayed full", ++tries)

        // SET_PARMS carries four sub-commands in one packet
        var at = 4
        at = copyCmd(SET_PARM_PAGE, params, at)
        at = copyCmd(SET_PARM_HISCOA, HiscoaParams().format(), at)
        at = copyCmd(SET_PARM_1, null, at)
        at = copyCmd(SET_PARM_2, null, at)
        io[0] = SET_PARMS.toByte(); io[1] = (SET_PARMS shr 8).toByte(); io[2] = at.toByte(); io[3] = (at shr 8).toByte()
        ioSize = at
        sendBuffer()
    }

    private var sends = 0

    /** Sends one compressed band, in commands of at most 0xFF00 bytes; every 16th waits until the printer is not busy. */
    fun sendBand(data: ByteArray) {
        var at = 0
        while (at < data.size) {
            val n = minOf(0xFF00, data.size - at)
            sends++
            if (sends % 16 == 0) waitReady()
            send(PRINT_DATA, data.copyOfRange(at, at + n))
            at += n
        }
    }

    fun startPageData() { sends = 0 }

    /** Ends the page's data and waits until the printer has printed it. Returns false if it ran out of paper. */
    fun pageEpilogue(): Boolean {
        send(PRINT_DATA_END, null)
        var tries = 0
        while (true) {
            sleepMs(1000)
            val st = getXStatusOnly()
            if (st.pageReceived == st.pageDecoding) break
            if (++tries > 180) throw CaptException("Timed out: the printer did not take the page.")
            if (cancelled()) throw CaptCancelled()
        }
        val decoding = status.pageDecoding
        jobStart(2, decoding)
        waitXReadyOnly()
        sendRecv(FIRE, byteArrayOf((decoding and 0xFF).toByte(), (decoding shr 8).toByte()))
        waitXReadyOnly()
        jobStart(6, decoding)
        tries = 0
        while (true) {
            val st = getXStatusOnly()
            if (st.pageOut == st.pageDecoding) return true
            if (st.noPaper1 || st.noPaper2) {
                if (st.printing || st.processing1) { pause("the printer kept printing", ++tries); continue }
                return false
            }
            pause("the page did not come out", ++tries, 300)
        }
    }

    fun jobEpilogue() {
        var tries = 0
        while (true) {
            val st = getXStatusOnly()
            if (st.pageCompleted == st.pageDecoding) {
                jobStart(4, st.pageCompleted)
                break
            }
            pause("the printer did not finish", ++tries)
        }
        sendRecv(JOB_END, byteArrayOf((job and 0xFF).toByte(), (job shr 8 and 0xFF).toByte()))
    }

    /** Leaves the printer idle after a cancelled or failed job; errors here are ignored. */
    fun cancelCleanup() {
        runCatching {
            val st = getXStatusOnly()
            sendRecv(GPIO, GPIO_INIT)
            jobStart(4, st.pageCompleted)
            sendRecv(JOB_END, byteArrayOf((job and 0xFF).toByte(), (job shr 8 and 0xFF).toByte()))
        }
    }

    class CaptCancelled : IOException("cancelled")

    private companion object {
        const val READ_TIMEOUT_MS = 15_000
        const val IDENT = 0xA1A1
        const val CHKXSTATUS = 0xA0A8
        const val JOB_BEGIN = 0xA2A0
        const val START_0 = 0xA3A2
        const val PRINT_DATA = 0xC0A0
        const val PRINT_DATA_END = 0xC0A4
        const val SET_PARM_PAGE = 0xD0A0
        const val SET_PARM_1 = 0xD0A1
        const val SET_PARM_2 = 0xD0A2
        const val SET_PARM_HISCOA = 0xD0A4
        const val SET_PARMS = 0xD0A9
        const val CHKSTATUS = 0xE0A0
        const val START_2 = 0xE0A2
        const val START_1 = 0xE0A3
        const val START_3 = 0xE0A4
        const val UPLOAD_2 = 0xE0A5
        const val FIRE = 0xE0A7
        const val JOB_END = 0xE0A9
        const val LBP6000_SETUP_0 = 0xE0BA
        const val JOB_SETUP = 0xE1A1
        const val GPIO = 0xE1A2

        val MAGIC_0 = byteArrayOf(0x00, 0x00, 0x1E, 0x00, 0x00, 0x00, 0x00, 0x00)
        val MAGIC_2 = byteArrayOf(0xEE.toByte(), 0xDB.toByte(), 0xEA.toByte(), 0xAD.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val GPIO_INIT = byteArrayOf(0x13, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    }
}
