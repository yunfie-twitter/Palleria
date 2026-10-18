package com.yunfie.illustia.pallasync

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
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

    fun verify(
        input: InputStream,
        expectedSource: PallaSyncExportArchive.ExpectedSource? = null,
        recordVerifier: PallaSyncArchiveRecordVerifier? = null,
        snapshotVerifier: PallaSyncArchiveSnapshotVerifier? = null,
    ): Header {
        val source = BufferedInputStream(input)
        val digest = MessageDigest.getInstance("SHA-256")
        var lineCount = 0L
        var header: kotlinx.serialization.json.JsonObject? = null
        var previousLine: String? = null
        val canonicalPin = expectedSource?.relayPinJson?.let(PallaSyncCore::canonicalJson)
        val pin = canonicalPin?.let(PallaSyncExportArchive::canonicalObject)
        var transparencyIndex = 0L
        var transparencyHash: String? = null
        var transparencyHeadVerified = false
        var transparencyId: String? = null
        var recoveryObjectsOmitted = false
        var restoreEvidenceUnsupported = false
        val receiptLinks = if (pin != null) ExportReceiptLinkIndex() else null
        try {
            while (true) {
                val line = readLine(source) ?: error("C_SCHEMA_VIOLATION")
                val value = PallaSyncExportArchive.canonicalObject(line)
                val kind = value["kind"]?.jsonPrimitive?.content
                if (header == null) {
                    check(kind == "header") { "C_SCHEMA_VIOLATION" }
                    PallaSyncExportArchive.validateHeader(value)
                    expectedSource?.let { PallaSyncExportArchive.validateExpectedSource(value, it) }
                    if (pin != null) {
                        transparencyId = value.getValue("transparency_id").jsonPrimitive.content
                    }
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
                    if (complete) {
                        check(
                            includeRecovery && !recoveryObjectsOmitted && pin != null &&
                                expectedSource?.identityJson != null && recordVerifier != null && snapshotVerifier != null,
                        ) { "C_SCHEMA_VIOLATION" }
                        check(!restoreEvidenceUnsupported) { "C_MISSING_DEPENDENCY" }
                    }
                    if (pin != null) {
                        check(transparencyHeadVerified) { "C_MISSING_DEPENDENCY" }
                        val expectedTransparencyHash =
                            if (transparencyIndex == 0L) {
                                PallaSyncCore.initialExportTransparencyHash(
                                    header.getValue("transparency_id").jsonPrimitive.content,
                                    checkNotNull(canonicalPin),
                                )
                            } else {
                                transparencyHash
                            }
                        check(
                            transparencyIndex == header.getValue("transparency_head_index").jsonPrimitive.long &&
                                expectedTransparencyHash == header.getValue("transparency_head_hash").jsonPrimitive.content,
                        ) { "C_TRANSPARENCY_INVALID" }
                        checkNotNull(receiptLinks).requireAllMatched()
                        expectedSource?.identityJson?.let { identity ->
                            checkNotNull(receiptLinks).verifyAuthorityHistory(
                                header,
                                identity,
                                checkNotNull(canonicalPin),
                                recordVerifier,
                                snapshotVerifier,
                            )
                        }
                    }
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
                PallaSyncExportArchive.validateExportLine(value)
                if (pin != null) {
                    check(!transparencyHeadVerified) { "C_TRANSPARENCY_INVALID" }
                    if (kind in setOf("upload_receipt", "transparency_entry", "transparency_head")) {
                        check(PallaSyncCore.validateExportAttestation(line, checkNotNull(canonicalPin))) { "C_SIGNATURE_INVALID" }
                    }
                    when (kind) {
                        "upload_receipt" -> {
                            val receipt = value.getValue("receipt").jsonObject
                            checkNotNull(receiptLinks).add(
                                receipt.getValue("transparency_index").jsonPrimitive.long,
                                receipt.toString(),
                            )
                            receiptLinks.addLogReceipt(receipt)
                        }

                        "log" -> {
                            checkNotNull(receiptLinks).addLogEntry(value.getValue("entry").jsonObject)
                        }

                        "snapshot_part" -> {
                            checkNotNull(receiptLinks).addSnapshotPart(value.getValue("record").jsonObject)
                        }

                        "compaction" -> {
                            checkNotNull(receiptLinks).addCompaction(value.getValue("compaction").jsonObject)
                        }

                        "transparency_entry" -> {
                            val entry = value.getValue("entry").jsonObject
                            check(entry["transparency_id"]?.jsonPrimitive?.content == transparencyId) {
                                "C_TRANSPARENCY_INVALID"
                            }
                            transparencyHash =
                                PallaSyncCore.advanceExportTransparency(
                                    line,
                                    checkNotNull(canonicalPin),
                                    transparencyIndex,
                                    transparencyHash.orEmpty(),
                                )
                            checkNotNull(receiptLinks).verifyAcceptedEntry(entry, checkNotNull(canonicalPin))
                            transparencyIndex = Math.addExact(transparencyIndex, 1L)
                        }

                        "transparency_head" -> {
                            val head = value.getValue("head").jsonObject
                            val archiveHeader = checkNotNull(header)
                            val expectedTransparencyHash =
                                if (transparencyIndex == 0L) {
                                    PallaSyncCore.initialExportTransparencyHash(
                                        archiveHeader.getValue("transparency_id").jsonPrimitive.content,
                                        checkNotNull(canonicalPin),
                                    )
                                } else {
                                    transparencyHash
                                }
                            check(
                                head["transparency_id"] == archiveHeader["transparency_id"] &&
                                    head["entry_index"] == archiveHeader["transparency_head_index"] &&
                                    head["transparency_hash"] == archiveHeader["transparency_head_hash"] &&
                                    head["head_seq"] == archiveHeader["head_seq"] &&
                                    head["head_hash"] == archiveHeader["head_hash"] &&
                                    transparencyIndex == archiveHeader.getValue("transparency_head_index").jsonPrimitive.long &&
                                    expectedTransparencyHash ==
                                    archiveHeader.getValue("transparency_head_hash").jsonPrimitive.content,
                            ) { "C_TRANSPARENCY_INVALID" }
                            transparencyHeadVerified = true
                        }

                        "omitted" -> {
                            recoveryObjectsOmitted = true
                        }

                        "control", "boundary", "removed_id", "source_receipt" -> {
                            restoreEvidenceUnsupported = true
                        }
                    }
                }
                val bytes = (checkNotNull(previousLine) + "\n").toByteArray(Charsets.UTF_8)
                digest.update(bytes)
                lineCount = Math.addExact(lineCount, 1L)
                previousLine = line
            }
        } finally {
            receiptLinks?.close()
        }
    }

    /** Disk-backed because a valid export can contain more receipts than fit in memory. */
    private class ExportReceiptLinkIndex : Closeable {
        private val file = File.createTempFile("pallasync-export-receipts-", ".sqlite")
        private val database = SQLiteDatabase.openOrCreateDatabase(file, null)

        init {
            database.execSQL(
                "CREATE TABLE export_receipts (transparency_index INTEGER PRIMARY KEY, receipt_json TEXT NOT NULL)",
            )
            database.execSQL("CREATE TABLE export_log_entries (relay_seq INTEGER PRIMARY KEY, entry_json TEXT NOT NULL)")
            database.execSQL("CREATE TABLE export_log_receipts (relay_seq INTEGER PRIMARY KEY, receipt_json TEXT NOT NULL)")
            database.execSQL(
                "CREATE TABLE export_snapshot_parts (record_id TEXT PRIMARY KEY, record_hash TEXT NOT NULL, record_json TEXT NOT NULL)",
            )
            database.execSQL("CREATE TABLE export_compactions (compaction_hash TEXT PRIMARY KEY)")
        }

        fun add(
            index: Long,
            receipt: String,
        ) {
            check(index > 0) { "C_TRANSPARENCY_INVALID" }
            try {
                database.execSQL(
                    "INSERT INTO export_receipts(transparency_index,receipt_json) VALUES (?,?)",
                    arrayOf<Any>(index, receipt),
                )
            } catch (_: SQLiteConstraintException) {
                error("C_TRANSPARENCY_INVALID")
            }
        }

        fun verifyAcceptedEntry(
            entry: kotlinx.serialization.json.JsonObject,
            pin: String,
        ) {
            if (entry["operation"]?.jsonPrimitive?.content != "accept") return
            val index = entry.getValue("entry_index").jsonPrimitive.long
            val receipt =
                database
                    .rawQuery(
                        "SELECT receipt_json FROM export_receipts WHERE transparency_index=?",
                        arrayOf(index.toString()),
                    ).use { cursor ->
                        check(cursor.moveToFirst()) { "C_ACCEPTANCE_UNPROVEN" }
                        cursor.getString(0)
                    }
            check(
                PallaSyncCore.validateExportReceiptTransparency(receipt, entry.toString(), pin),
            ) { "C_TRANSPARENCY_INVALID" }
            check(
                database.delete("export_receipts", "transparency_index=?", arrayOf(index.toString())) == 1,
            ) { "C_TRANSPARENCY_INVALID" }
        }

        fun addLogEntry(entry: kotlinx.serialization.json.JsonObject) {
            val sequence = entry.getValue("relay_seq").jsonPrimitive.long
            try {
                database.execSQL(
                    "INSERT INTO export_log_entries(relay_seq,entry_json) VALUES (?,?)",
                    arrayOf<Any>(sequence, entry.toString()),
                )
            } catch (_: SQLiteConstraintException) {
                error("C_SCHEMA_VIOLATION")
            }
        }

        fun addLogReceipt(receipt: kotlinx.serialization.json.JsonObject) {
            val sequence = receipt["relay_seq"]?.takeUnless { it.toString() == "null" }?.jsonPrimitive?.long ?: return
            try {
                database.execSQL(
                    "INSERT INTO export_log_receipts(relay_seq,receipt_json) VALUES (?,?)",
                    arrayOf<Any>(sequence, receipt.toString()),
                )
            } catch (_: SQLiteConstraintException) {
                error("C_TRANSPARENCY_INVALID")
            }
        }

        fun addCompaction(compaction: kotlinx.serialization.json.JsonObject) {
            val hash = checkNotNull(PallaSyncCore.objectHash(compaction.toString())) { "C_SCHEMA_VIOLATION" }
            try {
                database.execSQL("INSERT INTO export_compactions(compaction_hash) VALUES (?)", arrayOf<Any>(hash))
            } catch (_: SQLiteConstraintException) {
                error("C_SCHEMA_VIOLATION")
            }
        }

        fun verifyAuthorityHistory(
            header: kotlinx.serialization.json.JsonObject,
            identityJson: String,
            pinJson: String,
            recordVerifier: PallaSyncArchiveRecordVerifier?,
            snapshotVerifier: PallaSyncArchiveSnapshotVerifier?,
        ) {
            check(header.getValue("generation").jsonPrimitive.long == 0L) { "C_SNAPSHOT_INVALID" }
            var authority = PallaSyncCore.beginExportAuthorityHistory(identityJson, pinJson)
            var expectedSequence = 1L
            database.rawQuery("SELECT relay_seq,entry_json FROM export_log_entries ORDER BY relay_seq", null).use { entries ->
                while (entries.moveToNext()) {
                    val sequence = entries.getLong(0)
                    check(sequence == expectedSequence) { "C_MISSING_DEPENDENCY" }
                    val receipt =
                        database
                            .rawQuery(
                                "SELECT receipt_json FROM export_log_receipts WHERE relay_seq=?",
                                arrayOf(sequence.toString()),
                            ).use { receipts ->
                                check(receipts.moveToFirst()) { "C_ACCEPTANCE_UNPROVEN" }
                                receipts.getString(0)
                            }
                    authority =
                        PallaSyncCore.advanceAuthorityHistory(
                            authority,
                            entries.getString(1),
                            receipt,
                        )
                    val entry = Json.parseToJsonElement(entries.getString(1)).jsonObject
                    if (entry.getValue("kind").jsonPrimitive.content == "record") {
                        val record = entry.getValue("object").jsonObject
                        check(PallaSyncCore.validateRecordAuthority(authority, record.toString(), sequence)) {
                            "C_ACCEPTANCE_UNPROVEN"
                        }
                        checkNotNull(recordVerifier) { "C_MISSING_DEPENDENCY" }.verify(
                            authority,
                            record.toString(),
                            sequence,
                        )
                    }
                    if (entry.getValue("kind").jsonPrimitive.content == "compaction") {
                        val compaction = entry.getValue("object").jsonObject
                        val compactionHash = checkNotNull(PallaSyncCore.objectHash(compaction.toString())) { "C_SCHEMA_VIOLATION" }
                        database.delete("export_compactions", "compaction_hash=?", arrayOf(compactionHash))
                        verifyCompactionSnapshotParts(
                            authority,
                            compaction,
                            snapshotVerifier,
                        )
                    }
                    expectedSequence = Math.addExact(expectedSequence, 1L)
                }
            }
            val finalAuthority = Json.parseToJsonElement(authority).jsonObject
            val lastSequence = expectedSequence - 1L
            check(
                lastSequence == header.getValue("head_seq").jsonPrimitive.long &&
                    finalAuthority.getValue("sequence").jsonPrimitive.long == lastSequence &&
                    finalAuthority.getValue("head_hash").jsonPrimitive.content ==
                    header.getValue("head_hash").jsonPrimitive.content &&
                    finalAuthority
                        .getValue("pin")
                        .jsonObject
                        .getValue("log_id")
                        .jsonPrimitive.content ==
                    header.getValue("log_id").jsonPrimitive.content,
            ) { "C_LOG_HASH_MISMATCH" }
            database.rawQuery("SELECT COUNT(*) FROM export_snapshot_parts", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getLong(0) == 0L) { "C_MISSING_DEPENDENCY" }
            }
            database.rawQuery("SELECT COUNT(*) FROM export_compactions", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getLong(0) == 0L) { "C_MISSING_DEPENDENCY" }
            }
        }

        fun addSnapshotPart(record: kotlinx.serialization.json.JsonObject) {
            val recordId = record["record_id"]?.jsonPrimitive?.content ?: error("C_SCHEMA_VIOLATION")
            val recordHash = checkNotNull(PallaSyncCore.objectHash(record.toString())) { "C_SCHEMA_VIOLATION" }
            try {
                database.execSQL(
                    "INSERT INTO export_snapshot_parts(record_id,record_hash,record_json) VALUES (?,?,?)",
                    arrayOf<Any>(recordId, recordHash, record.toString()),
                )
            } catch (_: SQLiteConstraintException) {
                error("C_SCHEMA_VIOLATION")
            }
        }

        private fun verifyCompactionSnapshotParts(
            authorityJson: String,
            compaction: kotlinx.serialization.json.JsonObject,
            snapshotVerifier: PallaSyncArchiveSnapshotVerifier?,
        ) {
            val references = compaction.getValue("snapshot_parts").jsonArray
            check(references.isNotEmpty()) { "C_SNAPSHOT_INVALID" }
            val verifier = checkNotNull(snapshotVerifier) { "C_MISSING_DEPENDENCY" }
            verifier.verify(authorityJson, compaction.toString()) { consume ->
                references.forEachIndexed { index, reference ->
                    val expected = reference.jsonObject
                    val recordId = expected.getValue("record_id").jsonPrimitive.content
                    val expectedHash = expected.getValue("record_hash").jsonPrimitive.content
                    val recordJson =
                        database
                            .rawQuery(
                                "SELECT record_hash,record_json FROM export_snapshot_parts WHERE record_id=?",
                                arrayOf(recordId),
                            ).use { parts ->
                                check(parts.moveToFirst()) { "C_MISSING_DEPENDENCY" }
                                check(parts.getString(0) == expectedHash) { "C_SNAPSHOT_INVALID" }
                                parts.getString(1)
                            }
                    check(database.delete("export_snapshot_parts", "record_id=?", arrayOf(recordId)) == 1) {
                        "C_SNAPSHOT_INVALID"
                    }
                    consume(index, recordJson)
                }
            }
        }

        fun requireAllMatched() {
            database.rawQuery("SELECT COUNT(*) FROM export_receipts", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getLong(0) == 0L) { "C_ACCEPTANCE_UNPROVEN" }
            }
        }

        override fun close() {
            database.close()
            check(file.delete() || !file.exists()) { "C_LOCAL_STORAGE_FULL" }
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
