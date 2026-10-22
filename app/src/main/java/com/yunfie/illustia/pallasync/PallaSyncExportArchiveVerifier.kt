package com.yunfie.illustia.pallasync

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64

internal typealias PallaSyncArchiveReseedAuthorityVerifier =
    suspend (String, JsonArray, kotlinx.serialization.json.JsonObject) -> String

internal typealias PallaSyncArchiveSourceAuthorityProvider = suspend (Long) -> String?

/** Streaming §17.4 footer and line-integrity check; signatures and AEAD remain separate gates. */
internal object PallaSyncExportArchiveVerifier {
    data class Header(
        val chainId: String,
        val generation: Long,
        val logId: String,
        val includeRecovery: Boolean,
        val complete: Boolean,
        val headSeq: Long,
        val headHash: String,
        val transparencyHeadJson: String?,
        val historyCutJson: String?,
        val authorityJson: String?,
    )

    private const val MAX_ARCHIVE_LINE_BYTES = 8_388_608
    private const val HASH_BYTES = 32
    private val HASH_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")

    fun verify(
        input: InputStream,
        expectedSource: PallaSyncExportArchive.ExpectedSource? = null,
        recordVerifier: PallaSyncArchiveRecordVerifier? = null,
        snapshotVerifier: PallaSyncArchiveSnapshotVerifier? = null,
        logEntrySink: PallaSyncArchiveLogEntrySink? = null,
        beforeRetain: (suspend (Header) -> Unit)? = null,
        reseedAuthorityVerifier: PallaSyncArchiveReseedAuthorityVerifier? = null,
        sourceAuthorityProvider: PallaSyncArchiveSourceAuthorityProvider? = null,
    ): Header =
        runBlocking {
            verifySuspending(
                input,
                expectedSource,
                recordVerifier,
                snapshotVerifier,
                logEntrySink,
                beforeRetain,
                reseedAuthorityVerifier,
                sourceAuthorityProvider,
            )
        }

    suspend fun verifySuspending(
        input: InputStream,
        expectedSource: PallaSyncExportArchive.ExpectedSource? = null,
        recordVerifier: PallaSyncArchiveRecordVerifier? = null,
        snapshotVerifier: PallaSyncArchiveSnapshotVerifier? = null,
        logEntrySink: PallaSyncArchiveLogEntrySink? = null,
        beforeRetain: (suspend (Header) -> Unit)? = null,
        reseedAuthorityVerifier: PallaSyncArchiveReseedAuthorityVerifier? = null,
        sourceAuthorityProvider: PallaSyncArchiveSourceAuthorityProvider? = null,
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
        var transparencyHeadJson: String? = null
        var verifiedAuthorityJson: String? = null
        var historyCutJson: String? = null
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
                    check(source.read() == -1) { "C_SCHEMA_VIOLATION" }
                    if (complete) {
                        check(
                            includeRecovery && !recoveryObjectsOmitted && pin != null &&
                                expectedSource?.identityJson != null && recordVerifier != null && snapshotVerifier != null,
                        ) { "C_SCHEMA_VIOLATION" }
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
                        verifiedAuthorityJson =
                            expectedSource?.identityJson?.let { identity ->
                                checkNotNull(receiptLinks).verifyAuthorityHistory(
                                    header,
                                    identity,
                                    checkNotNull(canonicalPin),
                                    recordVerifier,
                                    snapshotVerifier,
                                    reseedAuthorityVerifier,
                                    sourceAuthorityProvider,
                                )
                            }
                        if (
                            !checkNotNull(receiptLinks).verifySourceReceipts(
                                checkNotNull(canonicalPin),
                                verifiedAuthorityJson,
                                recordVerifier,
                                sourceAuthorityProvider,
                            )
                        ) {
                            restoreEvidenceUnsupported = true
                        }
                        historyCutJson =
                            verifiedAuthorityJson?.let { authority ->
                                val headObject = Json.parseToJsonElement(checkNotNull(transparencyHeadJson)).jsonObject
                                val body = buildJsonObject { put("head", headObject) }.toString().toByteArray(Charsets.UTF_8)
                                PallaSyncCore.validateInitialHistoryCut(authority, body)
                            }
                    }
                    if (complete) check(!restoreEvidenceUnsupported) { "C_MISSING_DEPENDENCY" }
                    val verifiedHeader =
                        Header(
                            chainId = header.getValue("chain_id").jsonPrimitive.content,
                            generation = header.getValue("generation").jsonPrimitive.long,
                            logId = header.getValue("log_id").jsonPrimitive.content,
                            includeRecovery = includeRecovery,
                            complete = complete,
                            headSeq = header.getValue("head_seq").jsonPrimitive.long,
                            headHash = header.getValue("head_hash").jsonPrimitive.content,
                            transparencyHeadJson = transparencyHeadJson,
                            historyCutJson = historyCutJson,
                            authorityJson = verifiedAuthorityJson,
                        )
                    if (complete) beforeRetain?.invoke(verifiedHeader)
                    if (complete && logEntrySink != null) {
                        checkNotNull(receiptLinks).retainVerifiedAuthorityHistory(
                            verifiedAuthorityJson ?: error("C_MISSING_DEPENDENCY"),
                            logEntrySink,
                        )
                    }
                    return verifiedHeader
                }
                check(kind in PallaSyncExportArchive.EXPORT_LINE_KINDS) { "C_SCHEMA_VIOLATION" }
                PallaSyncExportArchive.validateExportLine(value)
                if (pin != null) {
                    check(!transparencyHeadVerified) { "C_TRANSPARENCY_INVALID" }
                    if (kind in setOf("source_receipt", "upload_receipt", "transparency_entry", "transparency_head")) {
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
                            transparencyHeadJson = head.toString()
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

                        "removed_id" -> {
                            checkNotNull(receiptLinks).addRemovedId(
                                value.getValue("record_id").jsonPrimitive.content,
                                value.getValue("record_hash").jsonPrimitive.content,
                            )
                            restoreEvidenceUnsupported = true
                        }

                        "source_receipt" -> {
                            checkNotNull(receiptLinks).addSourceReceipt(value.getValue("receipt").jsonObject)
                        }

                        "control", "boundary" -> {
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
    internal class ExportReceiptLinkIndex : Closeable {
        private val file = File.createTempFile("pallasync-export-receipts-", ".sqlite")
        private val database = SQLiteDatabase.openOrCreateDatabase(file, null)

        init {
            database.execSQL(
                "CREATE TABLE export_receipts (transparency_index INTEGER PRIMARY KEY, receipt_json TEXT NOT NULL)",
            )
            database.execSQL("CREATE TABLE export_log_entries (relay_seq INTEGER PRIMARY KEY, entry_json TEXT NOT NULL)")
            database.execSQL(
                "CREATE TABLE export_log_receipts (log_id TEXT NOT NULL, relay_seq INTEGER NOT NULL, receipt_json TEXT NOT NULL, PRIMARY KEY(log_id,relay_seq))",
            )
            database.execSQL(
                "CREATE TABLE export_snapshot_parts (record_id TEXT PRIMARY KEY, record_hash TEXT NOT NULL, record_json TEXT NOT NULL, receipt_verified INTEGER NOT NULL DEFAULT 0)",
            )
            database.execSQL("CREATE TABLE export_compactions (compaction_hash TEXT PRIMARY KEY)")
            database.execSQL("CREATE TABLE export_record_ids (record_id TEXT PRIMARY KEY, record_hash TEXT NOT NULL)")
            database.execSQL("CREATE TABLE export_removed_ids (record_id TEXT PRIMARY KEY, record_hash TEXT NOT NULL)")
            database.execSQL("CREATE TABLE export_source_record_ids (record_id TEXT PRIMARY KEY, record_hash TEXT NOT NULL)")
            database.execSQL(
                "CREATE TABLE export_source_entries (entry_hash TEXT PRIMARY KEY, relay_seq INTEGER NOT NULL, entry_json TEXT NOT NULL)",
            )
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
            val receiptObject = Json.parseToJsonElement(receipt).jsonObject
            if (receiptObject["relay_seq"]?.toString() == "null") {
                val objectHash = receiptObject["object_hash"]?.jsonPrimitive?.content ?: error("C_SCHEMA_VIOLATION")
                linkNullSequenceSnapshotReceipt(objectHash)
            }
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
            if (entry["kind"]?.jsonPrimitive?.content == "record") {
                val record = entry.getValue("object").jsonObject
                val recordId = record.getValue("record_id").jsonPrimitive.content
                val recordHash = checkNotNull(PallaSyncCore.objectHash(record.toString())) { "C_SCHEMA_VIOLATION" }
                try {
                    database.execSQL(
                        "INSERT INTO export_record_ids(record_id,record_hash) VALUES (?,?)",
                        arrayOf<Any>(recordId, recordHash),
                    )
                } catch (_: SQLiteConstraintException) {
                    val existingHash =
                        database
                            .rawQuery("SELECT record_hash FROM export_record_ids WHERE record_id=?", arrayOf(recordId))
                            .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    check(existingHash == recordHash) { "C_RECORD_ID_CONFLICT" }
                }
                val removedHash =
                    database
                        .rawQuery("SELECT record_hash FROM export_removed_ids WHERE record_id=?", arrayOf(recordId))
                        .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                check(removedHash == null || removedHash == recordHash) { "C_RECORD_ID_CONFLICT" }
                val sourceHash =
                    database
                        .rawQuery("SELECT record_hash FROM export_source_record_ids WHERE record_id=?", arrayOf(recordId))
                        .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                check(sourceHash == null || sourceHash == recordHash) { "C_RECORD_ID_CONFLICT" }
            }
        }

        fun addRemovedId(
            recordId: String,
            recordHash: String,
        ) {
            val retainedHash =
                database
                    .rawQuery("SELECT record_hash FROM export_record_ids WHERE record_id=?", arrayOf(recordId))
                    .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            val sourceHash =
                database
                    .rawQuery("SELECT record_hash FROM export_source_record_ids WHERE record_id=?", arrayOf(recordId))
                    .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            check((retainedHash == null || retainedHash == recordHash) && (sourceHash == null || sourceHash == recordHash)) {
                "C_RECORD_ID_CONFLICT"
            }
            try {
                database.execSQL(
                    "INSERT INTO export_removed_ids(record_id,record_hash) VALUES (?,?)",
                    arrayOf<Any>(recordId, recordHash),
                )
            } catch (_: SQLiteConstraintException) {
                val existingHash =
                    database
                        .rawQuery("SELECT record_hash FROM export_removed_ids WHERE record_id=?", arrayOf(recordId))
                        .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                check(existingHash == recordHash) { "C_RECORD_ID_CONFLICT" }
                error("C_SCHEMA_VIOLATION")
            }
        }

        fun addLogReceipt(receipt: kotlinx.serialization.json.JsonObject) {
            val sequence = receipt["relay_seq"]?.takeUnless { it.toString() == "null" }?.jsonPrimitive?.long ?: return
            val logId = receipt.getValue("log_id").jsonPrimitive.content
            try {
                database.execSQL(
                    "INSERT INTO export_log_receipts(log_id,relay_seq,receipt_json) VALUES (?,?,?)",
                    arrayOf<Any>(logId, sequence, receipt.toString()),
                )
            } catch (_: SQLiteConstraintException) {
                error("C_TRANSPARENCY_INVALID")
            }
        }

        fun logReceipt(
            logId: String,
            relaySequence: Long,
        ): String =
            database
                .rawQuery(
                    "SELECT receipt_json FROM export_log_receipts WHERE log_id=? AND relay_seq=?",
                    arrayOf(logId, relaySequence.toString()),
                ).use { receipts ->
                    check(receipts.moveToFirst()) { "C_ACCEPTANCE_UNPROVEN" }
                    receipts.getString(0)
                }

        fun addSourceReceipt(entry: kotlinx.serialization.json.JsonObject) {
            val entryHash = checkNotNull(PallaSyncCore.objectHash(entry.toString())) { "C_SCHEMA_VIOLATION" }
            val sequence = entry.getValue("relay_seq").jsonPrimitive.long
            if (entry.getValue("kind").jsonPrimitive.content == "record") {
                val record = entry.getValue("object").jsonObject
                val recordId = record.getValue("record_id").jsonPrimitive.content
                val recordHash = checkNotNull(PallaSyncCore.objectHash(record.toString())) { "C_SCHEMA_VIOLATION" }
                val retainedHash =
                    database
                        .rawQuery("SELECT record_hash FROM export_record_ids WHERE record_id=?", arrayOf(recordId))
                        .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                val removedHash =
                    database
                        .rawQuery("SELECT record_hash FROM export_removed_ids WHERE record_id=?", arrayOf(recordId))
                        .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                check((retainedHash == null || retainedHash == recordHash) && (removedHash == null || removedHash == recordHash)) {
                    "C_RECORD_ID_CONFLICT"
                }
                try {
                    database.execSQL(
                        "INSERT INTO export_source_record_ids(record_id,record_hash) VALUES (?,?)",
                        arrayOf<Any>(recordId, recordHash),
                    )
                } catch (_: SQLiteConstraintException) {
                    val existingHash =
                        database
                            .rawQuery("SELECT record_hash FROM export_source_record_ids WHERE record_id=?", arrayOf(recordId))
                            .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    check(existingHash == recordHash) { "C_RECORD_ID_CONFLICT" }
                    error("C_SCHEMA_VIOLATION")
                }
            }
            try {
                database.execSQL(
                    "INSERT INTO export_source_entries(entry_hash,relay_seq,entry_json) VALUES (?,?,?)",
                    arrayOf<Any>(entryHash, sequence, entry.toString()),
                )
            } catch (_: SQLiteConstraintException) {
                error("C_SCHEMA_VIOLATION")
            }
        }

        suspend fun verifySourceReceipts(
            pinJson: String,
            finalAuthorityJson: String?,
            recordVerifier: PallaSyncArchiveRecordVerifier?,
            sourceAuthorityProvider: PallaSyncArchiveSourceAuthorityProvider?,
        ): Boolean {
            database.rawQuery("SELECT entry_hash,relay_seq,entry_json FROM export_source_entries", null).use { entries ->
                while (entries.moveToNext()) {
                    val entryHash = entries.getString(0)
                    val sequence = entries.getLong(1)
                    val entryJson = entries.getString(2)
                    val entry = Json.parseToJsonElement(entryJson).jsonObject
                    val objectHash =
                        checkNotNull(PallaSyncCore.objectHash(entry.getValue("object").toString())) {
                            "C_SCHEMA_VIOLATION"
                        }
                    val candidates =
                        database
                            .rawQuery(
                                "SELECT receipt_json FROM export_log_receipts WHERE relay_seq=?",
                                arrayOf(sequence.toString()),
                            ).use { receipts ->
                                buildList {
                                    while (receipts.moveToNext()) add(receipts.getString(0))
                                }
                            }
                    val linked =
                        candidates.filter { receiptJson ->
                            val receipt = Json.parseToJsonElement(receiptJson).jsonObject
                            receipt["entry_hash"]?.jsonPrimitive?.content == entryHash &&
                                receipt["object_hash"]?.jsonPrimitive?.content == objectHash &&
                                receipt["received_at_ms"] == entry["received_at_ms"]
                        }
                    check(linked.size == 1) { "C_ACCEPTANCE_UNPROVEN" }
                    PallaSyncCore.validateExportSourceReceipt(linked.single(), entryJson, pinJson)

                    if (entry.getValue("kind").jsonPrimitive.content != "record") return false
                    val record = entry.getValue("object").jsonObject
                    val generation = record.getValue("generation").jsonPrimitive.long
                    val finalGeneration =
                        finalAuthorityJson
                            ?.let { Json.parseToJsonElement(it).jsonObject }
                            ?.get("parameters")
                            ?.jsonObject
                            ?.get("generation")
                            ?.jsonPrimitive
                            ?.long
                    val authorityJson =
                        if (finalGeneration == generation) {
                            checkNotNull(finalAuthorityJson)
                        } else {
                            sourceAuthorityProvider?.invoke(generation) ?: return false
                        }
                    if (recordVerifier == null) return false
                    check(PallaSyncCore.validateRecordAuthority(authorityJson, record.toString(), sequence)) {
                        "C_ACCEPTANCE_UNPROVEN"
                    }
                    recordVerifier.verify(authorityJson, record.toString(), sequence)
                }
            }
            return true
        }

        fun addCompaction(compaction: kotlinx.serialization.json.JsonObject) {
            val hash = checkNotNull(PallaSyncCore.objectHash(compaction.toString())) { "C_SCHEMA_VIOLATION" }
            try {
                database.execSQL("INSERT INTO export_compactions(compaction_hash) VALUES (?)", arrayOf<Any>(hash))
            } catch (_: SQLiteConstraintException) {
                error("C_SCHEMA_VIOLATION")
            }
        }

        suspend fun verifyAuthorityHistory(
            header: kotlinx.serialization.json.JsonObject,
            identityJson: String,
            pinJson: String,
            recordVerifier: PallaSyncArchiveRecordVerifier?,
            snapshotVerifier: PallaSyncArchiveSnapshotVerifier?,
            reseedAuthorityVerifier: PallaSyncArchiveReseedAuthorityVerifier?,
            sourceAuthorityProvider: PallaSyncArchiveSourceAuthorityProvider?,
        ): String {
            var authority = PallaSyncCore.beginExportAuthorityHistory(identityJson, pinJson)
            var expectedSequence = 1L
            var skipThroughSequence = 0L
            val reseedTransition = firstReseedTransition()
            if (reseedTransition != null) {
                val (sequence, entry) = reseedTransition
                val sourceGeneration =
                    entry
                        .getValue("object")
                        .jsonObject
                        .getValue("generation")
                        .jsonPrimitive
                        .long - 1L
                val trustedSource =
                    checkNotNull(sourceAuthorityProvider?.invoke(sourceGeneration)) {
                        "C_MISSING_DEPENDENCY"
                    }
                val (verifiedAuthority, cutSequence) =
                    verifyReseedTransition(
                        trustedSource,
                        sequence,
                        entry,
                        header,
                        reseedAuthorityVerifier,
                        snapshotVerifier,
                    )
                authority = verifiedAuthority
                skipThroughSequence = cutSequence
                expectedSequence = Math.addExact(cutSequence, 1L)
            }
            database.rawQuery("SELECT relay_seq,entry_json FROM export_log_entries ORDER BY relay_seq", null).use { entries ->
                while (entries.moveToNext()) {
                    val sequence = entries.getLong(0)
                    if (sequence <= skipThroughSequence) continue
                    check(sequence == expectedSequence) { "C_MISSING_DEPENDENCY" }
                    val receipt = logReceipt(header.getValue("log_id").jsonPrimitive.content, sequence)
                    val entryJson = entries.getString(1)
                    val entry = Json.parseToJsonElement(entryJson).jsonObject
                    val kind = entry.getValue("kind").jsonPrimitive.content
                    val authorityState = Json.parseToJsonElement(authority).jsonObject
                    val currentGeneration =
                        authorityState["parameters"]
                            ?.takeUnless { it.toString() == "null" }
                            ?.jsonObject
                            ?.get("generation")
                            ?.jsonPrimitive
                            ?.long
                    val entryGeneration =
                        entry
                            .getValue("object")
                            .jsonObject["generation"]
                            ?.jsonPrimitive
                            ?.long
                    if (kind == "parameters" && currentGeneration != null && entryGeneration == currentGeneration + 1L) {
                        val (verifiedAuthority, cutSequence) =
                            verifyReseedTransition(
                                authority,
                                sequence,
                                entry,
                                header,
                                reseedAuthorityVerifier,
                                snapshotVerifier,
                            )
                        authority = verifiedAuthority
                        skipThroughSequence = cutSequence
                        expectedSequence = Math.addExact(cutSequence, 1L)
                        continue
                    }
                    authority =
                        PallaSyncCore.advanceAuthorityHistory(
                            authority,
                            entryJson,
                            receipt,
                        )
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
            val finalGeneration =
                finalAuthority
                    .getValue("parameters")
                    .jsonObject
                    .getValue("generation")
                    .jsonPrimitive.long
            val lastSequence = expectedSequence - 1L
            check(
                lastSequence == header.getValue("head_seq").jsonPrimitive.long &&
                    finalGeneration == header.getValue("generation").jsonPrimitive.long &&
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
            database.rawQuery("SELECT COUNT(*) FROM export_snapshot_parts WHERE receipt_verified=0", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getLong(0) == 0L) { "C_ACCEPTANCE_UNPROVEN" }
            }
            return authority
        }

        private fun firstReseedTransition(): Pair<Long, kotlinx.serialization.json.JsonObject>? {
            var previousGeneration: Long? = null
            database
                .rawQuery("SELECT relay_seq,entry_json FROM export_log_entries ORDER BY relay_seq", null)
                .use { rows ->
                    while (rows.moveToNext()) {
                        val entry = Json.parseToJsonElement(rows.getString(1)).jsonObject
                        if (entry.getValue("kind").jsonPrimitive.content != "parameters") continue
                        val generation =
                            entry
                                .getValue("object")
                                .jsonObject
                                .getValue("generation")
                                .jsonPrimitive
                                .long
                        if (previousGeneration != null && generation == previousGeneration + 1L) {
                            return rows.getLong(0) to entry
                        }
                        previousGeneration = generation
                    }
                }
            return null
        }

        private suspend fun verifyReseedTransition(
            sourceAuthorityJson: String,
            transitionSequence: Long,
            transitionEntry: kotlinx.serialization.json.JsonObject,
            archiveHeader: kotlinx.serialization.json.JsonObject,
            reseedAuthorityVerifier: PallaSyncArchiveReseedAuthorityVerifier?,
            snapshotVerifier: PallaSyncArchiveSnapshotVerifier?,
        ): Pair<String, Long> {
            val sourceGeneration =
                Json
                    .parseToJsonElement(sourceAuthorityJson)
                    .jsonObject
                    .getValue("parameters")
                    .jsonObject
                    .getValue("generation")
                    .jsonPrimitive.long
            val targetGeneration =
                transitionEntry
                    .getValue("object")
                    .jsonObject
                    .getValue("generation")
                    .jsonPrimitive.long
            check(targetGeneration == sourceGeneration + 1L) { "C_EPOCH_FORK" }
            val targetLogId = archiveHeader.getValue("log_id").jsonPrimitive.content
            var expectedSequence = transitionSequence
            var cutSequence: Long? = null
            var cutCompaction: kotlinx.serialization.json.JsonObject? = null
            database
                .rawQuery(
                    "SELECT relay_seq,entry_json FROM export_log_entries WHERE relay_seq>=? ORDER BY relay_seq",
                    arrayOf(transitionSequence.toString()),
                ).use { rows ->
                    while (rows.moveToNext() && cutSequence == null) {
                        val sequence = rows.getLong(0)
                        check(sequence == expectedSequence) { "C_MISSING_DEPENDENCY" }
                        val entry = Json.parseToJsonElement(rows.getString(1)).jsonObject
                        val kind = entry.getValue("kind").jsonPrimitive.content
                        check(kind in setOf("parameters", "device", "envelope", "admin", "epoch", "compaction")) {
                            "C_SNAPSHOT_INVALID"
                        }
                        if (kind == "compaction") {
                            val compaction = entry.getValue("object").jsonObject
                            if (
                                compaction.getValue("generation").jsonPrimitive.long == targetGeneration &&
                                compaction.getValue("log_id").jsonPrimitive.content == targetLogId
                            ) {
                                cutSequence = sequence
                                cutCompaction = compaction
                            }
                        }
                        expectedSequence = Math.addExact(expectedSequence, 1L)
                    }
                }
            val through = checkNotNull(cutSequence) { "C_MISSING_DEPENDENCY" }
            val compaction = checkNotNull(cutCompaction) { "C_MISSING_DEPENDENCY" }
            val expectedEntries = mutableListOf<kotlinx.serialization.json.JsonObject>()
            val uploadReceipts = mutableListOf<kotlinx.serialization.json.JsonObject>()
            database
                .rawQuery(
                    "SELECT relay_seq,entry_json FROM export_log_entries WHERE relay_seq<=? ORDER BY relay_seq",
                    arrayOf(through.toString()),
                ).use { rows ->
                    var sequence = 1L
                    while (rows.moveToNext()) {
                        check(rows.getLong(0) == sequence) { "C_MISSING_DEPENDENCY" }
                        val entry = Json.parseToJsonElement(rows.getString(1)).jsonObject
                        expectedEntries +=
                            buildJsonObject {
                                put("kind", entry.getValue("kind"))
                                put("object", entry.getValue("object"))
                            }
                        val receiptJson = logReceipt(targetLogId, sequence)
                        uploadReceipts += Json.parseToJsonElement(receiptJson).jsonObject
                        sequence = Math.addExact(sequence, 1L)
                    }
                    check(sequence == through + 1L) { "C_MISSING_DEPENDENCY" }
                }
            val targetParameters =
                expectedEntries
                    .single {
                        it.getValue("kind").jsonPrimitive.content == "parameters" &&
                            it
                                .getValue("object")
                                .jsonObject
                                .getValue("generation")
                                .jsonPrimitive.long == targetGeneration
                    }.getValue("object")
                    .jsonObject
            val targetEpoch =
                expectedEntries
                    .single {
                        it.getValue("kind").jsonPrimitive.content == "epoch" &&
                            it
                                .getValue("object")
                                .jsonObject["generation"]
                                ?.jsonPrimitive
                                ?.long == targetGeneration &&
                            it
                                .getValue("object")
                                .jsonObject["reason"]
                                ?.jsonPrimitive
                                ?.content == "reseed"
                    }.getValue("object")
                    .jsonObject
            val lastReceipt = uploadReceipts.last()
            val checkpoint =
                buildJsonObject {
                    put("log_id", targetLogId)
                    put("head_seq", through)
                    put("head_hash", lastReceipt.getValue("resulting_head_hash"))
                    put("parameters_hash", checkNotNull(PallaSyncCore.objectHash(targetParameters.toString())))
                    put("epoch_hash", checkNotNull(PallaSyncCore.objectHash(targetEpoch.toString())))
                    put("receipts", JsonArray(uploadReceipts))
                }
            val verifiedAuthority =
                checkNotNull(reseedAuthorityVerifier) { "C_MISSING_DEPENDENCY" }
                    .invoke(sourceAuthorityJson, JsonArray(expectedEntries), checkpoint)
            verifyCompactionSnapshotParts(verifiedAuthority, compaction, snapshotVerifier)
            val compactionHash = checkNotNull(PallaSyncCore.objectHash(compaction.toString())) { "C_SCHEMA_VIOLATION" }
            check(database.delete("export_compactions", "compaction_hash=?", arrayOf(compactionHash)) == 1) {
                "C_MISSING_DEPENDENCY"
            }
            return verifiedAuthority to through
        }

        suspend fun retainVerifiedAuthorityHistory(
            verifiedAuthorityJson: String,
            sink: PallaSyncArchiveLogEntrySink,
        ) {
            // The complete archive was already authority-replayed before retention. Replaying
            // again would bypass the verified snapshot transition at a generation cutover.
            val authorityState = Json.parseToJsonElement(verifiedAuthorityJson).jsonObject
            val logId =
                authorityState
                    .getValue("pin")
                    .jsonObject
                    .getValue("log_id")
                    .jsonPrimitive.content
            database.rawQuery("SELECT relay_seq,entry_json FROM export_log_entries ORDER BY relay_seq", null).use { entries ->
                while (entries.moveToNext()) {
                    val sequence = entries.getLong(0)
                    val entryJson = entries.getString(1)
                    val receipt = logReceipt(logId, sequence)
                    sink.retain(verifiedAuthorityJson, entryJson, receipt, sequence)
                }
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

        fun linkNullSequenceSnapshotReceipt(objectHash: String) {
            val unmatchedParts =
                database
                    .rawQuery(
                        "SELECT COUNT(*) FROM export_snapshot_parts WHERE record_hash=? AND receipt_verified=0",
                        arrayOf(objectHash),
                    ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else -1L }
            check(unmatchedParts == 1L) { "C_ACCEPTANCE_UNPROVEN" }
            database.execSQL(
                "UPDATE export_snapshot_parts SET receipt_verified=1 WHERE record_hash=? AND receipt_verified=0",
                arrayOf(objectHash),
            )
            check(
                database
                    .rawQuery(
                        "SELECT COUNT(*) FROM export_snapshot_parts WHERE record_hash=? AND receipt_verified=1",
                        arrayOf(objectHash),
                    ).use { cursor -> cursor.moveToFirst() && cursor.getLong(0) == 1L },
            ) { "C_ACCEPTANCE_UNPROVEN" }
        }

        private suspend fun verifyCompactionSnapshotParts(
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
                                "SELECT record_hash,record_json,receipt_verified FROM export_snapshot_parts WHERE record_id=?",
                                arrayOf(recordId),
                            ).use { parts ->
                                check(parts.moveToFirst()) { "C_MISSING_DEPENDENCY" }
                                check(parts.getString(0) == expectedHash) { "C_SNAPSHOT_INVALID" }
                                check(parts.getInt(2) == 1) { "C_ACCEPTANCE_UNPROVEN" }
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
            database.rawQuery("SELECT COUNT(*) FROM export_snapshot_parts WHERE receipt_verified=0", null).use { cursor ->
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
