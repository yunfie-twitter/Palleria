package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.SnapshotPublicationPartEntity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

internal data class PallaSyncSnapshotPartReference(
    val partIndex: Int,
    val recordId: String,
    val recordHash: String,
)

internal data class PallaSyncProducedSnapshot(
    val header: JsonObject,
    val stateCommitment: String,
    val parts: List<PallaSyncSnapshotPartReference>,
)

/** Durable callbacks allow a caller to checkpoint signed ciphertext before the network boundary. */
internal interface PallaSyncSnapshotPartJournal {
    suspend fun begin(
        header: JsonObject,
        partCount: Int,
    )

    suspend fun prepared(partIndex: Int): SnapshotPublicationPartEntity?

    suspend fun retain(part: SnapshotPublicationPartEntity)
}

/** Two-pass, bounded-memory §16.6 producer. A caller must validate and retain every upload receipt. */
internal class PallaSyncSnapshotProducer(
    database: PallaSyncDatabase,
    pageSize: Int = 200,
) {
    private val stateReader = PallaSyncSnapshotStateReader(database, pageSize)

    /**
     * Plans the exact part count, then rereads the stable logical state and streams each encrypted
     * part to [publishPart]. No CompactionRecord is published here; the caller may do that only
     * after this returns and all upload receipts have been checked and retained.
     */
    @Suppress("LongMethod")
    suspend fun produce(
        chainId: String,
        compactionId: String,
        deviceId: String,
        epochSession: PallaSyncEpochSession,
        signingSeed: () -> ByteArray,
        devicePublicKeyBase64Url: String? = null,
        journal: PallaSyncSnapshotPartJournal? = null,
        reseedHeader: JsonObject? = null,
        preservePendingOutbox: Boolean = false,
        publishPart: suspend (recordId: String, signedRecordJson: String, recordHash: String) -> Unit,
    ): PallaSyncProducedSnapshot {
        require(isUuid4(compactionId) && isUuid4(deviceId)) { "C_SNAPSHOT_INVALID" }
        val plan = plan(chainId, compactionId, reseedHeader, preservePendingOutbox)
        check(plan.partCount in 1..MAX_SNAPSHOT_PARTS) { "C_LOCAL_LIMIT" }
        if (journal != null) {
            check(!devicePublicKeyBase64Url.isNullOrBlank()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            journal.begin(plan.header, plan.partCount)
        }
        val parts = ArrayList<PallaSyncSnapshotPartReference>(plan.partCount)
        val verifier =
            PallaSyncSnapshotVerifier.beginFromEpochSession(
                epochSession,
                plan.headerJson,
                compactionId,
                plan.partCount,
            )
        var expectedHeader = true
        var current = SnapshotPartBuffer(compactionId)
        var partIndex = 0
        try {
            stateReader.stream(chainId, reseedHeader, preservePendingOutbox) { entry ->
                if (expectedHeader) {
                    check(canonical(entry) == plan.headerJson) { "C_SNAPSHOT_INVALID" }
                    expectedHeader = false
                }
                if (!current.tryAdd(entry)) {
                    uploadPart(
                        current.frame(compactionId, partIndex, plan.partCount),
                        partIndex,
                        plan.partCount,
                        deviceId,
                        chainId,
                        compactionId,
                        epochSession,
                        verifier,
                        signingSeed,
                        devicePublicKeyBase64Url,
                        journal,
                        publishPart,
                        parts,
                    )
                    partIndex++
                    current = SnapshotPartBuffer(compactionId)
                    check(current.tryAdd(entry)) { "C_LOCAL_LIMIT" }
                }
            }
            check(!expectedHeader && current.isNotEmpty) { "C_SNAPSHOT_INVALID" }
            uploadPart(
                current.frame(compactionId, partIndex, plan.partCount),
                partIndex,
                plan.partCount,
                deviceId,
                chainId,
                compactionId,
                epochSession,
                verifier,
                signingSeed,
                devicePublicKeyBase64Url,
                journal,
                publishPart,
                parts,
            )
            partIndex++
            check(partIndex == plan.partCount) { "C_SNAPSHOT_INVALID" }
            return PallaSyncProducedSnapshot(plan.header, verifier.finishCommitment(), parts.toList())
        } finally {
            verifier.close()
        }
    }

    private suspend fun plan(
        chainId: String,
        compactionId: String,
        reseedHeader: JsonObject?,
        preservePendingOutbox: Boolean,
    ): Plan {
        var header: JsonObject? = null
        var headerJson: String? = null
        var count = 0
        var buffer = SnapshotPartBuffer(compactionId)
        stateReader.stream(chainId, reseedHeader, preservePendingOutbox) { entry ->
            if (header == null) {
                check(entry["kind"]?.jsonPrimitive?.content == "header") { "C_SNAPSHOT_INVALID" }
                header = entry
                headerJson = canonical(entry)
            }
            if (!buffer.tryAdd(entry)) {
                count++
                check(count < MAX_SNAPSHOT_PARTS) { "C_LOCAL_LIMIT" }
                buffer = SnapshotPartBuffer(compactionId)
                check(buffer.tryAdd(entry)) { "C_LOCAL_LIMIT" }
            }
        }
        check(header != null && buffer.isNotEmpty) { "C_SNAPSHOT_INVALID" }
        count++
        check(count <= MAX_SNAPSHOT_PARTS) { "C_LOCAL_LIMIT" }
        return Plan(checkNotNull(header), checkNotNull(headerJson), count)
    }

    private suspend fun uploadPart(
        frame: JsonObject,
        partIndex: Int,
        partCount: Int,
        deviceId: String,
        chainId: String,
        compactionId: String,
        epochSession: PallaSyncEpochSession,
        verifier: PallaSyncSnapshotVerifier,
        signingSeed: () -> ByteArray,
        devicePublicKeyBase64Url: String?,
        journal: PallaSyncSnapshotPartJournal?,
        publishPart: suspend (recordId: String, signedRecordJson: String, recordHash: String) -> Unit,
        references: MutableList<PallaSyncSnapshotPartReference>,
    ) {
        val partJson = canonical(frame)
        verifier.acceptDecryptedAuthenticatedPart(partJson)
        val saved = journal?.prepared(partIndex)
        val recordId: String
        val recordJson: String
        val recordHash: String
        if (saved != null) {
            check(saved.chainId == chainId && saved.compactionId == compactionId && saved.partIndex == partIndex) {
                "C_SNAPSHOT_INVALID"
            }
            val parsed =
                kotlinx.serialization.json.Json
                    .parseToJsonElement(saved.signedJson)
                    .jsonObject
            recordId = parsed["record_id"]?.jsonPrimitive?.content ?: error("C_SNAPSHOT_INVALID")
            recordJson = canonical(parsed)
            recordHash = checkNotNull(PallaSyncCore.objectHash(recordJson)) { "C_SNAPSHOT_INVALID" }
            check(recordId == saved.recordId && recordHash == saved.recordHash && recordJson == saved.signedJson) {
                "C_SNAPSHOT_INVALID"
            }
            val verifiedFrame =
                epochSession.decryptSnapshotPart(
                    recordJson,
                    checkNotNull(devicePublicKeyBase64Url) { "C_BOOTSTRAP_TRUST_REQUIRED" },
                )
            check(PallaSyncCore.canonicalJson(verifiedFrame) == partJson) { "C_SNAPSHOT_INVALID" }
        } else {
            recordId = UUID.randomUUID().toString()
            recordJson =
                canonical(
                    kotlinx.serialization.json.Json
                        .parseToJsonElement(
                            epochSession.createSnapshotRecord(recordId, deviceId, partJson, signingSeed()),
                        ).jsonObject,
                )
            recordHash = checkNotNull(PallaSyncCore.objectHash(recordJson)) { "C_SNAPSHOT_INVALID" }
            journal?.retain(
                SnapshotPublicationPartEntity(
                    chainId = chainId,
                    compactionId = compactionId,
                    partIndex = partIndex,
                    recordId = recordId,
                    recordHash = recordHash,
                    signedJson = recordJson,
                ),
            )
        }
        publishPart(recordId, recordJson, recordHash)
        references += PallaSyncSnapshotPartReference(partIndex, recordId, recordHash)
        check(partIndex < partCount) { "C_SNAPSHOT_INVALID" }
    }

    private fun canonical(value: JsonObject): String = checkNotNull(PallaSyncCore.canonicalJson(value.toString())) { "C_SNAPSHOT_INVALID" }

    private fun isUuid4(value: String): Boolean =
        runCatching { UUID.fromString(value) }.getOrNull()?.let {
            it.toString() == value && it.version() == UUID_VERSION &&
                it.variant() == UUID_VARIANT
        } ==
            true

    private data class Plan(
        val header: JsonObject,
        val headerJson: String,
        val partCount: Int,
    )

    private class SnapshotPartBuffer(
        compactionId: String,
    ) {
        private val entries = ArrayList<JsonObject>()
        private var entryBytes = 0
        private val emptyFrameBytes =
            canonicalFrame(compactionId, MAX_SNAPSHOT_PARTS, MAX_SNAPSHOT_PARTS).toByteArray(Charsets.UTF_8).size - EMPTY_ARRAY_BYTES

        val isNotEmpty: Boolean
            get() = entries.isNotEmpty()

        fun tryAdd(entry: JsonObject): Boolean {
            val size = PallaSyncCore.canonicalJson(entry.toString())?.toByteArray(Charsets.UTF_8)?.size ?: error("C_SNAPSHOT_INVALID")
            val separators = if (entries.isEmpty()) 0 else entries.size
            if (emptyFrameBytes + entryBytes + separators + size > MAX_PART_FRAME_BYTES) return false
            entries += entry
            entryBytes += size
            return true
        }

        fun frame(
            compactionId: String,
            partIndex: Int,
            partCount: Int,
        ): JsonObject =
            buildJsonObject {
                put("type", "PALLASYNC-SNAPSHOT-v3")
                put("collection", "pallasync.snapshot/3")
                put("compaction_id", compactionId)
                put("part_index", partIndex)
                put("part_count", partCount)
                put("entries", JsonArray(entries))
            }

        private fun canonicalFrame(
            compactionId: String,
            partIndex: Int,
            partCount: Int,
        ): String =
            checkNotNull(
                PallaSyncCore.canonicalJson(
                    buildJsonObject {
                        put("type", "PALLASYNC-SNAPSHOT-v3")
                        put("collection", "pallasync.snapshot/3")
                        put("compaction_id", compactionId)
                        put("part_index", partIndex)
                        put("part_count", partCount)
                        put("entries", buildJsonArray {})
                    }.toString(),
                ),
            ) { "C_SNAPSHOT_INVALID" }

        private companion object {
            const val EMPTY_ARRAY_BYTES = 2
        }
    }

    private companion object {
        const val MAX_SNAPSHOT_PARTS = 65_536
        const val MAX_PART_FRAME_BYTES = 1_048_572
        const val UUID_VERSION = 4
        const val UUID_VARIANT = 2
    }
}
