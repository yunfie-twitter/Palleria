package com.yunfie.illustia.pallasync

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Bounded UTF-8 reader/writer for the existing local device-enrollment transfer file (§18.1). */
internal object PallaSyncPeerTransferFile {
    const val MAX_BYTES = 4_194_304

    /** Builds the selected public-history file while enforcing the same bounded transfer limit as its reader. */
    fun encodeHistory(
        parameters: String,
        authority: String,
        items: Iterable<String>,
    ): String {
        val prefix = "{\"format\":\"pallasync-peer-history/3\",\"parameters\":$parameters,\"authority\":$authority,\"items\":["
        val suffix = "]}"
        val output = StringBuilder()
        output.append(prefix)
        var bytes = utf8Size(prefix).toLong() + utf8Size(suffix)
        var first = true
        for (item in items) {
            val separator = if (first) "" else ","
            bytes += utf8Size(separator).toLong() + utf8Size(item)
            check(bytes <= MAX_BYTES) { "C_LOCAL_LIMIT" }
            output.append(separator).append(item)
            first = false
        }
        output.append(suffix)
        return output.toString().also { check(utf8Size(it) <= MAX_BYTES) { "C_LOCAL_LIMIT" } }
    }

    @Throws(CharacterCodingException::class)
    fun encode(value: String): ByteArray =
        Charsets.UTF_8
            .newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(value))
            .let { buffer -> ByteArray(buffer.remaining()).also(buffer::get) }
            .also { check(it.size <= MAX_BYTES) { "C_SCHEMA_VIOLATION" } }

    @Throws(CharacterCodingException::class)
    fun utf8Size(value: String): Int =
        Charsets.UTF_8
            .newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(value))
            .remaining()

    fun read(input: InputStream): String {
        val bytes = PallaSyncDeletionEvidenceArchive.readBounded(input, MAX_BYTES)
        return Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }
}
