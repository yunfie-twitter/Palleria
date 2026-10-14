package com.yunfie.illustia.pallasync

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Bounded UTF-8 reader/writer for the existing local device-enrollment transfer file (§18.1). */
internal object PallaSyncPeerTransferFile {
    const val MAX_BYTES = 4_194_304

    @Throws(CharacterCodingException::class)
    fun encode(value: String): ByteArray =
        Charsets.UTF_8
            .newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(value))
            .let { buffer -> ByteArray(buffer.remaining()).also(buffer::get) }
            .also { check(it.size <= MAX_BYTES) { "C_SCHEMA_VIOLATION" } }

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
