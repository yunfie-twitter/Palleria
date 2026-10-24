package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Bounded SAF import for the public §7.12.5 receipt-and-witness archive. */
internal object PallaSyncDeletionEvidenceArchive {
    const val MAX_BYTES = 262_144
    const val MAX_PROTOCOL_BODY_BYTES = 8_388_608

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

    /** Decode an imported evidence archive without replacing malformed UTF-8 with U+FFFD. */
    fun decodeUtf8(bytes: ByteArray): String =
        Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

    /** Reject duplicate keys and other non-protocol JSON before kotlinx.serialization builds a tree. */
    fun parseStrictObject(text: String): JsonObject {
        check(PallaSyncCore.canonicalJson(text) != null) { "C_SCHEMA_VIOLATION" }
        return try {
            Json.parseToJsonElement(text).jsonObject
        } catch (_: IllegalArgumentException) {
            throw IllegalStateException("C_SCHEMA_VIOLATION")
        }
    }

    private const val BUFFER_SIZE = 8_192
}
