package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import java.io.Closeable
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64

/** §17.4/§17.10 bounded JSONL chunk merger. It preserves signed source lines verbatim. */
internal class PallaSyncExportArchive private constructor(
    private val output: OutputStream,
    private val expectedSource: ExpectedSource?,
) : Closeable {
    data class ExpectedSource(
        val chainId: String,
        val generation: Long,
        val logId: String,
        val relayPublicKey: String,
        val relayKeyId: String,
        val includeRecovery: Boolean = false,
        val relayPinJson: String? = null,
    )

    data class Page(
        val cursor: String?,
        val hasMore: Boolean,
    )

    private val digest = MessageDigest.getInstance("SHA-256")
    private val seenCursors = mutableSetOf<String>()
    private var headerLine: String? = null
    private var lineCount = 0L
    private var finished = false
    private var failed = false
    private var expectedCursor: String? = null
    private var hasMore = true
    private var transparencyIndex = 0L
    private var transparencyHash: String? = null
    private var transparencyHeadVerified = false

    @Suppress("TooGenericExceptionCaught") // Every failure after a partial write must poison the archive.
    fun acceptChunk(
        bytes: ByteArray,
        requestedCursor: String?,
    ): Page =
        try {
            acceptChunkInner(bytes, requestedCursor)
        } catch (failure: Throwable) {
            failed = true
            throw failure
        }

    private fun acceptChunkInner(
        bytes: ByteArray,
        requestedCursor: String?,
    ): Page {
        check(!finished && !failed) { "C_SCHEMA_VIOLATION" }
        check(requestedCursor == expectedCursor) { "C_CURSOR_INVALID" }
        val lines = parseChunkLines(bytes)
        acceptHeader(lines.first())
        val page = parseContinuation(lines.last())
        writeItems(lines)
        expectedCursor = page.cursor
        hasMore = page.hasMore
        return page
    }

    private fun parseChunkLines(bytes: ByteArray): List<String> {
        check(bytes.isNotEmpty() && bytes.last() == '\n'.code.toByte() && bytes.none { it == '\r'.code.toByte() }) {
            "C_SCHEMA_VIOLATION"
        }
        val text =
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        return text.dropLast(1).split('\n').also {
            check(it.size >= 2 && it.none(String::isEmpty)) { "C_SCHEMA_VIOLATION" }
        }
    }

    private fun acceptHeader(line: String) {
        val header = canonicalObject(line)
        validateHeader(header)
        expectedSource?.let { validateExpectedSource(header, it) }
        val previous = headerLine
        if (previous == null) {
            headerLine = line
            expectedSource?.relayPinJson?.let { pin ->
                transparencyHash =
                    PallaSyncCore.initialExportTransparencyHash(
                        header.getValue("transparency_id").jsonPrimitive.content,
                        pin,
                    )
            }
            writeArchiveLine(line)
        } else {
            check(previous == line) { "C_ROLLBACK_DETECTED" }
        }
    }

    private fun parseContinuation(line: String): Page {
        val trailer = canonicalObject(line)
        check(trailer.keys == setOf("kind", "next_cursor", "has_more") && trailer["kind"]?.jsonPrimitive?.content == "continuation") {
            "C_SCHEMA_VIOLATION"
        }
        val hasMore = trailer["has_more"]?.jsonPrimitive?.booleanOrNull ?: error("C_SCHEMA_VIOLATION")
        val cursorValue = trailer["next_cursor"] ?: error("C_SCHEMA_VIOLATION")
        val cursor =
            if (cursorValue.toString() == "null") {
                null
            } else {
                check(cursorValue.jsonPrimitive.toString().startsWith('"')) { "C_SCHEMA_VIOLATION" }
                cursorValue.jsonPrimitive.content
            }
        check((hasMore && !cursor.isNullOrBlank()) || (!hasMore && cursor == null)) { "C_SCHEMA_VIOLATION" }
        if (cursor != null) check(seenCursors.add(cursor)) { "C_CURSOR_INVALID" }
        return Page(cursor, hasMore)
    }

    private fun writeItems(lines: List<String>) {
        lines.subList(1, lines.lastIndex).forEach { line ->
            val value = canonicalObject(line)
            check(expectedSource?.relayPinJson == null || !transparencyHeadVerified) { "C_TRANSPARENCY_INVALID" }
            validateExportLine(value)
            validateRelayAttestation(value, line)
            writeArchiveLine(line)
        }
    }

    private fun validateRelayAttestation(
        value: kotlinx.serialization.json.JsonObject,
        line: String,
    ) {
        val pin = expectedSource?.relayPinJson ?: return
        val kind = value["kind"]?.jsonPrimitive?.content
        if (kind in RELAY_SIGNED_EXPORT_KINDS) {
            check(PallaSyncCore.validateExportAttestation(line, pin)) { "C_SIGNATURE_INVALID" }
        }
        if (kind == "transparency_entry") {
            val header = checkNotNull(headerLine).let(::canonicalObject)
            val entry = value.getValue("entry").jsonObject
            check(entry["transparency_id"] == header["transparency_id"]) { "C_TRANSPARENCY_INVALID" }
            transparencyHash =
                PallaSyncCore.advanceExportTransparency(
                    line,
                    pin,
                    transparencyIndex,
                    transparencyHash.orEmpty(),
                )
            transparencyIndex = Math.addExact(transparencyIndex, 1L)
        }
        if (kind == "transparency_head") {
            val head = value.getValue("head").jsonObject
            val header = checkNotNull(headerLine).let(::canonicalObject)
            check(
                head["transparency_id"] == header["transparency_id"] &&
                    head["entry_index"] == header["transparency_head_index"] &&
                    head["transparency_hash"] == header["transparency_head_hash"] &&
                    head["head_seq"] == header["head_seq"] &&
                    head["head_hash"] == header["head_hash"],
            ) { "C_TRANSPARENCY_INVALID" }
            check(
                transparencyIndex == header.getValue("transparency_head_index").jsonPrimitive.long &&
                    transparencyHash == header.getValue("transparency_head_hash").jsonPrimitive.content,
            ) { "C_TRANSPARENCY_INVALID" }
            transparencyHeadVerified = true
        }
    }

    fun finish() {
        check(!finished && !failed && headerLine != null && !hasMore) { "C_SCHEMA_VIOLATION" }
        if (expectedSource?.relayPinJson != null) {
            check(transparencyHeadVerified) { "C_MISSING_DEPENDENCY" }
        }
        try {
            val footer =
                """{"archive_sha256":"${Base64.getUrlEncoder().withoutPadding().encodeToString(
                    digest.digest(),
                )}","complete":false,"kind":"footer","line_count":$lineCount}"""
            val canonicalFooter = checkNotNull(PallaSyncCore.canonicalJson(footer))
            val bytes = canonicalFooter.toByteArray(Charsets.UTF_8) + '\n'.code.toByte()
            output.write(bytes)
            output.flush()
        } finally {
            finished = true
            output.close()
        }
    }

    private fun writeArchiveLine(line: String) {
        val bytes = line.toByteArray(Charsets.UTF_8) + '\n'.code.toByte()
        output.write(bytes)
        digest.update(bytes)
        lineCount = Math.addExact(lineCount, 1)
    }

    override fun close() {
        if (!finished) {
            finished = true
            output.close()
        }
    }

    companion object {
        internal val EXPORT_HEADER_FIELDS =
            setOf(
                "kind",
                "protocol",
                "chain_id",
                "generation",
                "log_id",
                "head_seq",
                "head_hash",
                "include_recovery",
                "relay_public_key",
                "relay_key_id",
                "transparency_id",
                "transparency_head_index",
                "transparency_head_hash",
            )
        internal val EXPORT_LINE_KINDS =
            setOf(
                "log",
                "snapshot_part",
                "compaction",
                "boundary",
                "removed_id",
                "source_receipt",
                "control",
                "omitted",
                "upload_receipt",
                "transparency_entry",
                "transparency_head",
            )
        private val HEADER_STRING_FIELDS =
            setOf("chain_id", "log_id", "head_hash", "relay_public_key", "relay_key_id", "transparency_id", "transparency_head_hash")
        private val HEADER_INTEGER_FIELDS = setOf("head_seq", "transparency_head_index")
        private val B64U_32_FIELDS = setOf("chain_id", "head_hash", "relay_public_key", "relay_key_id", "transparency_head_hash")
        private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
        private const val MAX_GENERATION = 4_294_967_295L
        private const val UUID_VERSION_4 = 4
        private const val UUID_VARIANT_RFC = 2
        private val B64U_32 = Regex("^[A-Za-z0-9_-]{43}$")
        private val CONTROL_OBJECT_TYPES = setOf("parameters", "device", "epoch", "envelope", "admin")
        private val RELAY_SIGNED_EXPORT_KINDS = setOf("upload_receipt", "transparency_entry", "transparency_head")
        private val EXPORT_LINE_VALIDATORS: Map<String, (kotlinx.serialization.json.JsonObject) -> Boolean> =
            mapOf(
                "log" to { line -> objectPayload(line, "entry") },
                "source_receipt" to { line -> objectPayload(line, "receipt") },
                "upload_receipt" to { line -> objectPayload(line, "receipt") },
                "snapshot_part" to { line -> objectPayload(line, "record") },
                "compaction" to { line -> objectPayload(line, "compaction") },
                "boundary" to { line -> objectPayload(line, "compaction") },
                "removed_id" to ::validRemovedId,
                "control" to ::validControl,
                "omitted" to ::validOmission,
                "transparency_entry" to { line -> objectPayload(line, "entry") },
                "transparency_head" to { line -> objectPayload(line, "head") },
            )

        internal fun canonicalObject(line: String): kotlinx.serialization.json.JsonObject {
            check(line.isNotBlank() && PallaSyncCore.canonicalJson(line) == line) { "C_SCHEMA_VIOLATION" }
            return Json.parseToJsonElement(line).jsonObject
        }

        internal fun validateHeader(value: kotlinx.serialization.json.JsonObject) {
            check(
                value.keys == EXPORT_HEADER_FIELDS && value["kind"]?.jsonPrimitive?.content == "header" &&
                    value["protocol"]?.jsonPrimitive?.content == "pallasync/3" &&
                    value["include_recovery"]?.jsonPrimitive?.booleanOrNull != null &&
                    HEADER_STRING_FIELDS.all { value[it]?.jsonPrimitive?.toString()?.startsWith('"') == true } &&
                    HEADER_INTEGER_FIELDS.all { value[it]?.jsonPrimitive?.longOrNull in 0..MAX_SAFE_INTEGER } &&
                    value["generation"]?.jsonPrimitive?.longOrNull in 0..MAX_GENERATION &&
                    uuid4(value.getValue("log_id").jsonPrimitive.content) &&
                    uuid4(value.getValue("transparency_id").jsonPrimitive.content) &&
                    B64U_32_FIELDS.all { value[it]?.jsonPrimitive?.content?.matches(B64U_32) == true },
            ) { "C_SCHEMA_VIOLATION" }
        }

        /** Validate the archive wrapper shape without treating local hashes as relay signatures. */
        internal fun validateExportLine(value: kotlinx.serialization.json.JsonObject) {
            val kind = value["kind"]?.jsonPrimitive?.content ?: error("C_SCHEMA_VIOLATION")
            check(EXPORT_LINE_VALIDATORS[kind]?.invoke(value) == true) { "C_SCHEMA_VIOLATION" }
        }

        private fun objectPayload(
            line: kotlinx.serialization.json.JsonObject,
            name: String,
        ): Boolean = line.keys == setOf("kind", name) && line[name] is kotlinx.serialization.json.JsonObject

        private fun validRemovedId(line: kotlinx.serialization.json.JsonObject): Boolean =
            line.keys == setOf("kind", "record_id", "record_hash") &&
                line["record_id"]?.jsonPrimitive?.content?.let(::uuid4) == true &&
                line["record_hash"]?.jsonPrimitive?.content?.matches(B64U_32) == true

        private fun validControl(line: kotlinx.serialization.json.JsonObject): Boolean =
            line.keys == setOf("kind", "object_type", "object") &&
                line["object_type"]?.jsonPrimitive?.content in CONTROL_OBJECT_TYPES &&
                line["object"] is kotlinx.serialization.json.JsonObject

        private fun validOmission(line: kotlinx.serialization.json.JsonObject): Boolean =
            line.keys == setOf("kind", "relay_seq", "object_hash", "reason") &&
                line["relay_seq"]?.jsonPrimitive?.longOrNull in 1..MAX_SAFE_INTEGER &&
                line["object_hash"]?.jsonPrimitive?.content?.matches(B64U_32) == true &&
                line["reason"]?.jsonPrimitive?.content == "recovery_authority"

        internal fun validateExpectedSource(
            header: kotlinx.serialization.json.JsonObject,
            expected: ExpectedSource,
        ) {
            check(
                header["chain_id"]?.jsonPrimitive?.content == expected.chainId &&
                    header["generation"]?.jsonPrimitive?.longOrNull == expected.generation &&
                    header["log_id"]?.jsonPrimitive?.content == expected.logId &&
                    header["relay_public_key"]?.jsonPrimitive?.content == expected.relayPublicKey &&
                    header["relay_key_id"]?.jsonPrimitive?.content == expected.relayKeyId &&
                    header["include_recovery"]?.jsonPrimitive?.booleanOrNull == expected.includeRecovery,
            ) { "C_ROLLBACK_DETECTED" }
            expected.relayPinJson?.let { pinJson ->
                val canonicalPin = checkNotNull(PallaSyncCore.canonicalJson(pinJson)) { "C_RELAY_KEY_MISMATCH" }
                val pin = canonicalObject(canonicalPin)
                val expectedKeyId =
                    Base64.getUrlEncoder().withoutPadding().encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(
                            Base64.getUrlDecoder().decode(expected.relayPublicKey),
                        ),
                    )
                check(
                    pin.keys == setOf("origin", "chain_id", "public_key", "log_id") &&
                        pin["chain_id"]?.jsonPrimitive?.content == expected.chainId &&
                        pin["log_id"]?.jsonPrimitive?.content == expected.logId &&
                        pin["public_key"]?.jsonPrimitive?.content == expected.relayPublicKey &&
                        expectedKeyId == expected.relayKeyId,
                ) { "C_RELAY_KEY_MISMATCH" }
            }
        }

        private fun uuid4(value: String): Boolean =
            runCatching { java.util.UUID.fromString(value) }.getOrNull()?.let {
                it.version() == UUID_VERSION_4 && it.variant() == UUID_VARIANT_RFC && it.toString() == value
            } == true

        fun create(
            output: OutputStream,
            expectedSource: ExpectedSource? = null,
        ): PallaSyncExportArchive = PallaSyncExportArchive(output, expectedSource)
    }
}
