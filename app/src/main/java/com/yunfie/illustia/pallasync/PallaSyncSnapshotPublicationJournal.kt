package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.SnapshotPublicationEntity
import com.yunfie.illustia.pallasync.data.SnapshotPublicationPartEntity
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Room adapter for producer checkpoints and verified relay acknowledgements (§10.11/§16.6). */
internal class PallaSyncSnapshotPublicationJournal(
    private val database: PallaSyncDatabase,
    private val chainId: String,
    private val compactionId: String,
    private val epoch: Long,
) : PallaSyncSnapshotPartJournal {
    private val dao get() = database.snapshotPublicationDao()

    override suspend fun begin(
        header: JsonObject,
        partCount: Int,
    ) {
        check(header["chain_id"]?.jsonPrimitive?.content == chainId) { "C_SNAPSHOT_INVALID" }
        val existing = dao.publication(chainId, compactionId)
        val publication =
            SnapshotPublicationEntity(
                chainId = chainId,
                compactionId = compactionId,
                generation = header.getValue("generation").jsonPrimitive.long,
                epoch = epoch,
                coveredThroughSeq = header.getValue("covered_through_seq").jsonPrimitive.long,
                logId = header.getValue("log_id").jsonPrimitive.content,
                headerJson = checkNotNull(PallaSyncCore.canonicalJson(header.toString())) { "C_SNAPSHOT_INVALID" },
                partCount = partCount,
                status = "preparing",
            )
        if (existing != null) {
            check(
                existing.generation == publication.generation && existing.epoch == publication.epoch &&
                    existing.coveredThroughSeq == publication.coveredThroughSeq && existing.logId == publication.logId &&
                    existing.headerJson == publication.headerJson && existing.partCount == publication.partCount,
            ) { "C_SNAPSHOT_INVALID" }
        }
        dao.begin(publication)
    }

    override suspend fun prepared(partIndex: Int): SnapshotPublicationPartEntity? {
        val publication = dao.publication(chainId, compactionId) ?: error("C_MISSING_DEPENDENCY")
        check(partIndex in 0 until publication.partCount) { "C_SNAPSHOT_INVALID" }
        return dao.part(chainId, compactionId, partIndex)
    }

    override suspend fun retain(part: SnapshotPublicationPartEntity) {
        check(part.chainId == chainId && part.compactionId == compactionId) { "C_SNAPSHOT_INVALID" }
        val publication = dao.publication(chainId, compactionId) ?: error("C_MISSING_DEPENDENCY")
        check(part.partIndex in 0 until publication.partCount) { "C_SNAPSHOT_INVALID" }
        dao.retainPrepared(part)
    }

    suspend fun retainVerifiedReceipt(
        partIndex: Int,
        receiptJson: String,
    ) {
        val part = prepared(partIndex) ?: error("C_MISSING_DEPENDENCY")
        dao.retainReceipt(part, receiptJson)
    }

    /** Persist only complete-batch receipts after each transparency inclusion has been verified. */
    suspend fun verifyAndRetainUploadReceipts(
        records: List<JsonObject>,
        response: JsonObject,
        verifyInclusion: suspend (receipt: JsonObject) -> Unit,
    ) {
        check(records.isNotEmpty()) { "C_SNAPSHOT_INVALID" }
        val submittedById = records.associateBy { it.getValue("record_id").jsonPrimitive.content }
        check(submittedById.size == records.size) { "C_DEVICE_EQUIVOCATION" }
        val accepted = response.getValue("accepted").jsonArray.map { it.jsonPrimitive.content }
        val rejected = response.getValue("rejected").jsonArray
        check(rejected.isEmpty() && accepted.toSet() == submittedById.keys && accepted.size == submittedById.size) {
            "C_ACCEPTANCE_UNPROVEN"
        }
        val receipts = response.getValue("upload_receipts").jsonArray.map { it.jsonObject }
        check(receipts.size == records.size) { "C_ACCEPTANCE_UNPROVEN" }
        val recordsByHash =
            records.associateBy { record ->
                checkNotNull(PallaSyncCore.objectHash(record.toString())) { "C_SNAPSHOT_INVALID" }
            }
        check(recordsByHash.size == records.size) { "C_DEVICE_EQUIVOCATION" }
        val parts = dao.parts(chainId, compactionId).associateBy { it.recordHash }
        check(parts.size == dao.parts(chainId, compactionId).size) { "C_DEVICE_EQUIVOCATION" }
        val receiptsByHash = receipts.associateBy { it.getValue("object_hash").jsonPrimitive.content }
        check(receiptsByHash.size == receipts.size && receiptsByHash.keys == recordsByHash.keys) { "C_ACCEPTANCE_UNPROVEN" }
        val publication = dao.publication(chainId, compactionId) ?: error("C_MISSING_DEPENDENCY")
        for ((objectHash, record) in recordsByHash) {
            val recordId = record.getValue("record_id").jsonPrimitive.content
            val part = parts[objectHash] ?: error("C_MISSING_DEPENDENCY")
            check(part.recordId == recordId && part.signedJson == record.toString()) { "C_RECORD_ID_CONFLICT" }
            val receipt = receiptsByHash.getValue(objectHash)
            check(
                receipt.getValue("chain_id").jsonPrimitive.content == chainId &&
                    receipt.getValue("log_id").jsonPrimitive.content == publication.logId &&
                    receipt.getValue("object_hash").jsonPrimitive.content == part.recordHash &&
                    receipt["relay_seq"] == JsonNull && receipt["entry_hash"] == JsonNull,
            ) { "C_ACCEPTANCE_UNPROVEN" }
            verifyInclusion(receipt)
            retainVerifiedReceipt(part.partIndex, receipt.toString())
        }
    }

    suspend fun finalize(
        commitment: String,
        signedCompactionJson: String,
    ) = dao.finalizeCompaction(chainId, compactionId, commitment, signedCompactionJson)

    suspend fun markPublished(signedCompactionJson: String): Int = dao.markPublished(chainId, compactionId, signedCompactionJson)
}
