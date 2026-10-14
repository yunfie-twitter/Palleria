package com.yunfie.illustia.pallasync

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Bounded SAF import for the public §7.12.5 receipt-and-witness archive. */
internal object PallaSyncDeletionEvidenceArchive {
    const val MAX_BYTES = 262_144

    fun readBounded(
        input: InputStream,
        maximumBytes: Int,
    ): ByteArray {
        require(maximumBytes > 0) { "C_SCHEMA_VIOLATION" }
        val output = ByteArrayOutputStream(minOf(maximumBytes, BUFFER_SIZE))
        val buffer = ByteArray(minOf(maximumBytes, BUFFER_SIZE))
        while (true) {
            val remaining = maximumBytes - output.size()
            val requested = minOf(buffer.size, remaining + 1)
            val read = input.read(buffer, 0, requested)
            if (read < 0) return output.toByteArray()
            if (read == 0) {
                val next = input.read()
                if (next < 0) return output.toByteArray()
                check(output.size() < maximumBytes) { "C_SCHEMA_VIOLATION" }
                output.write(next)
                continue
            }
            check(read <= remaining) { "C_SCHEMA_VIOLATION" }
            output.write(buffer, 0, read)
        }
    }

    private const val BUFFER_SIZE = 8_192
}
