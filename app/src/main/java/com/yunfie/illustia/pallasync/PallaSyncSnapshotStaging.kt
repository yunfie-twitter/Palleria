package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.SnapshotDao
import com.yunfie.illustia.pallasync.data.SnapshotPartEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Durable resume journal for exact encrypted snapshot SyncRecords (§16.6). It stores only the
 * immutable signed ciphertext returned by the relay and the already-validated compaction
 * coverage. It does not mark parts authenticated, install state, or authorize pruning.
 */
internal class PallaSyncSnapshotStaging(
    private val dao: SnapshotDao,
    private val chainId: String,
    private val compactionId: String,
    private val generation: Long,
    private val epoch: Long,
    private val coveredThroughSeq: Long,
    validatedCompactionJson: String,
) {
    private val compactionJson: String
    private val partReferences: List<Pair<String, String>>

    val partCount: Int
        get() = partReferences.size

    /** The exact locally verified CompactionRecord that defines this staging set. */
    fun compactionRecord(): JsonObject = Json.parseToJsonElement(compactionJson).jsonObject

    init {
        require(chainId.isNotBlank() && compactionId.isNotBlank() && generation >= 0 && epoch >= 0 && coveredThroughSeq >= 0) {
            "C_SNAPSHOT_INVALID"
        }
        compactionJson = checkNotNull(PallaSyncCore.canonicalJson(validatedCompactionJson)) { "C_SNAPSHOT_INVALID" }
        val compaction = Json.parseToJsonElement(compactionJson).jsonObject
        check(
            compaction.getValue("chain_id").jsonPrimitive.content == chainId &&
                compaction.getValue("compaction_id").jsonPrimitive.content == compactionId &&
                compaction.getValue("generation").jsonPrimitive.long == generation &&
                compaction.getValue("covered_through_seq").jsonPrimitive.long == coveredThroughSeq,
        ) { "C_SNAPSHOT_INVALID" }
        partReferences =
            compaction
                .getValue("snapshot_parts")
                .jsonArray
                .map { value ->
                    val part = value.jsonObject
                    part.getValue("record_id").jsonPrimitive.content to part.getValue("record_hash").jsonPrimitive.content
                }
        check(partReferences.isNotEmpty() && partReferences.size <= MAX_SNAPSHOT_PARTS) { "C_SNAPSHOT_INVALID" }
    }

    suspend fun retainDownloaded(
        partIndex: Int,
        validatedResponseJson: String,
    ): SnapshotPartEntity {
        val (expectedRecordId, expectedHash) = partReferences.getOrNull(partIndex) ?: error("C_SNAPSHOT_INVALID")
        val response = Json.parseToJsonElement(validatedResponseJson).jsonObject
        val signedRecordJson = response.getValue("record").toString()
        val canonicalRecord = checkNotNull(PallaSyncCore.canonicalJson(signedRecordJson)) { "C_SNAPSHOT_INVALID" }
        val uploadReceipt =
            checkNotNull(PallaSyncCore.canonicalJson(response.getValue("upload_receipt").toString())) {
                "C_SNAPSHOT_INVALID"
            }
        val record = Json.parseToJsonElement(canonicalRecord).jsonObject
        check(
            record.getValue("record_id").jsonPrimitive.content == expectedRecordId &&
                record.getValue("chain_id").jsonPrimitive.content == chainId &&
                record.getValue("generation").jsonPrimitive.long == generation &&
                record.getValue("epoch").jsonPrimitive.long == epoch &&
                PallaSyncCore.objectHash(canonicalRecord) == expectedHash,
        ) { "C_SNAPSHOT_INVALID" }
        val part =
            SnapshotPartEntity(
                chainId = chainId,
                compactionId = compactionId,
                partIndex = partIndex,
                partHash = expectedHash,
                signedJson = canonicalRecord,
                uploadReceiptJson = uploadReceipt,
                coveredSeq = coveredThroughSeq,
                coverageJson = compactionJson,
                stagingState = STAGE_DOWNLOADED,
            )
        dao.retainDownloaded(part)
        return checkNotNull(dao.part(chainId, compactionId, partIndex)) { "C_LOCAL_STORAGE_FULL" }
    }

    fun expectedRecordId(partIndex: Int): String = partReferences.getOrNull(partIndex)?.first ?: error("C_SNAPSHOT_INVALID")

    /** Reverify durable receipt evidence on resume; legacy rows without a receipt must be read back again. */
    suspend fun verifiedDownloaded(
        partIndex: Int,
        pinJson: String,
    ): SnapshotPartEntity? {
        val part = dao.part(chainId, compactionId, partIndex) ?: return null
        val reference = partReferences.getOrNull(partIndex) ?: error("C_SNAPSHOT_INVALID")
        check(
            part.chainId == chainId &&
                part.compactionId == compactionId &&
                part.partIndex == partIndex &&
                part.stagingState == STAGE_DOWNLOADED &&
                part.partHash == reference.second &&
                part.coveredSeq == coveredThroughSeq &&
                part.coverageJson == compactionJson,
        ) { "C_SNAPSHOT_INVALID" }
        if (part.uploadReceiptJson != LEGACY_MISSING_RECEIPT) {
            val wrapper =
                buildJsonObject {
                    put("record", Json.parseToJsonElement(part.signedJson))
                    put("upload_receipt", Json.parseToJsonElement(part.uploadReceiptJson))
                }.toString()
            val checked =
                Json
                    .parseToJsonElement(
                        PallaSyncSnapshotCore.validateSnapshotPartResponse(
                            wrapper.toByteArray(),
                            reference.first,
                            pinJson,
                        ),
                    ).jsonObject
            val canonicalRecord =
                checkNotNull(PallaSyncCore.canonicalJson(checked.getValue("record").toString())) {
                    "C_SNAPSHOT_INVALID"
                }
            val canonicalReceipt =
                checkNotNull(PallaSyncCore.canonicalJson(checked.getValue("upload_receipt").toString())) {
                    "C_SNAPSHOT_INVALID"
                }
            check(
                canonicalRecord == part.signedJson &&
                    canonicalReceipt == part.uploadReceiptJson &&
                    PallaSyncCore.objectHash(canonicalRecord) == reference.second,
            ) { "C_SNAPSHOT_INVALID" }
        }
        return part.takeUnless { it.uploadReceiptJson == LEGACY_MISSING_RECEIPT }
    }

    private companion object {
        const val MAX_SNAPSHOT_PARTS = 65_536
        const val STAGE_DOWNLOADED = "downloaded"
        const val LEGACY_MISSING_RECEIPT = "null"
    }
}
