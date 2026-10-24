package com.yunfie.illustia.pallasync

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Canonical, bounded local-medium encoding for §7.12.4 DeviceCheckpoint objects. */
internal object PallaSyncCheckpointFile {
    const val MAX_BYTES = 16_384

    fun encode(checkpointJson: String): ByteArray {
        val canonical = checkNotNull(PallaSyncCore.canonicalJson(checkpointJson)) { "C_SCHEMA_VIOLATION" }
        check(canonical == checkpointJson) { "C_SCHEMA_VIOLATION" }
        val bytes = strictRootUtf8(checkpointJson)
        check(bytes.size <= MAX_BYTES) { "C_LOCAL_LIMIT" }
        return bytes
    }

    fun write(
        output: OutputStream,
        checkpointJson: String,
    ) {
        val bytes = encode(checkpointJson)
        output.write(bytes)
        output.flush()
    }

    fun read(input: InputStream): String {
        val bytes = PallaSyncDeletionEvidenceArchive.readBounded(input, MAX_BYTES)
        val text =
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        encode(text)
        return text
    }
}
