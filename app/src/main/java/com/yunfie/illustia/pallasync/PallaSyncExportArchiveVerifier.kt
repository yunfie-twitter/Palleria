package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64

/** Streaming §17.4 footer and line-integrity check; signatures and AEAD remain separate gates. */
internal object PallaSyncExportArchiveVerifier {
    data class Header(
        val chainId: String,
        val generation: Long,
        val logId: String,
        val includeRecovery: Boolean,
        val complete: Boolean,
    )

    private const val MAX_ARCHIVE_LINE_BYTES = 8_388_608
    private const val HASH_BYTES = 32
    private val HASH_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")

    fun verify(input: InputStream): Header {
        val source = BufferedInputStream(input)
        val digest = MessageDigest.getInstance("SHA-256")
        var lineCount = 0L
        var header: kotlinx.serialization.json.JsonObject? = null
        var previousLine: String? = null
        while (true) {
            val line = readLine(source) ?: error("C_SCHEMA_VIOLATION")
            val value = PallaSyncExportArchive.canonicalObject(line)
            val kind = value["kind"]?.jsonPrimitive?.content
            if (header == null) {
                check(kind == "header") { "C_SCHEMA_VIOLATION" }
                PallaSyncExportArchive.validateHeader(value)
                header = value
                previousLine = line
                continue
            }
            if (kind == "footer") {
                check(value.keys == setOf("archive_sha256", "complete", "kind", "line_count")) { "C_SCHEMA_VIOLATION" }
                digest.update((checkNotNull(previousLine) + "\n").toByteArray(Charsets.UTF_8))
                lineCount = Math.addExact(lineCount, 1L)
                val complete = value["complete"]?.jsonPrimitive?.booleanOrNull ?: error("C_SCHEMA_VIOLATION")
                val includeRecovery = checkNotNull(header["include_recovery"]?.jsonPrimitive?.booleanOrNull)
                val hash = value["archive_sha256"]?.jsonPrimitive?.content ?: error("C_SCHEMA_VIOLATION")
                check(hash.matches(HASH_PATTERN)) { "C_SCHEMA_VIOLATION" }
                val decodedHash = Base64.getUrlDecoder().decode(hash)
                check(decodedHash.size == HASH_BYTES && MessageDigest.isEqual(digest.digest(), decodedHash)) {
                    "C_SCHEMA_VIOLATION"
                }
                check(value["line_count"]?.jsonPrimitive?.longOrNull == lineCount) { "C_SCHEMA_VIOLATION" }
                check(includeRecovery || !complete) { "C_SCHEMA_VIOLATION" }
                check(source.read() == -1) { "C_SCHEMA_VIOLATION" }
                return Header(
                    chainId = header.getValue("chain_id").jsonPrimitive.content,
                    generation = header.getValue("generation").jsonPrimitive.long,
                    logId = header.getValue("log_id").jsonPrimitive.content,
                    includeRecovery = includeRecovery,
                    complete = complete,
                )
            }
            check(kind in PallaSyncExportArchive.EXPORT_LINE_KINDS) { "C_SCHEMA_VIOLATION" }
            val bytes = (checkNotNull(previousLine) + "\n").toByteArray(Charsets.UTF_8)
            digest.update(bytes)
            lineCount = Math.addExact(lineCount, 1L)
            previousLine = line
        }
    }

    private fun readLine(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next < 0) {
                check(bytes.size() == 0) { "C_SCHEMA_VIOLATION" }
                return null
            }
            if (next == '\n'.code) break
            check(next != '\r'.code && bytes.size() < MAX_ARCHIVE_LINE_BYTES) { "C_SCHEMA_VIOLATION" }
            bytes.write(next)
        }
        check(bytes.size() > 0) { "C_SCHEMA_VIOLATION" }
        return Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes.toByteArray()))
            .toString()
    }
}
