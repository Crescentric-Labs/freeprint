package crescentric.freeprint.otg.print

/*
 * Hi-SCoA band compression for Canon CAPT printers.
 *
 * This is a Kotlin port of hiscoa-compress.c and hiscoa-common.c from captdriver
 * (https://github.com/mounaiban/captdriver), Copyright (C) 2013 Alexey Galakhov <agalakhov@gmail.com>,
 * licensed under the GNU General Public License, version 3 or (at your option) any later version.
 * It is used here under the GNU General Public License version 3.
 */

/** The five small numbers that tell the printer how to read a compressed band; the driver's defaults. */
data class HiscoaParams(val origin3: Int = 1, val origin5: Int = 4, val origin0: Int = 0, val origin2: Int = -7, val origin4: Int = 0) {
    /** The 8 bytes of the "Hi-SCoA parameters" command. */
    fun format(): ByteArray = byteArrayOf(
        origin3.toByte(), origin5.toByte(), 0x01, 0x01, origin0.toByte(), origin2.toByte(),
        (origin4 and 0xFF).toByte(), ((origin4 shr 8) and 0xFF).toByte(),
    )
}

object Hiscoa {
    private const val XOR = 0x43

    /**
     * Compresses [nLines] rows of [lineSize] bytes from [band] (1 = black). The result is a whole number of 32-bit words.
     * [last] marks the end of the page's data, which the driver never sets (every band is sent as a normal one).
     */
    fun compressBand(band: ByteArray, lineSize: Int, nLines: Int, params: HiscoaParams = HiscoaParams(), last: Boolean = false): ByteArray =
        Encoder(band, lineSize, lineSize * nLines, params).run(last)

    private class Encoder(val input: ByteArray, val lineSize: Int, val inputSize: Int, p: HiscoaParams) {
        private var out = ByteArray(maxOf(64, inputSize / 4))
        private var bitPos = 0
        private var inPos = 0
        private var nBytes = 0
        private val bytes = IntArray(16)
        private val origin = intArrayOf(p.origin0 + lineSize, 0, p.origin2 + lineSize, p.origin3, p.origin4, p.origin5)

        fun run(last: Boolean): ByteArray {
            while (inPos < inputSize) {
                if (tryLongRepeat()) continue
                if (tryByteRepeat()) continue
                writeSimpleByte()
            }
            push(0xFE, 8)                              // end
            push(if (last) 1 else 0, 2)
            push(-1, 32 - (bitPos % 32))               // pad to a 32-bit boundary (a whole extra word if already on one)
            val size = bitPos / 8
            return ByteArray(size) { (out[it].toInt() xor XOR).toByte() }
        }

        /** Appends the low [count] bits of [bits], most significant first. */
        private fun push(bits: Int, count: Int) {
            if (((bitPos + count) shr 3) + 1 > out.size) out = out.copyOf(maxOf(out.size * 2, ((bitPos + count) shr 3) + 8))
            var left = count
            while (left > 0) {
                val idx = bitPos shr 3
                val space = 8 - (bitPos and 7)
                val take = minOf(left, space)
                val mask = (0xFF ushr (8 - take)) shl (space - take)
                val value = (bits ushr (left - take)) shl (space - take)
                out[idx] = ((out[idx].toInt() and mask.inv()) or (mask and value)).toByte()
                bitPos += take
                left -= take
            }
        }

        private fun tryMatch(diff: Int): Int {
            var pos = inPos
            if (pos < diff) return 0
            while (input[pos] == input[pos - diff]) {
                pos++
                if (pos >= inputSize) break
                if (pos >= inPos + 512 + 127) break
                if (pos % lineSize == 0) break
            }
            return pos - inPos
        }

        private fun findMsb(v: Int): Int {
            if (v == 0) return 0
            var n = 31
            while (v ushr n == 0) n--
            return n + 1
        }

        private fun tryLongRepeat(): Boolean {
            var bestCmd = 0
            var bestLen = 0
            for (cmd in 0 until 6) {
                if (origin[cmd] != 0) {
                    val len = tryMatch(origin[cmd])
                    if (len > bestLen) { bestCmd = cmd; bestLen = len }
                }
            }
            if (bestLen <= 1) return false
            var len = bestLen
            if (len > 127) {
                val v = len / 128
                val nbits = findMsb(v) - 1
                push(0xFC, 8)
                push(nbits, 2)
                push(v.inv(), nbits)
                len %= 128
            }
            push(-2, bestCmd + 1)                      // long repeat
            if (bestCmd == 2) push(0, 1)               // sub-command 0
            when (len) {
                0 -> push(0x3F, 6)
                1 -> push(0x0, 2)
                2 -> push(0x3, 3)
                3 -> push(0x2, 3)
                else -> {
                    val nbits = findMsb(len) - 1
                    push(-2, nbits)
                    push(len.inv(), nbits)
                }
            }
            if (bestCmd == 2) { val t = origin[2]; origin[2] = origin[0]; origin[0] = t }
            if (bestCmd == 5) { val t = origin[5]; origin[5] = origin[3]; origin[3] = t }
            inPos += bestLen
            return true
        }

        private fun tryByteRepeat(): Boolean {
            val byte = input[inPos].toInt() and 0xFF
            for (i in 0 until nBytes) {
                if (byte == bytes[i]) {
                    for (j in i downTo 1) bytes[j] = bytes[j - 1]
                    bytes[0] = byte
                    push(0x20 or ((15 - i) and 0xF), 6)
                    inPos++
                    return true
                }
            }
            return false
        }

        private fun writeSimpleByte() {
            val byte = input[inPos].toInt() and 0xFF
            if (nBytes < 16) nBytes++
            for (i in nBytes - 1 downTo 1) bytes[i] = bytes[i - 1]
            bytes[0] = byte
            if (byte == 0) push(0xFD, 8) else push(0xD00 or byte, 12)
            inPos++
        }
    }
}
