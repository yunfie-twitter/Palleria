package com.yunfie.illustia.pallasync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.yunfie.illustia.pallasync.PallaSyncConflictResolver
import com.yunfie.illustia.pallasync.PallaSyncInnerRecord
import com.yunfie.illustia.pallasync.PallaSyncOperation
import com.yunfie.illustia.pallasync.PallaSyncReplayWork
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private const val MIN_HISTORY_PAGE = 100
private const val MAX_HISTORY_PAGE = 500
internal const val PALLASYNC_SEARCH_SAFETY_LIMIT = 1_000_000L
internal const val PALLASYNC_VIEWED_SAFETY_LIMIT = 1_000_000L
internal const val PALLASYNC_SEEN_SAFETY_LIMIT = 10_000_000L

internal fun historyCeiling(
    collection: String,
    scope: String,
): Long =
    when {
        collection == "palleria.search_history/3" && scope == "all" -> PALLASYNC_SEARCH_SAFETY_LIMIT
        collection == "palleria.view_history/3" && scope == "viewed" -> PALLASYNC_VIEWED_SAFETY_LIMIT
        collection == "palleria.view_history/3" && scope == "seen" -> PALLASYNC_SEEN_SAFETY_LIMIT
        else -> error("C_SCHEMA_VIOLATION")
    }

private fun conflictsWithPlacement(
    old: SignedEventEntity?,
    incoming: SignedEventEntity,
): Boolean = old?.relaySeq != null && incoming.relaySeq != null && (old.relaySeq != incoming.relaySeq || old.logId != incoming.logId)

private const val LIVE_ENTITY_PREDICATE = """
 FROM ps_entities e JOIN ps_history h
        ON e.chain_id=h.chain_id AND e.collection=h.collection AND e.entity_id=h.entity_id
        WHERE e.chain_id=:chainId AND e.collection=:collection AND e.operation='upsert'
        AND NOT EXISTS (SELECT 1 FROM ps_registers c WHERE c.chain_id=h.chain_id AND c.collection=h.collection
          AND c.register_kind='clear' AND (c.scope='all' OR c.scope=h.scope)
          AND (c.lamport>h.lamport OR (c.lamport=h.lamport AND
            (c.op_rank>h.op_rank OR (c.op_rank=h.op_rank AND
              (c.device_id>h.device_id OR (c.device_id=h.device_id AND
                (c.record_id>h.record_id OR (c.record_id=h.record_id AND c.op_index>=h.op_index)))))))))
"""
private const val LIVE_ORDERED_ENTITIES = "SELECT e.*" + LIVE_ENTITY_PREDICATE

private suspend fun ProtocolDao.conflictsWithSnapshotAuthorEvidence(
    signed: SignedEventEntity,
    generation: Long,
    deviceId: String,
    inner: PallaSyncInnerRecord,
    same: AuthorSequenceEntity?,
    previous: AuthorSequenceEntity?,
): Boolean {
    val snapshotPrevious =
        if (inner.deviceSeq > 1 && previous == null) {
            snapshotAuthorHead(signed.chainId, generation, deviceId, inner.deviceSeq - 1)
        } else {
            null
        }
    val snapshotSame =
        if (same == null) snapshotAuthorHead(signed.chainId, generation, deviceId, inner.deviceSeq) else null
    return (snapshotSame != null && snapshotSame.recordHash != signed.objectHash) ||
        (snapshotPrevious != null && snapshotPrevious.recordHash != inner.prevRecordHash)
}

/** Effects of a verified, decrypted record commit together (§11.1/§15.4). */
@Dao
// The logical tables share one transaction boundary; Room requires these accessors on the DAO.
@Suppress("TooManyFunctions", "LargeClass")
interface ProtocolDao {
    @Query("SELECT * FROM ps_migrations WHERE migration_id=:id AND chunk_index=:index")
    suspend fun migrationChunk(
        id: String,
        index: Long,
    ): MigrationJournalEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun putMigrationChunk(value: MigrationJournalEntity)

    @Query("SELECT MAX(chunk_index) FROM ps_migrations WHERE migration_id=:id")
    suspend fun lastMigrationIndex(id: String): Long?

    @Query(
        "SELECT * FROM ps_migrations WHERE migration_id=:id AND state='pending' AND chunk_index>:after ORDER BY chunk_index LIMIT :limit",
    )
    suspend fun pendingMigrationPage(
        id: String,
        after: Long,
        limit: Int,
    ): List<MigrationJournalEntity>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM ps_migrations WHERE migration_id=:id AND state='pending' AND report_json LIKE '%\"blockedCode\":\"C_ORDER_KEY_EXHAUSTED\"%')",
    )
    suspend fun hasBlockedFavoriteOrderReindex(id: String): Boolean

    @Query(
        "SELECT * FROM ps_migrations WHERE migration_id=:id AND state IN ('pending','constructed','receipt_verified','legacy_candidate_excluded') AND source_identity LIKE 'legacy:%' AND source_identity NOT LIKE 'legacy-audit:%' AND chunk_index>:after ORDER BY chunk_index LIMIT :limit",
    )
    suspend fun legacyCandidatePage(
        id: String,
        after: Long,
        limit: Int,
    ): List<MigrationJournalEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun putLegacyMigrationCandidate(value: LegacyMigrationCandidateEntity)

    @Query(
        "SELECT DISTINCT group_key FROM ps_legacy_migration_candidates WHERE migration_id=:id AND group_key>:afterGroupKey ORDER BY group_key LIMIT :limit",
    )
    suspend fun legacyMigrationCandidateGroupPage(
        id: String,
        afterGroupKey: String,
        limit: Int,
    ): List<String>

    @Query(
        "SELECT * FROM ps_legacy_migration_candidates WHERE migration_id=:id AND group_key=:groupKey AND entry_index>:afterEntryIndex ORDER BY entry_index LIMIT :limit",
    )
    suspend fun legacyMigrationCandidatesInGroup(
        id: String,
        groupKey: String,
        afterEntryIndex: Long,
        limit: Int,
    ): List<LegacyMigrationCandidateEntity>

    @Query("SELECT COUNT(*) FROM ps_legacy_migration_candidates WHERE migration_id=:id AND group_key=:groupKey")
    suspend fun legacyMigrationCandidateGroupCount(
        id: String,
        groupKey: String,
    ): Long

    @Query(
        "SELECT * FROM ps_legacy_migration_candidates WHERE migration_id=:id AND group_key=:groupKey AND entry_index=:entryIndex",
    )
    suspend fun legacyMigrationCandidate(
        id: String,
        groupKey: String,
        entryIndex: Long,
    ): LegacyMigrationCandidateEntity?

    @Query("SELECT * FROM ps_legacy_migration_candidates WHERE migration_id=:id AND entry_index=:entryIndex")
    suspend fun legacyMigrationCandidateByEntry(
        id: String,
        entryIndex: Long,
    ): LegacyMigrationCandidateEntity?

    @Query(
        "UPDATE ps_legacy_migration_candidates SET group_key=:groupKey,requires_choice=1 WHERE migration_id=:id AND entry_index=:entryIndex AND group_key=:expectedGroupKey AND requires_choice=:expectedRequiresChoice",
    )
    suspend fun markLegacyMigrationNormalizationCollision(
        id: String,
        entryIndex: Long,
        expectedGroupKey: String,
        groupKey: String,
        expectedRequiresChoice: Boolean,
    ): Int

    @Query("SELECT * FROM ps_legacy_migration_choices WHERE migration_id=:id AND group_key=:groupKey")
    suspend fun legacyMigrationCandidateChoice(
        id: String,
        groupKey: String,
    ): LegacyMigrationChoiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putLegacyMigrationCandidateChoice(value: LegacyMigrationChoiceEntity)

    @Query(
        "UPDATE ps_migrations SET state='legacy_candidate_excluded' WHERE migration_id=:id AND chunk_index=:entryIndex AND source_hash=:sourceHash AND state='pending'",
    )
    suspend fun excludeLegacyMigrationCandidate(
        id: String,
        entryIndex: Long,
        sourceHash: String,
    ): Int

    @Query(
        "UPDATE ps_migrations SET report_json=:report WHERE migration_id=:id AND chunk_index=:entryIndex AND source_hash=:sourceHash AND state='pending'",
    )
    suspend fun updateLegacyMigrationCandidate(
        id: String,
        entryIndex: Long,
        sourceHash: String,
        report: String,
    ): Int

    @Query(
        "SELECT c.* FROM ps_legacy_migration_candidates c JOIN ps_legacy_migration_choices choice ON choice.migration_id=c.migration_id AND choice.group_key=c.group_key AND choice.selected_entry_index=c.entry_index WHERE c.migration_id=:id AND c.schema=:schema AND c.operation='upsert' ORDER BY c.source_sequence,c.candidate_index LIMIT :limit",
    )
    suspend fun selectedLegacyMigrationFavorites(
        id: String,
        schema: String,
        limit: Int,
    ): List<LegacyMigrationCandidateEntity>

    @Query(
        "UPDATE ps_migrations SET report_json=:report WHERE migration_id=:id AND chunk_index=:index AND source_hash=:hash AND state=:state",
    )
    suspend fun updateMigrationReport(
        id: String,
        index: Long,
        hash: String,
        state: String,
        report: String,
    ): Int

    @Query(
        "UPDATE ps_migrations SET state=:nextState,report_json=:report WHERE migration_id=:id AND chunk_index=0 AND source_hash=:hash AND state=:expectedState",
    )
    suspend fun updateMigrationManifest(
        id: String,
        hash: String,
        expectedState: String,
        nextState: String,
        report: String,
    ): Int

    @Query("SELECT COUNT(*) FROM ps_migrations WHERE migration_id=:id AND state='pending' AND source_identity NOT LIKE 'legacy-audit:%'")
    suspend fun pendingMigrationCount(id: String): Long

    @Query("SELECT COUNT(*) FROM ps_migrations WHERE migration_id=:id AND state=:state AND source_identity NOT LIKE 'legacy-audit:%'")
    suspend fun migrationStateCount(
        id: String,
        state: String,
    ): Long

    @Query(
        "SELECT DISTINCT source_identity FROM ps_migrations WHERE migration_id=:id AND state='pending' AND source_identity IN (:collections)",
    )
    suspend fun pendingIntentCollections(
        id: String,
        collections: List<String>,
    ): List<String>

    @Query("SELECT * FROM ps_migrations WHERE migration_id=:id ORDER BY chunk_index LIMIT :limit")
    suspend fun migrationChunks(
        id: String,
        limit: Int,
    ): List<MigrationJournalEntity>

    @Query(
        "SELECT * FROM ps_migrations WHERE migration_id=:id AND state LIKE 'legacy_decision_%' AND chunk_index>:afterIndex ORDER BY chunk_index LIMIT :limit",
    )
    suspend fun legacyDecisionPage(
        id: String,
        afterIndex: Long,
        limit: Int,
    ): List<MigrationJournalEntity>

    @Query(
        "SELECT * FROM ps_migrations WHERE migration_id=:id AND state='legacy_source_evidence' AND chunk_index>:afterSequence ORDER BY chunk_index LIMIT :limit",
    )
    suspend fun legacySourceEvidencePage(
        id: String,
        afterSequence: Long,
        limit: Int,
    ): List<MigrationJournalEntity>

    @Query("SELECT COUNT(*) FROM ps_migrations WHERE migration_id=:id AND state='legacy_source_evidence'")
    suspend fun legacySourceEvidenceCount(id: String): Long

    @Query(
        "SELECT * FROM ps_migrations WHERE chunk_index=0 AND state LIKE 'legacy_%' AND migration_id>:afterId ORDER BY migration_id LIMIT :limit",
    )
    suspend fun legacyMigrationManifestPage(
        afterId: String,
        limit: Int,
    ): List<MigrationJournalEntity>

    @Query("SELECT * FROM ps_migrations WHERE migration_id=:id AND source_identity LIKE 'legacy-audit:%' ORDER BY chunk_index")
    suspend fun legacyMigrationAuditRows(id: String): List<MigrationJournalEntity>

    @Query(
        "UPDATE ps_migrations SET state='constructed',report_json=:report,destination_record_id=:recordId,destination_object_hash=:objectHash WHERE migration_id=:id AND chunk_index=:index AND source_hash=:hash",
    )
    suspend fun bindReplayDestination(
        id: String,
        index: Long,
        hash: String,
        report: String,
        recordId: String,
        objectHash: String,
    ): Int

    @Query(
        "SELECT * FROM ps_migrations WHERE migration_id LIKE 'legacy:%' AND target_chain=:chain AND chunk_index>0 AND source_identity LIKE 'legacy:%' AND source_identity NOT LIKE 'legacy-audit:%' AND destination_record_id=:recordId",
    )
    suspend fun legacyMigrationReceiptCandidates(
        chain: String,
        recordId: String,
    ): List<MigrationJournalEntity>

    @Query(
        "SELECT * FROM ps_migrations WHERE migration_id LIKE 'legacy:%' AND target_chain=:chain AND chunk_index>0 AND source_identity LIKE 'legacy-audit:%' AND destination_record_id=:recordId",
    )
    suspend fun legacyMigrationAuditReceiptCandidates(
        chain: String,
        recordId: String,
    ): List<MigrationJournalEntity>

    @Query(
        "SELECT destination_record_id FROM ps_migrations WHERE migration_id=:id AND target_chain=:chain AND state='receipt_verified' AND source_identity LIKE 'legacy:%' AND source_identity NOT LIKE 'legacy-audit:%' AND destination_record_id>:after ORDER BY destination_record_id LIMIT :limit",
    )
    suspend fun legacyMigrationDestinationPage(
        id: String,
        chain: String,
        after: String,
        limit: Int,
    ): List<String>

    @Query(
        "UPDATE ps_migrations SET state='receipt_verified',verified_receipt_hash=:receiptHash WHERE migration_id=:migrationId AND chunk_index=:entryIndex AND target_chain=:chain AND destination_record_id=:recordId AND destination_object_hash=:objectHash AND state='constructed'",
    )
    suspend fun confirmMigrationReceipt(
        migrationId: String,
        entryIndex: Long,
        chain: String,
        recordId: String,
        objectHash: String,
        receiptHash: String,
    ): Int

    @Query(
        "SELECT * FROM ps_migrations WHERE migration_id=:id AND chunk_index BETWEEN :first AND :last AND target_chain=:chain AND destination_record_id=:recordId AND state IN ('constructed','receipt_verified')",
    )
    suspend fun newRootMigrationReceiptCandidates(
        id: String,
        first: Long,
        last: Long,
        chain: String,
        recordId: String,
    ): List<MigrationJournalEntity>

    @Query("SELECT COUNT(*) FROM ps_migrations WHERE migration_id=:id AND chunk_index BETWEEN :first AND :last")
    suspend fun migrationRangeCount(
        id: String,
        first: Long,
        last: Long,
    ): Long

    @Query("SELECT COUNT(*) FROM ps_migrations WHERE migration_id=:id AND chunk_index BETWEEN :first AND :last AND state=:state")
    suspend fun migrationRangeStateCount(
        id: String,
        first: Long,
        last: Long,
        state: String,
    ): Long

    /** Invoked inside the same Room transaction as the exact signed record/outbox/tail. */
    suspend fun commitReplay(
        migration: String,
        index: Long,
        sourceHash: String,
        chain: String,
        record: String,
    ) {
        val row = checkNotNull(migrationChunk(migration, index)) { "C_MISSING_DEPENDENCY" }
        check(row.sourceHash == sourceHash && row.targetChain == chain && row.state in setOf("pending", "constructed")) {
            "C_ROLLBACK_DETECTED"
        }
        val work = Json.decodeFromString<PallaSyncReplayWork>(row.reportJson)
        check(work.destinationRecordId == null || work.destinationRecordId == record) { "C_RECORD_ID_CONFLICT" }
        val destinationHash = checkNotNull(event(chain, record)?.objectHash) { "C_ACCEPTANCE_UNPROVEN" }
        check(row.destinationRecordId == null || row.destinationRecordId == record) { "C_RECORD_ID_CONFLICT" }
        check(row.destinationObjectHash == null || row.destinationObjectHash == destinationHash) { "C_RECORD_ID_CONFLICT" }
        check(
            bindReplayDestination(
                migration,
                index,
                sourceHash,
                Json.encodeToString(work.copy(destinationRecordId = record, blockedCode = null)),
                record,
                destinationHash,
            ) ==
                1,
        ) { "C_LOCAL_STORAGE_FULL" }
    }

    @Query(
        "UPDATE ps_events SET validation_state='abandoned_unaccepted' WHERE chain_id=:chain AND record_id=:id AND object_hash=:hash AND relay_seq IS NULL AND receipt_json IS NULL",
    )
    suspend fun retainAbandonedEvent(
        chain: String,
        id: String,
        hash: String,
    ): Int

    @Query("SELECT signed_json FROM ps_evidence WHERE chain_id=:chain AND kind='record_acceptance' AND object_hash=:hash LIMIT 1")
    suspend fun recordAcceptance(
        chain: String,
        hash: String,
    ): String?

    @Query("SELECT * FROM ps_log WHERE chain_id=:chainId AND log_id=:logId AND relay_seq=:sequence")
    suspend fun logEntry(
        chainId: String,
        logId: String,
        sequence: Long,
    ): LogEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun putLogEntry(entry: LogEntryEntity)

    @Transaction
    suspend fun retainLogEntry(entry: LogEntryEntity) {
        val old = logEntry(entry.chainId, entry.logId, entry.relaySeq)
        check(old == null || old.copy(validationState = entry.validationState) == entry) { "C_LOG_HASH_MISMATCH" }
        if (old == null) putLogEntry(entry)
    }

    @Query(
        "SELECT * FROM ps_log WHERE chain_id=:chainId AND log_id=:logId AND relay_seq>:after AND relay_seq<=:through ORDER BY relay_seq LIMIT :limit",
    )
    suspend fun logPage(
        chainId: String,
        logId: String,
        after: Long,
        through: Long,
        limit: Int,
    ): List<LogEntryEntity>

    @Query("SELECT DISTINCT log_id FROM ps_log WHERE chain_id=:chainId AND validation_state='authority_verified' ORDER BY log_id")
    suspend fun repairSourceLogIds(chainId: String): List<String>

    @Query(
        "SELECT DISTINCT log_id FROM ps_log WHERE chain_id=:chainId " +
            "AND validation_state IN ('authority_verified','materialized') ORDER BY log_id",
    )
    suspend fun importSourceLogIds(chainId: String): List<String>

    @Query("SELECT MAX(relay_seq) FROM ps_log WHERE chain_id=:chainId AND log_id=:logId AND validation_state='authority_verified'")
    suspend fun repairSourceHighWater(
        chainId: String,
        logId: String,
    ): Long?

    @Query("SELECT MAX(relay_seq) FROM ps_log WHERE chain_id=:chainId AND log_id=:logId")
    suspend fun importSourceHighWater(
        chainId: String,
        logId: String,
    ): Long?

    @Query(
        "UPDATE ps_events SET log_id=:logId,relay_seq=:relaySeq,receipt_json=:receipt,validation_state='materialized' WHERE chain_id=:chainId AND record_id=:recordId AND object_hash=:objectHash",
    )
    suspend fun rebindRepairedEvent(
        chainId: String,
        recordId: String,
        objectHash: String,
        logId: String,
        relaySeq: Long,
        receipt: String,
    ): Int

    @Transaction
    suspend fun retainRepairedEvent(repaired: SignedEventEntity) {
        val old = event(repaired.chainId, repaired.recordId)
        if (old == null) {
            check(removedRecordId(repaired.chainId, repaired.recordId) == null) { "C_RECORD_ID_CONFLICT" }
            insertEvent(repaired)
        } else {
            check(old.objectHash == repaired.objectHash && old.signedJson == repaired.signedJson) { "C_RECORD_ID_CONFLICT" }
            check(
                rebindRepairedEvent(
                    repaired.chainId,
                    repaired.recordId,
                    repaired.objectHash,
                    repaired.logId,
                    checkNotNull(repaired.relaySeq),
                    checkNotNull(repaired.receiptJson),
                ) == 1,
            ) { "C_LOCAL_STORAGE_FULL" }
        }
    }

    @Query(
        "UPDATE ps_log SET validation_state=:state WHERE chain_id=:chainId AND log_id=:logId AND relay_seq=:sequence AND entry_hash=:hash",
    )
    suspend fun markLogEntry(
        chainId: String,
        logId: String,
        sequence: Long,
        hash: String,
        state: String,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun putAuthority(authority: AuthorityHistoryEntity)

    @Query("SELECT * FROM ps_authority WHERE chain_id=:chain AND kind=:kind AND object_hash=:hash")
    suspend fun authorityObject(
        chain: String,
        kind: String,
        hash: String,
    ): AuthorityHistoryEntity?

    @Query(
        "SELECT * FROM ps_authority WHERE chain_id=:chain AND kind='checkpoint' AND generation=:generation AND validation_state='archived_checkpoint' ORDER BY object_hash LIMIT 1",
    )
    suspend fun archivedAuthority(
        chain: String,
        generation: Long,
    ): AuthorityHistoryEntity?

    @Query(
        "SELECT * FROM ps_authority WHERE chain_id=:chain AND kind='checkpoint' AND generation=:generation " +
            "AND validation_state='archived_checkpoint' ORDER BY object_hash",
    )
    suspend fun archivedAuthorities(
        chain: String,
        generation: Long,
    ): List<AuthorityHistoryEntity>

    @Query(
        "SELECT * FROM ps_authority WHERE chain_id=:chain AND kind='checkpoint' AND generation=:generation AND validation_state='reseed_candidate' ORDER BY object_hash LIMIT 1",
    )
    suspend fun reseedCandidateAuthority(
        chain: String,
        generation: Long,
    ): AuthorityHistoryEntity?

    @Transaction
    suspend fun retainAuthority(authority: AuthorityHistoryEntity) {
        val old = authorityObject(authority.chainId, authority.kind, authority.objectHash)
        check(old == null || old == authority) { "C_LOG_HASH_MISMATCH" }
        if (old == null) putAuthority(authority)
    }

    @Query("SELECT signed_json FROM ps_authority WHERE chain_id=:chainId AND kind='checkpoint' AND validation_state='current_checkpoint'")
    suspend fun currentAuthority(chainId: String): String?

    @Query(
        "SELECT * FROM ps_authority WHERE chain_id=:chain AND kind=:kind AND validation_state='authority_verified' ORDER BY object_hash LIMIT 1",
    )
    suspend fun firstAuthorityOfKind(
        chain: String,
        kind: String,
    ): AuthorityHistoryEntity?

    @Query("UPDATE ps_authority SET validation_state='archived_checkpoint' WHERE chain_id=:chainId AND kind='checkpoint'")
    suspend fun archiveAuthority(chainId: String)

    @Query(
        "UPDATE ps_authority SET validation_state='archived_checkpoint' WHERE chain_id=:chainId AND kind='checkpoint' " +
            "AND validation_state='current_checkpoint'",
    )
    suspend fun archiveCurrentAuthority(chainId: String): Int

    @Query(
        "UPDATE ps_authority SET validation_state='current_checkpoint' WHERE chain_id=:chain AND kind='checkpoint' " +
            "AND object_hash=:hash AND validation_state='reseed_candidate'",
    )
    suspend fun promoteReseedCandidate(
        chain: String,
        hash: String,
    ): Int

    @Transaction
    suspend fun installReseedAuthority(
        chain: String,
        hash: String,
        authorityJson: String,
    ) {
        if (currentAuthority(chain) == authorityJson) return
        val candidate = checkNotNull(authorityObject(chain, "checkpoint", hash)) { "C_MISSING_DEPENDENCY" }
        check(candidate.signedJson == authorityJson && candidate.validationState == "reseed_candidate") { "C_ROLLBACK_DETECTED" }
        check(archiveCurrentAuthority(chain) == 1 && promoteReseedCandidate(chain, hash) == 1) { "C_LOCAL_STORAGE_FULL" }
        check(currentAuthority(chain) == authorityJson) { "C_LOCAL_STORAGE_FULL" }
    }

    @Transaction
    suspend fun commitAuthority(authority: AuthorityHistoryEntity) {
        if (currentAuthority(authority.chainId) == authority.signedJson) return
        archiveAuthority(authority.chainId)
        putAuthority(authority)
    }

    @Query("SELECT * FROM ps_events WHERE chain_id=:chainId AND record_id=:recordId")
    suspend fun event(
        chainId: String,
        recordId: String,
    ): SignedEventEntity?

    @Query(
        "SELECT record_id FROM ps_events WHERE chain_id=:chain AND relay_seq<=:through AND validation_state='materialized' AND record_id>:after ORDER BY record_id LIMIT :limit",
    )
    suspend fun materializedEventIdPage(
        chain: String,
        through: Long,
        after: String,
        limit: Int,
    ): List<String>

    @Query("SELECT * FROM ps_events WHERE chain_id=:chainId AND relay_seq IS NOT NULL ORDER BY relay_seq LIMIT 1")
    suspend fun firstAcceptedEvent(chainId: String): SignedEventEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: SignedEventEntity)

    @Query(
        "UPDATE ps_events SET log_id=:logId,relay_seq=:sequence,receipt_json=COALESCE(:receipt,receipt_json),validation_state='materialized' WHERE chain_id=:chain AND record_id=:id AND object_hash=:hash",
    )
    suspend fun bindAcceptedEvent(
        chain: String,
        id: String,
        hash: String,
        logId: String,
        sequence: Long,
        receipt: String?,
    ): Int

    @Query("SELECT * FROM ps_authors WHERE chain_id=:chainId AND generation=:generation AND device_id=:deviceId AND device_seq=:seq")
    suspend fun author(
        chainId: String,
        generation: Long,
        deviceId: String,
        seq: Long,
    ): AuthorSequenceEntity?

    @Query(
        "SELECT * FROM ps_snapshot_author_heads WHERE chain_id=:chainId AND generation=:generation AND device_id=:deviceId AND device_seq=:seq",
    )
    suspend fun snapshotAuthorHead(
        chainId: String,
        generation: Long,
        deviceId: String,
        seq: Long,
    ): SnapshotAuthorHeadEntity?

    @Query(
        "SELECT * FROM ps_snapshot_author_gaps WHERE chain_id=:chainId AND generation=:generation AND device_id=:deviceId AND first_seq<=:sequence AND last_seq>=:sequence ORDER BY first_seq",
    )
    suspend fun coveringSnapshotAuthorGaps(
        chainId: String,
        generation: Long,
        deviceId: String,
        sequence: Long,
    ): List<SnapshotAuthorGapEntity>

    @Query(
        "DELETE FROM ps_snapshot_author_gaps WHERE chain_id=:chainId AND generation=:generation AND device_id=:deviceId AND first_seq=:first AND last_seq=:last",
    )
    suspend fun deleteSnapshotAuthorGap(
        chainId: String,
        generation: Long,
        deviceId: String,
        first: Long,
        last: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSnapshotAuthorGap(gap: SnapshotAuthorGapEntity)

    @Transaction
    suspend fun resolveSnapshotAuthorGaps(
        chainId: String,
        generation: Long,
        deviceId: String,
        sequence: Long,
    ) {
        coveringSnapshotAuthorGaps(chainId, generation, deviceId, sequence).forEach { gap ->
            check(deleteSnapshotAuthorGap(chainId, generation, deviceId, gap.first, gap.last) == 1) { "C_LOCAL_STORAGE_FULL" }
            if (gap.first < sequence) insertSnapshotAuthorGap(gap.copy(last = sequence - 1))
            if (sequence < gap.last) insertSnapshotAuthorGap(gap.copy(first = sequence + 1))
        }
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuthor(author: AuthorSequenceEntity)

    @Query(
        "UPDATE ps_authors SET gap_before=0 WHERE chain_id=:chainId AND generation=:generation AND device_id=:deviceId AND device_seq=:seq",
    )
    suspend fun resolveAuthorGap(
        chainId: String,
        generation: Long,
        deviceId: String,
        seq: Long,
    )

    @Query("SELECT * FROM ps_entities WHERE chain_id=:chainId AND collection=:collection AND entity_id=:id")
    suspend fun entity(
        chainId: String,
        collection: String,
        id: String,
    ): EntityRegisterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putEntity(entity: EntityRegisterEntity)

    @Query("SELECT * FROM ps_registers WHERE chain_id=:chainId AND collection=:collection AND scope=:scope AND register_kind=:kind")
    suspend fun register(
        chainId: String,
        collection: String,
        scope: String,
        kind: String,
    ): ScopeRegisterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRegister(register: ScopeRegisterEntity)

    @Query("DELETE FROM ps_history WHERE chain_id=:chainId AND collection=:collection AND entity_id=:id")
    suspend fun deleteIndex(
        chainId: String,
        collection: String,
        id: String,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putIndex(index: HistoryIndexEntity)

    @Query("UPDATE pallasync_chain_state SET lamport=MAX(lamport,:lamport),index_revision=index_revision+1 WHERE chainId=:chainId")
    suspend fun observeLamport(
        chainId: String,
        lamport: Long,
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun retainEvidence(evidence: ProtectedEvidenceEntity)

    @Query("SELECT * FROM ps_removed_ids WHERE chain_id=:chainId AND record_id=:recordId")
    suspend fun removedRecordId(
        chainId: String,
        recordId: String,
    ): RemovedRecordIdEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRemovedRecordId(value: RemovedRecordIdEntity)

    @Transaction
    suspend fun retainRemovedRecordId(value: RemovedRecordIdEntity) {
        val current = removedRecordId(value.chainId, value.recordId)
        if (current == null) {
            val event = event(value.chainId, value.recordId)
            check(event == null || event.objectHash == value.recordHash) { "C_RECORD_ID_CONFLICT" }
            insertRemovedRecordId(value)
        } else {
            check(current.recordHash == value.recordHash) { "C_RECORD_ID_CONFLICT" }
            check(current.evidenceJson == value.evidenceJson || current.sourceLogId == value.sourceLogId) {
                "C_RECORD_ID_CONFLICT"
            }
        }
    }

    @Query(
        "SELECT signed_json FROM ps_evidence WHERE origin=:origin AND relay_key_id=:keyId AND chain_id=:chainId AND log_id=:logId AND kind=:kind AND sequence=:sequence LIMIT 2",
    )
    suspend fun evidenceAt(
        origin: String,
        keyId: String,
        chainId: String,
        logId: String,
        kind: String,
        sequence: Long,
    ): List<String>

    @Query(
        "SELECT DISTINCT signed_json FROM ps_evidence WHERE origin=:origin AND relay_key_id=:keyId AND chain_id=:chainId AND log_id=:logId AND kind='deletion_evidence_bundle' AND sequence=:sequence ORDER BY object_hash",
    )
    suspend fun deletionEvidenceArchives(
        origin: String,
        keyId: String,
        chainId: String,
        logId: String,
        sequence: Long,
    ): List<String>

    /** All locally retained, revision-bound public bundles for post-deletion export. */
    @Query(
        "SELECT * FROM ps_evidence WHERE chain_id=:chainId AND log_id=:logId AND kind='deletion_evidence_bundle' ORDER BY sequence,object_hash",
    )
    suspend fun deletionEvidenceArchiveHistory(
        chainId: String,
        logId: String,
    ): List<ProtectedEvidenceEntity>

    @Query(
        "SELECT DISTINCT signed_json FROM ps_evidence WHERE origin=:origin AND relay_key_id=:keyId AND chain_id=:chainId AND log_id=:logId AND kind=:kind AND sequence=:sequence ORDER BY object_hash",
    )
    suspend fun deletionProofsAtSequence(
        origin: String,
        keyId: String,
        chainId: String,
        logId: String,
        kind: String,
        sequence: Long,
    ): List<String>

    @Query(
        "SELECT DISTINCT sequence,signed_json FROM ps_evidence WHERE origin=:origin AND relay_key_id=:keyId AND chain_id=:chainId AND kind='transparency' AND sequence>:after AND sequence<=:through ORDER BY sequence LIMIT :limit",
    )
    suspend fun transparencyEvidencePage(
        origin: String,
        keyId: String,
        chainId: String,
        after: Long,
        through: Long,
        limit: Int,
    ): List<TransparencyEvidenceRow>

    @Query(
        "SELECT sequence FROM ps_evidence WHERE origin=:origin AND relay_key_id=:keyId AND chain_id=:chainId AND kind='transparency' AND sequence>:after AND sequence<=:through GROUP BY sequence HAVING COUNT(DISTINCT object_hash)>1 ORDER BY sequence LIMIT 1",
    )
    suspend fun transparencyEvidenceConflict(
        origin: String,
        keyId: String,
        chainId: String,
        after: Long,
        through: Long,
    ): Long?

    @Query("SELECT signed_json FROM ps_evidence WHERE chain_id=:chainId AND log_id=:logId AND kind=:kind ORDER BY sequence DESC LIMIT 1")
    suspend fun latestEvidence(
        chainId: String,
        logId: String,
        kind: String,
    ): String?

    @Query("DELETE FROM ps_registry WHERE chain_id=:chain")
    suspend fun deleteRegistry(chain: String)

    @Query("DELETE FROM ps_epochs WHERE chain_id=:chain")
    suspend fun deleteEpochs(chain: String)

    @Query("DELETE FROM ps_events WHERE chain_id=:chain")
    suspend fun deleteEvents(chain: String)

    @Query("DELETE FROM ps_authors WHERE chain_id=:chain")
    suspend fun deleteAuthors(chain: String)

    @Query("DELETE FROM ps_snapshot_author_heads WHERE chain_id=:chain")
    suspend fun deleteSnapshotAuthorHeads(chain: String)

    @Query("DELETE FROM ps_snapshot_author_gaps WHERE chain_id=:chain")
    suspend fun deleteSnapshotAuthorGaps(chain: String)

    @Query("DELETE FROM ps_entities WHERE chain_id=:chain")
    suspend fun deleteEntities(chain: String)

    @Query("DELETE FROM ps_registers WHERE chain_id=:chain")
    suspend fun deleteRegisters(chain: String)

    @Query("DELETE FROM ps_history WHERE chain_id=:chain")
    suspend fun deleteHistory(chain: String)

    @Query("DELETE FROM ps_quarantine WHERE chain_id=:chain")
    suspend fun deleteQuarantine(chain: String)

    @Query("DELETE FROM ps_projections WHERE chain_id=:chain")
    suspend fun deleteProjections(chain: String)

    @Query("DELETE FROM ps_log WHERE chain_id=:chain")
    suspend fun deleteLog(chain: String)

    @Query("DELETE FROM ps_snapshots WHERE chain_id=:chain")
    suspend fun deleteSnapshots(chain: String)

    @Query("DELETE FROM ps_authority WHERE chain_id=:chain")
    suspend fun deleteAuthority(chain: String)

    @Query("DELETE FROM ps_migrations WHERE target_chain=:chain OR source_identity=:chain")
    suspend fun deleteChainMigrations(chain: String)

    @Query(
        "DELETE FROM ps_evidence WHERE chain_id=:chain AND kind NOT IN " +
            "('delete_receipt','deletion_evidence_bundle','delete_request','deletion_authority')",
    )
    suspend fun deleteNonDeletionEvidence(chain: String)

    @Transaction
    suspend fun retainDeletionProof(proof: ProtectedEvidenceEntity) {
        retainEvidence(proof)
        val retained =
            if (proof.kind == "deletion_evidence_bundle") {
                deletionEvidenceArchives(proof.origin, proof.relayKeyId, proof.chainId, proof.logId, proof.sequence)
            } else if (proof.kind == "delete_receipt") {
                deletionProofsAtSequence(
                    proof.origin,
                    proof.relayKeyId,
                    proof.chainId,
                    proof.logId,
                    proof.kind,
                    proof.sequence,
                )
            } else {
                evidenceAt(proof.origin, proof.relayKeyId, proof.chainId, proof.logId, proof.kind, proof.sequence)
            }
        check(
            retained.contains(proof.signedJson),
        ) {
            "C_LOCAL_STORAGE_FULL"
        }
    }

    @Transaction
    suspend fun eraseChainState(chainId: String) {
        deleteRegistry(chainId)
        deleteEpochs(chainId)
        deleteEvents(chainId)
        deleteAuthors(chainId)
        deleteSnapshotAuthorHeads(chainId)
        deleteSnapshotAuthorGaps(chainId)
        deleteEntities(chainId)
        deleteRegisters(chainId)
        deleteHistory(chainId)
        deleteQuarantine(chainId)
        deleteProjections(chainId)
        deleteLog(chainId)
        deleteSnapshots(chainId)
        deleteAuthority(chainId)
        deleteChainMigrations(chainId)
        deleteNonDeletionEvidence(chainId)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun quarantine(evidence: QuarantineEvidenceEntity)

    @Query("SELECT * FROM ps_quarantine WHERE chain_id=:chainId AND object_hash=:objectHash LIMIT 1")
    suspend fun quarantineEvidence(
        chainId: String,
        objectHash: String,
    ): QuarantineEvidenceEntity?

    @Query(
        "UPDATE ps_quarantine SET resolution_state='resolved' WHERE chain_id=:chainId AND object_hash=:objectHash AND reason=:reason AND resolution_state IN ('unresolved','pending_resolution')",
    )
    suspend fun resolveQuarantineEvidence(
        chainId: String,
        objectHash: String,
        reason: String,
    ): Int

    @Query(
        "UPDATE ps_quarantine SET resolution_state='excluded' WHERE chain_id=:chainId AND object_hash=:objectHash AND reason=:reason AND resolution_state IN ('unresolved','pending_resolution')",
    )
    suspend fun recordExplicitQuarantineExclusion(
        chainId: String,
        objectHash: String,
        reason: String,
    ): Int

    @Query(
        "SELECT (SELECT COUNT(*) FROM ps_authors WHERE chain_id=:chainId AND gap_before=1) + " +
            "(SELECT COUNT(*) FROM ps_snapshot_author_gaps WHERE chain_id=:chainId)",
    )
    suspend fun unresolvedAuthorGapCount(chainId: String): Long

    @Query("SELECT COUNT(*) FROM ps_quarantine WHERE chain_id=:chainId AND resolution_state!='resolved'")
    suspend fun unresolvedQuarantineCount(chainId: String): Long

    @Query(
        "SELECT object_hash,reason,resolution_state FROM ps_quarantine WHERE chain_id=:chainId AND resolution_state!='resolved' AND object_hash>:afterHash ORDER BY object_hash LIMIT :limit",
    )
    suspend fun unresolvedQuarantinePage(
        chainId: String,
        afterHash: String,
        limit: Int,
    ): List<QuarantineSummaryRow>

    @Query("SELECT index_revision FROM pallasync_chain_state WHERE chainId=:chainId")
    suspend fun indexRevision(chainId: String): Long?

    // A scoped clear keeps its full rejecting floor. Query only a bounded visible page.
    @Query(
        """
        SELECT h.* FROM ps_history h WHERE h.chain_id=:chainId AND h.collection=:collection AND h.scope=:scope
        AND (:authorDevice IS NULL OR h.device_id=:authorDevice)
        AND (:afterKey IS NULL OR h.last_access_seq<:afterKey OR (h.last_access_seq=:afterKey AND h.entity_id<:afterId COLLATE BINARY))
        AND NOT EXISTS (SELECT 1 FROM ps_registers c WHERE c.chain_id=h.chain_id AND c.collection=h.collection
          AND c.register_kind='clear' AND (c.scope='all' OR c.scope=h.scope)
          AND (c.lamport>h.lamport OR (c.lamport=h.lamport AND
            (c.op_rank>h.op_rank OR (c.op_rank=h.op_rank AND
              (c.device_id>h.device_id OR (c.device_id=h.device_id AND
                (c.record_id>h.record_id OR (c.record_id=h.record_id AND c.op_index>=h.op_index)))))))))
        ORDER BY h.last_access_seq DESC,h.entity_id COLLATE BINARY DESC LIMIT :limit
    """,
    )
    suspend fun historyPage(
        chainId: String,
        collection: String,
        scope: String,
        afterKey: ByteArray?,
        afterId: String,
        limit: Int,
        authorDevice: ByteArray? = null,
    ): List<HistoryIndexEntity>

    @Query(
        LIVE_ORDERED_ENTITIES +
            " ORDER BY e.order_key COLLATE BINARY,e.lamport,e.op_rank,e.device_id,e.record_id,e.op_index,e.entity_id COLLATE BINARY LIMIT :limit",
    )
    suspend fun orderedEntities(
        chainId: String,
        collection: String,
        limit: Int,
    ): List<EntityRegisterEntity>

    @Query(
        LIVE_ORDERED_ENTITIES +
            " ORDER BY e.order_key COLLATE BINARY DESC,e.lamport DESC,e.op_rank DESC,e.device_id DESC,e.record_id DESC,e.op_index DESC,e.entity_id COLLATE BINARY DESC LIMIT 1",
    )
    suspend fun lastOrderedEntity(
        chainId: String,
        collection: String,
    ): EntityRegisterEntity?

    // A membership lookup must include the cold index, not just the displayed page (§16.5).
    @Query("SELECT e.entity_id" + LIVE_ENTITY_PREDICATE + " AND e.entity_id IN (:ids)")
    suspend fun liveEntityIds(
        chainId: String,
        collection: String,
        ids: List<String>,
    ): List<String>

    @Query("SELECT COUNT(*)" + LIVE_ENTITY_PREDICATE + " AND h.scope=:scope")
    suspend fun liveScopeCount(
        chainId: String,
        collection: String,
        scope: String,
    ): Long

    @Query(
        "SELECT EXISTS(SELECT 1 FROM ps_registers WHERE chain_id=:chainId AND collection=:collection " +
            "AND register_kind='clear' AND scope IN ('all',:scope))",
    )
    suspend fun hasRelevantClear(
        chainId: String,
        collection: String,
        scope: String,
    ): Boolean

    /** ps_history already contains only the winning upsert for an entity; avoid a 10M-row entity join when no clear exists. */
    @Query("SELECT COUNT(*) FROM ps_history WHERE chain_id=:chainId AND collection=:collection AND scope=:scope")
    suspend fun indexedHistoryCount(
        chainId: String,
        collection: String,
        scope: String,
    ): Long

    @Query(
        "SELECT * FROM ps_history WHERE chain_id=:chainId AND collection=:collection AND scope=:scope " +
            "ORDER BY last_access_seq DESC,entity_id COLLATE BINARY DESC LIMIT 1 OFFSET :offset",
    )
    suspend fun indexedProjectionFloor(
        chainId: String,
        collection: String,
        scope: String,
        offset: Long,
    ): HistoryIndexEntity?

    @Query(
        "SELECT * FROM ps_history WHERE chain_id=:chainId AND collection=:collection AND scope=:scope " +
            "ORDER BY last_access_seq ASC,entity_id COLLATE BINARY ASC LIMIT 1",
    )
    suspend fun indexedOldestHistory(
        chainId: String,
        collection: String,
        scope: String,
    ): HistoryIndexEntity?

    @Query(
        "SELECT h.*" + LIVE_ENTITY_PREDICATE +
            " AND h.scope=:scope ORDER BY h.last_access_seq DESC,h.entity_id COLLATE BINARY DESC LIMIT 1 OFFSET :offset",
    )
    suspend fun projectionFloor(
        chainId: String,
        collection: String,
        scope: String,
        offset: Long,
    ): HistoryIndexEntity?

    @Query("SELECT * FROM ps_projections WHERE chain_id=:chainId AND collection=:collection AND scope=:scope")
    suspend fun savedProjection(
        chainId: String,
        collection: String,
        scope: String,
    ): ProjectionStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putProjection(state: ProjectionStateEntity)

    @Query(
        "SELECT e.entity_id" + LIVE_ENTITY_PREDICATE +
            " AND h.scope=:scope AND e.entity_id IN (:ids) AND NOT EXISTS " +
            "(SELECT 1 FROM ps_projections p WHERE p.chain_id=h.chain_id AND p.collection=h.collection AND p.scope=h.scope " +
            "AND p.user_limit IS NOT NULL AND p.eviction_key IS NOT NULL AND " +
            "(h.last_access_seq<p.eviction_key OR (h.last_access_seq=p.eviction_key AND h.entity_id<p.eviction_entity COLLATE BINARY)))",
    )
    suspend fun retainedHistoryIds(
        chainId: String,
        collection: String,
        scope: String,
        ids: List<String>,
    ): List<String>

    @Transaction
    suspend fun historyProjection(
        chainId: String,
        collection: String,
        scope: String,
    ): ProjectionStateEntity {
        val revision = indexRevision(chainId) ?: error("C_MISSING_DEPENDENCY")
        savedProjection(chainId, collection, scope)?.takeIf { it.indexRevision == revision }?.let { return it }
        val policy =
            register(chainId, collection, scope, "retention")?.bodyJson?.let {
                kotlinx.serialization.json.Json
                    .decodeFromString<com.yunfie.illustia.pallasync.PallaSyncRetentionPolicy>(it)
            }
        val ceiling = historyCeiling(collection, scope)
        val limit = minOf(policy?.limit ?: ceiling, ceiling)
        val hasClear = hasRelevantClear(chainId, collection, scope)
        val count =
            if (hasClear) {
                liveScopeCount(chainId, collection, scope)
            } else {
                indexedHistoryCount(chainId, collection, scope)
            }
        val floor =
            if (count >= limit) {
                if (hasClear) {
                    projectionFloor(chainId, collection, scope, limit - 1)
                } else if (count == limit) {
                    indexedOldestHistory(chainId, collection, scope)
                } else {
                    indexedProjectionFloor(chainId, collection, scope, limit - 1)
                }
            } else {
                null
            }
        return ProjectionStateEntity(
            chainId,
            collection,
            scope,
            count,
            limit,
            policy?.limit,
            floor?.lastAccessSeq,
            floor?.entityId,
            revision,
        ).also { putProjection(it) }
    }

    /** A local preference only. Remote materialization never calls this guard (§16.4). */
    suspend fun requireLocalHistoryWrite(
        chainId: String,
        collection: String,
        op: PallaSyncOperation,
    ) {
        if (op.operation != "upsert" || op.entityId == "*" ||
            collection !in listOf("palleria.search_history/3", "palleria.view_history/3")
        ) {
            return
        }
        val scope = if (collection == "palleria.view_history/3") op.entityId.substringBefore(':') else "all"
        val policy =
            register(chainId, collection, scope, "retention")?.bodyJson?.let {
                kotlinx.serialization.json.Json
                    .decodeFromString<com.yunfie.illustia.pallasync.PallaSyncRetentionPolicy>(it)
            }
        if (policy?.stopOnSafetyLimit != true || liveEntityIds(chainId, collection, listOf(op.entityId)).isNotEmpty()) return
        check(liveScopeCount(chainId, collection, scope) < historyCeiling(collection, scope)) { "C_WRITE_PAUSED_BY_POLICY" }
    }

    @Transaction
    suspend fun historySlice(
        chainId: String,
        collection: String,
        scope: String,
        cursor: HistoryCursor? = null,
        pageSize: Int = 200,
        authorDevice: ByteArray? = null,
    ): HistorySlice {
        require(pageSize in MIN_HISTORY_PAGE..MAX_HISTORY_PAGE) { "C_SCHEMA_VIOLATION" }
        val revision = indexRevision(chainId) ?: error("C_MISSING_DEPENDENCY")
        check(cursor == null || cursor.revision == revision) { "C_MISSING_DEPENDENCY" }
        val rows = historyPage(chainId, collection, scope, cursor?.key, cursor?.entityId.orEmpty(), pageSize + 1, authorDevice)
        val visible = rows.take(pageSize)
        val next = if (rows.size > pageSize) visible.last().let { HistoryCursor(revision, it.lastAccessSeq, it.entityId) } else null
        return HistorySlice(visible, next, revision)
    }

    @Transaction
    suspend fun applyVerifiedRecord(
        signed: SignedEventEntity,
        generation: Long,
        deviceId: String,
        inner: PallaSyncInnerRecord,
    ): Boolean {
        val conflict = recordConflict(signed, generation, deviceId, inner)
        check(conflict == null) { checkNotNull(conflict) }
        val previous = event(signed.chainId, signed.recordId)
        if (previous != null) {
            if (signed.relaySeq != null) {
                check(previous.relaySeq == null || (previous.relaySeq == signed.relaySeq && previous.logId == signed.logId)) {
                    "C_RECORD_ID_CONFLICT"
                }
                bindAcceptedEvent(signed.chainId, signed.recordId, signed.objectHash, signed.logId, signed.relaySeq, signed.receiptJson)
            }
            return false
        }
        insertAuthorHead(signed, generation, deviceId, inner)
        inner.operations.forEachIndexed { index, op -> applyOperation(signed, inner.collection, deviceId, index, op) }
        observeLamport(signed.chainId, inner.operations.maxOf { it.lamport })
        insertEvent(signed.copy(validationState = if (signed.relaySeq == null) "local_provisional" else "materialized"))
        return true
    }

    /** No effects: the page transaction can quarantine this object and still apply unrelated objects. */
    suspend fun recordConflict(
        signed: SignedEventEntity,
        generation: Long,
        deviceId: String,
        inner: PallaSyncInnerRecord,
    ): String? {
        val old = event(signed.chainId, signed.recordId)
        val same = author(signed.chainId, generation, deviceId, inner.deviceSeq)
        val previous = if (inner.deviceSeq > 1) author(signed.chainId, generation, deviceId, inner.deviceSeq - 1) else null
        val snapshotConflict = conflictsWithSnapshotAuthorEvidence(signed, generation, deviceId, inner, same, previous)
        val next = author(signed.chainId, generation, deviceId, inner.deviceSeq + 1)
        return when {
            old == null && removedRecordId(signed.chainId, signed.recordId) != null -> {
                "C_RECORD_ID_CONFLICT"
            }

            old != null && old.objectHash != signed.objectHash -> {
                "C_RECORD_ID_CONFLICT"
            }

            conflictsWithPlacement(old, signed) -> {
                "C_RECORD_ID_CONFLICT"
            }

            same != null && (same.recordId != signed.recordId || same.recordHash != signed.objectHash) -> {
                "C_DEVICE_EQUIVOCATION"
            }

            previous != null && previous.recordHash != inner.prevRecordHash -> {
                "C_DEVICE_EQUIVOCATION"
            }

            snapshotConflict -> {
                "C_DEVICE_EQUIVOCATION"
            }

            next != null && next.previousHash != signed.objectHash -> {
                "C_DEVICE_EQUIVOCATION"
            }

            else -> {
                null
            }
        }
    }

    suspend fun insertAuthorHead(
        signed: SignedEventEntity,
        generation: Long,
        deviceId: String,
        inner: PallaSyncInnerRecord,
    ) {
        resolveSnapshotAuthorGaps(signed.chainId, generation, deviceId, inner.deviceSeq)
        if (author(signed.chainId, generation, deviceId, inner.deviceSeq) == null) {
            val gap =
                inner.deviceSeq > 1 &&
                    author(signed.chainId, generation, deviceId, inner.deviceSeq - 1) == null &&
                    snapshotAuthorHead(signed.chainId, generation, deviceId, inner.deviceSeq - 1) == null
            insertAuthor(
                AuthorSequenceEntity(
                    signed.chainId,
                    generation,
                    deviceId,
                    inner.deviceSeq,
                    signed.recordId,
                    signed.objectHash,
                    inner.prevRecordHash,
                    gap,
                ),
            )
        }
        val next = author(signed.chainId, generation, deviceId, inner.deviceSeq + 1)
        if (next != null) resolveAuthorGap(signed.chainId, generation, deviceId, next.deviceSeq)
    }

    suspend fun applyOperation(
        signed: SignedEventEntity,
        collection: String,
        device: String,
        index: Int,
        op: PallaSyncOperation,
    ) {
        val key = PallaSyncConflictResolver.key(op.lamport, op.operation, device, signed.recordId, index)
        val retention = op.entityId == "*" && op.context["scope"]?.jsonPrimitive?.contentOrNull == "retention"
        if (retention || op.operation == "clear") {
            val kind = if (retention) "retention" else "clear"
            val scope = (if (retention) op.body else op.context)["scope"]?.jsonPrimitive?.contentOrNull ?: error("C_SCHEMA_VIOLATION")
            val current = register(signed.chainId, collection, scope, kind)
            if (current == null || PallaSyncConflictResolver.compare(key, current.winner) > 0) {
                putRegister(ScopeRegisterEntity(signed.chainId, collection, scope, kind, key, op.body.toString()))
            }
        } else {
            applyEntity(signed.chainId, collection, key, op)
        }
    }

    suspend fun applyEntity(
        chain: String,
        collection: String,
        key: WinnerColumns,
        op: PallaSyncOperation,
    ) {
        val current = entity(chain, collection, op.entityId)
        if (current == null || PallaSyncConflictResolver.compare(key, current.winner) > 0) {
            val scope = if (collection == "palleria.view_history/3") op.entityId.substringBefore(':') else "all"
            putEntity(
                EntityRegisterEntity(
                    chain,
                    collection,
                    op.entityId,
                    key,
                    op.operation,
                    scope,
                    op.body.toString(),
                    op.context.toString(),
                    op.context["order_key"]?.jsonPrimitive?.contentOrNull,
                ),
            )
            deleteIndex(chain, collection, op.entityId)
            if (op.operation == "upsert") {
                putIndex(
                    HistoryIndexEntity(
                        chain,
                        collection,
                        scope,
                        PallaSyncConflictResolver.encoded(key),
                        op.entityId,
                        key,
                    ),
                )
            }
        }
    }
}

/** Small focused reader for immutable deletion proof revisions retained after chain erasure. */
@Dao
interface DeletionEvidenceDao {
    @Query("SELECT * FROM ps_evidence WHERE kind='deletion_authority' ORDER BY chain_id COLLATE BINARY,origin COLLATE BINARY")
    suspend fun retainedDeletionAuthorities(): List<ProtectedEvidenceEntity>

    /** §17.7: a retained authenticated deletion ledger prevents this deterministic chain ID being reactivated on any relay. */
    @Query("SELECT EXISTS(SELECT 1 FROM ps_evidence WHERE chain_id=:chainId AND kind='delete_receipt')")
    suspend fun hasDeletionReceipt(chainId: String): Boolean

    @Query(
        "SELECT signed_json FROM ps_evidence WHERE chain_id=:chainId AND log_id=:logId AND kind='delete_receipt' ORDER BY sequence DESC, rowid DESC LIMIT 1",
    )
    suspend fun latestReceiptRevision(
        chainId: String,
        logId: String,
    ): String?

    @Query(
        "SELECT signed_json FROM ps_evidence WHERE origin=:origin AND relay_key_id=:keyId AND chain_id=:chainId AND log_id=:logId AND kind='delete_receipt' AND sequence=:sequence ORDER BY rowid DESC LIMIT 1",
    )
    suspend fun latestReceiptAtSequence(
        origin: String,
        keyId: String,
        chainId: String,
        logId: String,
        sequence: Long,
    ): String?
}
