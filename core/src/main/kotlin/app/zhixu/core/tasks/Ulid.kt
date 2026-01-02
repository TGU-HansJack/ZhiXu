package app.zhixu.core.tasks

import java.security.SecureRandom
import java.time.Instant

/**
 * Minimal ULID generator (26-char Crockford Base32).
 * Spec: https://github.com/ulid/spec
 */
object Ulid {
    private val random = SecureRandom()
    private val encoding = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun next(now: Instant = Instant.now()): String {
        val time = now.toEpochMilli()
        require(time >= 0) { "ULID time must be non-negative" }

        val bytes = ByteArray(16)
        // 48-bit timestamp (ms)
        bytes[0] = ((time ushr 40) and 0xFF).toByte()
        bytes[1] = ((time ushr 32) and 0xFF).toByte()
        bytes[2] = ((time ushr 24) and 0xFF).toByte()
        bytes[3] = ((time ushr 16) and 0xFF).toByte()
        bytes[4] = ((time ushr 8) and 0xFF).toByte()
        bytes[5] = (time and 0xFF).toByte()
        // 80-bit randomness
        random.nextBytes(bytes, 6, 10)

        return encodeBase32(bytes)
    }

    private fun SecureRandom.nextBytes(out: ByteArray, offset: Int, length: Int) {
        val tmp = ByteArray(length)
        nextBytes(tmp)
        tmp.copyInto(out, destinationOffset = offset)
    }

    private fun encodeBase32(bytes: ByteArray): String {
        require(bytes.size == 16)

        val chars = CharArray(26)
        var buffer = 0
        var bitsLeft = 0
        var index = 0

        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5 && index < chars.size) {
                val value = (buffer ushr (bitsLeft - 5)) and 0x1F
                bitsLeft -= 5
                chars[index++] = encoding[value]
            }
        }

        if (index != chars.size) {
            // 128 bits should always encode to 26 chars (130 bits with padding of 2 zero bits).
            while (index < chars.size) chars[index++] = encoding[0]
        }

        return String(chars)
    }
}

