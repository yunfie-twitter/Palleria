package com.yunfie.illustia.pallasync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Monotone write-ahead journal; retries cannot switch origins or replace accepted evidence. */
@Dao
interface ReseedJournalDao {
    @Query("SELECT * FROM ps_reseed_journal WHERE chain_id=:chain")
    suspend fun get(chain: String): ReseedJournalEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(value: ReseedJournalEntity)

    @Update
    suspend fun update(value: ReseedJournalEntity)

    @Transaction
    suspend fun saveMonotone(value: ReseedJournalEntity) {
        require(value.phase in 0..7 && value.updatedAtMs >= 0) { "C_SCHEMA_VIOLATION" }
        val previous = get(value.chainId)
        if (previous == null) {
            require(
                value.phase == 0 ||
                    (value.phase == 1 && value.stagedLogId != null && value.compactionId != null),
            ) { "C_MISSING_DEPENDENCY" }
            insert(value)
        } else {
            require(
                previous.chainId == value.chainId && previous.sourceLogId == value.sourceLogId &&
                    previous.sourceGeneration == value.sourceGeneration && previous.targetOrigin == value.targetOrigin &&
                    previous.targetPinJson == value.targetPinJson && value.phase >= previous.phase &&
                    immutableOnce(previous.stagedLogId, value.stagedLogId) &&
                    immutableOnce(previous.compactionId, value.compactionId) &&
                    immutableOnce(previous.preparedBundleJson, value.preparedBundleJson) &&
                    immutableOnce(previous.commitBodyJson, value.commitBodyJson) &&
                    immutableOnce(previous.expectedEntriesJson, value.expectedEntriesJson) &&
                    immutableOnce(previous.commitResultJson, value.commitResultJson) &&
                    immutableOnce(previous.transparencyHeadJson, value.transparencyHeadJson) &&
                    monotoneSourceConflict(previous, value) &&
                    monotoneAnchor(previous, value) && monotoneReceipts(previous, value),
            ) { "C_ROLLBACK_DETECTED" }
            update(value)
        }
        check(get(value.chainId) == value) { "C_LOCAL_STORAGE_FULL" }
    }

    private fun immutableOnce(
        old: String?,
        next: String?,
    ): Boolean = old == null || old == next

    private fun monotoneAnchor(
        old: ReseedJournalEntity,
        next: ReseedJournalEntity,
    ): Boolean {
        if (old.transparencyAnchorJson == next.transparencyAnchorJson) return true
        if (old.transparencyAnchorJson == null) return true
        if (next.transparencyAnchorJson == null) return false
        // Signature/link validation belongs to the verifier; keep this checkpoint on one log.
        val oldAnchor = parseAnchor(old.transparencyAnchorJson) ?: return false
        val nextAnchor = parseAnchor(next.transparencyAnchorJson) ?: return false
        return oldAnchor.first == nextAnchor.first && nextAnchor.second >= oldAnchor.second
    }

    private fun monotoneReceipts(
        old: ReseedJournalEntity,
        next: ReseedJournalEntity,
    ): Boolean {
        if (old.verifiedReceiptIndicesJson == next.verifiedReceiptIndicesJson) return true
        val oldIndices = receiptIndices(old.verifiedReceiptIndicesJson) ?: return false
        val nextIndices = receiptIndices(next.verifiedReceiptIndicesJson) ?: return false
        return nextIndices.containsAll(oldIndices)
    }

    private fun monotoneSourceConflict(
        old: ReseedJournalEntity,
        next: ReseedJournalEntity,
    ): Boolean {
        val oldReport = old.sourceConflictReportJson ?: return true
        val nextReport = next.sourceConflictReportJson ?: return false
        return runCatching {
            val previous =
                kotlinx.serialization.json.Json
                    .parseToJsonElement(oldReport)
                    .jsonObject
            val candidate =
                kotlinx.serialization.json.Json
                    .parseToJsonElement(nextReport)
                    .jsonObject
            val sameCut =
                previous.getValue("source_origin") == candidate.getValue("source_origin") &&
                    previous.getValue("source_log_id") == candidate.getValue("source_log_id") &&
                    previous.getValue("source_generation") == candidate.getValue("source_generation") &&
                    (previous["source_authority_hash"] == null ||
                        previous.getValue("source_authority_hash") == candidate.getValue("source_authority_hash")) &&
                    previous.getValue("source_cut_seq") == candidate.getValue("source_cut_seq") &&
                    previous.getValue("source_cut_hash") == candidate.getValue("source_cut_hash") &&
                    previous.getValue("source_cut_parameters_hash") == candidate.getValue("source_cut_parameters_hash") &&
                    previous.getValue("source_cut_epoch_hash") == candidate.getValue("source_cut_epoch_hash")
            val cutSequence = candidate.getValue("source_cut_seq").jsonPrimitive.long
            val oldHead = previous.getValue("observed_head_seq").jsonPrimitive.long
            val nextHead = candidate.getValue("observed_head_seq").jsonPrimitive.long
            val advancesWithNewHash =
                nextHead > oldHead &&
                    previous.getValue("observed_head_hash") != candidate.getValue("observed_head_hash")
            val sameObservedHead =
                if (advancesWithNewHash) {
                    true
                } else {
                    nextHead == oldHead &&
                        previous.getValue("observed_head_hash") == candidate.getValue("observed_head_hash") &&
                        previous.getValue("observed_parameters_hash") == candidate.getValue("observed_parameters_hash") &&
                        previous.getValue("observed_epoch_hash") == candidate.getValue("observed_epoch_hash")
                }
            val coherentRange =
                nextHead >= cutSequence &&
                    candidate.getValue("unimported_entry_count").jsonPrimitive.long == nextHead - cutSequence &&
                    candidate.getValue("unimported_first_seq").jsonPrimitive.long == cutSequence + 1 &&
                    candidate.getValue("unimported_last_seq").jsonPrimitive.long == nextHead
            sameCut && coherentRange && nextHead >= oldHead && sameObservedHead
        }.getOrDefault(false)
    }

    private fun parseAnchor(json: String): Pair<String, Long>? =
        runCatching {
            val anchor =
                kotlinx.serialization.json.Json
                    .parseToJsonElement(json)
                    .jsonObject
            anchor.getValue("transparency_id").jsonPrimitive.content to anchor.getValue("index").jsonPrimitive.long
        }.getOrNull()

    private fun receiptIndices(json: String): Set<Long>? =
        runCatching {
            kotlinx.serialization.json.Json
                .parseToJsonElement(json)
                .jsonArray
                .map { it.jsonPrimitive.long }
                .also { values ->
                    require(values == values.distinct().sorted())
                }.toSet()
        }.getOrNull()
}
