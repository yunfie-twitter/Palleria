package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.SnapshotInstallDao
import com.yunfie.illustia.pallasync.data.SnapshotInstallEntity
import com.yunfie.illustia.pallasync.data.SnapshotInstallEntryEntity
import com.yunfie.illustia.pallasync.data.SnapshotInstallPartEntity
import com.yunfie.illustia.pallasync.data.SnapshotPartEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Persists authenticated plaintext parts privately while preventing partial state exposure (§16.6). */
internal class PallaSyncSnapshotStateStager(
    private val dao: SnapshotInstallDao,
    private val chainId: String,
    compaction: JsonObject,
) {
    private val compaction = Json.parseToJsonElement(checkNotNull(PallaSyncCore.canonicalJson(compaction.toString()))).jsonObject
    val compactionId =
        this.compaction
            .getValue("compaction_id")
            .jsonPrimitive.content
    private val partReferences =
        this.compaction
            .getValue("snapshot_parts")
            .jsonArray
            .map { value ->
                val part = value.jsonObject
                part.getValue("record_id").jsonPrimitive.content to part.getValue("record_hash").jsonPrimitive.content
            }

    suspend fun begin() {
        dao.begin(
            SnapshotInstallEntity(
                chainId,
                compactionId,
                this.compaction.toString(),
                partReferences.size,
                this.compaction
                    .getValue("state_sha256")
                    .jsonPrimitive.content,
                this.compaction
                    .getValue("covered_through_seq")
                    .jsonPrimitive.long,
                this.compaction
                    .getValue("covered_log_hash")
                    .jsonPrimitive.content,
                this.compaction
                    .getValue("generation")
                    .jsonPrimitive.long,
                "staging",
            ),
        )
    }

    suspend fun retainVerifiedPart(
        partIndex: Int,
        part: SnapshotPartEntity,
        frame: JsonObject,
    ) {
        val entries = frame.getValue("entries").jsonArray
        val recordId =
            Json
                .parseToJsonElement(part.signedJson)
                .jsonObject
                .getValue("record_id")
                .jsonPrimitive.content
        val (expectedRecordId, expectedRecordHash) = partReferences.getOrNull(partIndex) ?: error("C_SNAPSHOT_INVALID")
        check(
            frame
                .getValue("part_index")
                .jsonPrimitive.content
                .toIntOrNull() == partIndex &&
                frame
                    .getValue("part_count")
                    .jsonPrimitive.content
                    .toIntOrNull() == partReferences.size &&
                recordId == expectedRecordId &&
                part.partHash == expectedRecordHash &&
                PallaSyncCore.objectHash(part.signedJson) == expectedRecordHash,
        ) { "C_SNAPSHOT_INVALID" }
        val rows =
            entries.mapIndexed { entryIndex, entry ->
                SnapshotInstallEntryEntity(
                    chainId,
                    compactionId,
                    partIndex,
                    entryIndex,
                    checkNotNull(PallaSyncCore.canonicalJson(entry.toString())) { "C_SNAPSHOT_INVALID" },
                )
            }
        dao.retainVerifiedPart(
            SnapshotInstallPartEntity(chainId, compactionId, partIndex, recordId, part.partHash, rows.size),
            rows,
        )
    }

    /** Retain a signed part from a fully verified recovery archive without inventing a relay receipt. */
    suspend fun retainVerifiedArchivePart(
        partIndex: Int,
        signedRecordJson: String,
        frame: JsonObject,
    ) {
        val record = Json.parseToJsonElement(signedRecordJson).jsonObject
        val canonical = checkNotNull(PallaSyncCore.canonicalJson(record.toString())) { "C_SNAPSHOT_INVALID" }
        val part =
            SnapshotPartEntity(
                chainId = chainId,
                compactionId = compactionId,
                partIndex = partIndex,
                partHash = checkNotNull(PallaSyncCore.objectHash(canonical)) { "C_SNAPSHOT_INVALID" },
                signedJson = canonical,
                uploadReceiptJson = "null",
                coveredSeq = compaction.getValue("covered_through_seq").jsonPrimitive.long,
                coverageJson = compaction.toString(),
                stagingState = "downloaded",
            )
        retainVerifiedPart(partIndex, part, frame)
    }

    suspend fun complete() = dao.markVerified(chainId, compactionId)

    suspend fun discardUnverified() = dao.discardUnverifiedStaging(chainId, compactionId)
}
