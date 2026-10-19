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
            val restartingUncommittedSnapshot =
                previous.sourceConflictReportJson != null && previous.phase == 3 &&
                    previous.commitBodyJson != null && previous.expectedEntriesJson != null &&
                    previous.commitResultJson == null && previous.transparencyHeadJson == null &&
                    previous.transparencyAnchorJson == null && previous.verifiedReceiptIndicesJson == "[]" &&
                    value.phase == 2 && value.compactionId != previous.compactionId &&
                    value.commitBodyJson == null && value.expectedEntriesJson == null &&
                    value.commitResultJson == null && value.transparencyHeadJson == null &&
                    value.transparencyAnchorJson == null && value.sourceConflictReportJson == null &&
                    value.verifiedReceiptIndicesJson == "[]"
            val monotone =
                previous.chainId == value.chainId && previous.sourceLogId == value.sourceLogId &&
                    previous.sourceGeneration == value.sourceGeneration && previous.targetOrigin == value.targetOrigin &&
                    previous.targetPinJson == value.targetPinJson &&
                    (value.phase >= previous.phase || restartingUncommittedSnapshot) &&
                    immutableOnce(previous.stagedLogId, value.stagedLogId) &&
                    (immutableOnce(previous.compactionId, value.compactionId) || restartingUncommittedSnapshot) &&
                    immutableOnce(previous.preparedBundleJson, value.preparedBundleJson) &&
                    (immutableOnce(previous.commitBodyJson, value.commitBodyJson) || restartingUncommittedSnapshot) &&
                    (immutableOnce(previous.expectedEntriesJson, value.expectedEntriesJson) || restartingUncommittedSnapshot) &&
                    immutableOnce(previous.commitResultJson, value.commitResultJson) &&
                    (immutableOnce(previous.transparencyHeadJson, value.transparencyHeadJson) || restartingUncommittedSnapshot) &&
                    (monotoneSourceConflict(previous, value) || restartingUncommittedSnapshot) &&
                    (monotoneAnchor(previous, value) || restartingUncommittedSnapshot) &&
                    (monotoneReceipts(previous, value) || restartingUncommittedSnapshot)
            require(monotone) {
                "C_ROLLBACK_DETECTED phase=${previous.phase}->${value.phase} restart=$restartingUncommittedSnapshot " +
                    "commit=${previous.commitResultJson != null} sourceConflict=${previous.sourceConflictReportJson != null} " +
                    "compaction=${previous.compactionId == value.compactionId} body=${immutableOnce(
                        previous.commitBodyJson,
                        value.commitBodyJson,
                    )} " +
                    "expected=${immutableOnce(
                        previous.expectedEntriesJson,
                        value.expectedEntriesJson,
                    )} cut=${monotoneSourceConflict(previous, value)} " +
                    "anchor=${monotoneAnchor(previous, value)} receipts=${monotoneReceipts(previous, value)}"
            }
            update(value)
        }
        check(get(value.chainId) == value) { "C_LOCAL_STORAGE_FULL" }
    }

    /**
     * Abandon only an uncommitted snapshot capture whose persisted encrypted parts no longer
     * match the authoritative local state. Old publication rows remain as public evidence; the
     * replacement gets a fresh compaction ID so neither relay objects nor local parts are reused.
     */
    @Transaction
    suspend fun restartUncommittedSnapshot(
        expected: ReseedJournalEntity,
        replacement: ReseedJournalEntity,
    ) {
        val current = get(expected.chainId)
        val safeBeforeCommitBody =
            expected.phase <= 2 && expected.commitBodyJson == null && expected.expectedEntriesJson == null
        val safeAfterKnownSourceConflict =
            expected.phase == 3 && expected.commitBodyJson != null && expected.expectedEntriesJson != null &&
                expected.sourceConflictReportJson != null
        require(current == expected) { "C_ROLLBACK_DETECTED journal" }
        require(safeBeforeCommitBody || safeAfterKnownSourceConflict) {
            "C_ROLLBACK_DETECTED phase=${expected.phase} result=${expected.commitResultJson != null} conflict=${expected.sourceConflictReportJson != null}"
        }
        require(
            expected.commitResultJson == null && expected.transparencyHeadJson == null &&
                expected.transparencyAnchorJson == null && expected.verifiedReceiptIndicesJson == "[]" &&
                replacement.chainId == expected.chainId && replacement.sourceLogId == expected.sourceLogId &&
                replacement.sourceGeneration == expected.sourceGeneration && replacement.targetOrigin == expected.targetOrigin &&
                replacement.stagedLogId == expected.stagedLogId && replacement.compactionId != expected.compactionId &&
                replacement.targetPinJson == expected.targetPinJson &&
                replacement.preparedBundleJson == expected.preparedBundleJson && replacement.commitBodyJson == null &&
                replacement.expectedEntriesJson == null && replacement.commitResultJson == null &&
                replacement.transparencyHeadJson == null && replacement.transparencyAnchorJson == null &&
                replacement.sourceConflictReportJson == null && expected.sourceConflictReportJson != null &&
                replacement.verifiedReceiptIndicesJson == "[]" && replacement.phase == 2 &&
                replacement.updatedAtMs >= expected.updatedAtMs,
        ) { "C_ROLLBACK_DETECTED" }
        update(replacement)
        check(get(replacement.chainId) == replacement) { "C_LOCAL_STORAGE_FULL" }
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
            val sameAuthority =
                previous["source_authority_hash"] == null ||
                    previous.getValue("source_authority_hash") == candidate.getValue("source_authority_hash")
            val sameCut =
                previous.getValue("source_origin") == candidate.getValue("source_origin") &&
                    previous.getValue("source_log_id") == candidate.getValue("source_log_id") &&
                    previous.getValue("source_generation") == candidate.getValue("source_generation") &&
                    sameAuthority &&
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
