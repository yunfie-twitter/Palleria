package com.yunfie.illustia.pallasync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface PallaSyncDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOutboxEvent(event: OutboxEntity): Long

    @Query("SELECT * FROM pallasync_outbox WHERE status = 'queued' ORDER BY device_seq ASC")
    suspend fun getQueuedEvents(): List<OutboxEntity>

    @Query("SELECT * FROM pallasync_outbox WHERE chain_id = :chainId AND status = 'queued' ORDER BY device_seq ASC")
    suspend fun getQueuedEvents(chainId: String): List<OutboxEntity>

    @Query("SELECT * FROM pallasync_outbox WHERE chain_id=:chainId AND status='queued' ORDER BY device_seq ASC,id ASC LIMIT :limit")
    suspend fun queuedBatch(
        chainId: String,
        limit: Int,
    ): List<OutboxEntity>

    @Query("SELECT * FROM pallasync_outbox WHERE chain_id=:chain AND record_id=:record LIMIT 1")
    suspend fun outboxRecord(
        chain: String,
        record: String,
    ): OutboxEntity?

    @Query(
        "UPDATE pallasync_chain_state SET author_seq=:sequence,author_tail_hash=:hash,lamport=MAX(lamport,:lamport) WHERE chainId=:chain AND author_seq=:previousSequence",
    )
    suspend fun commitAuthorTail(
        chain: String,
        previousSequence: Long,
        sequence: Long,
        hash: String,
        lamport: Long,
    ): Int

    @Query(
        "UPDATE pallasync_inbox SET status='applied',quarantine_reason=NULL,relay_seq=:sequence WHERE chain_id=:chain AND record_id=:record AND raw_record_json=:raw",
    )
    suspend fun resolveInboxRecord(
        chain: String,
        record: String,
        raw: String,
        sequence: Long,
    ): Int

    @Query(
        "UPDATE pallasync_outbox SET status = 'queued' WHERE chain_id = :chainId AND device_seq = 0 AND status NOT IN ('accepted','reseed_rebased')",
    )
    suspend fun requeueControlEvents(chainId: String)

    @Query(
        """
        UPDATE pallasync_outbox
        SET status = 'queued'
        WHERE chain_id = :chainId AND status = 'pending_initial_merge'
        """,
    )
    suspend fun queuePendingInitialMergeEvents(chainId: String): Int

    @Query(
        """
        UPDATE pallasync_chain_state
        SET initial_pull_completed = 1
        WHERE chainId = :chainId
        """,
    )
    suspend fun markInitialPullCompleted(chainId: String): Int

    @Transaction
    suspend fun completeInitialPullAndQueueEvents(chainId: String): Int {
        check(markInitialPullCompleted(chainId) == 1) {
            "Cannot complete initial pull for inactive chain $chainId"
        }
        return queuePendingInitialMergeEvents(chainId)
    }

    @Query("DELETE FROM pallasync_outbox WHERE status = 'accepted'")
    suspend fun deleteAcceptedEvents()

    @Query("DELETE FROM pallasync_outbox WHERE id = :id")
    suspend fun deleteOutboxEvent(id: Long)

    @Query("SELECT * FROM pallasync_outbox WHERE id=:id")
    suspend fun outboxEvent(id: Long): OutboxEntity?

    @Query("SELECT COUNT(*) FROM pallasync_outbox WHERE chain_id=:chain AND status NOT IN ('accepted','reseed_rebased')")
    suspend fun pendingOutboxCount(chain: String): Long

    @Query(
        "SELECT * FROM pallasync_outbox WHERE chain_id=:chain AND id>:after AND status NOT IN ('accepted','reseed_rebased') ORDER BY id LIMIT :limit",
    )
    suspend fun pendingOutboxPage(
        chain: String,
        after: Long,
        limit: Int,
    ): List<OutboxEntity>

    @Query("SELECT MAX(id) FROM pallasync_outbox WHERE chain_id=:chain AND status NOT IN ('accepted','reseed_rebased')")
    suspend fun lastPendingOutboxId(chain: String): Long?

    @Query("DELETE FROM pallasync_outbox WHERE id=:id AND chain_id=:chainId AND record_id=:recordId AND object_hash=:objectHash")
    suspend fun removeReceiptConfirmedOutbox(
        id: Long,
        chainId: String,
        recordId: String,
        objectHash: String,
    ): Int

    @Query(
        "UPDATE pallasync_outbox SET status='reseed_rebased' WHERE id=:id AND chain_id=:chainId AND record_id=:recordId AND object_hash=:objectHash AND status NOT IN ('accepted','reseed_rebased')",
    )
    suspend fun markReseedRebased(
        id: Long,
        chainId: String,
        recordId: String,
        objectHash: String,
    ): Int

    @Update
    suspend fun updateOutboxEvent(event: OutboxEntity)

    @Query("SELECT * FROM pallasync_chain_state WHERE chainId = :chainId")
    suspend fun getChainState(chainId: String): ChainStateEntity?

    @Query("SELECT * FROM pallasync_chain_state")
    suspend fun getAllChainStates(): List<ChainStateEntity>

    @Query("SELECT chainId FROM pallasync_chain_state WHERE write_state IN ('active','archived') ORDER BY chainId")
    suspend fun getNewRootMigrationSourceIds(): List<String>

    @Query("SELECT * FROM pallasync_chain_state WHERE write_state NOT IN ('archived','activation_preparing')")
    suspend fun getActiveChainStates(): List<ChainStateEntity>

    @Query("SELECT * FROM pallasync_chain_state WHERE write_state NOT IN ('archived','activation_preparing') LIMIT 1")
    suspend fun getActiveChainState(): ChainStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateChainState(state: ChainStateEntity)

    @Query("UPDATE pallasync_chain_state SET write_state='integrity_paused' WHERE chainId=:chainId AND write_state!='archived'")
    suspend fun pauseIntegrityWrites(chainId: String): Int

    @Query(
        """
        UPDATE pallasync_chain_state
        SET last_relay_seq = CASE
            WHEN last_relay_seq < :lastRelaySeq THEN :lastRelaySeq
            ELSE last_relay_seq
        END
        WHERE chainId = :chainId
        """,
    )
    suspend fun advanceRecordsCursor(
        chainId: String,
        lastRelaySeq: Long,
    ): Int

    @Query(
        """
        UPDATE pallasync_chain_state
        SET lamport = CASE WHEN lamport < :lamport THEN :lamport ELSE lamport END
        WHERE chainId = :chainId
        """,
    )
    suspend fun advanceLamport(
        chainId: String,
        lamport: Long,
    ): Int

    @Query("DELETE FROM pallasync_outbox")
    suspend fun clearOutboxEvents()

    @Query("DELETE FROM pallasync_chain_state")
    suspend fun clearChainStates()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: PallaSyncDeviceEntity)

    @Query("SELECT * FROM pallasync_devices WHERE chainId = :chainId")
    suspend fun getDevicesInChain(chainId: String): List<PallaSyncDeviceEntity>

    @Query("SELECT * FROM pallasync_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDeviceSync(deviceId: String): PallaSyncDeviceEntity?

    @Query("SELECT COUNT(*) FROM pallasync_devices WHERE chainId = :chainId")
    suspend fun getDeviceCount(chainId: String): Int

    @Query("DELETE FROM pallasync_devices")
    suspend fun clearDevices()

    @Query("DELETE FROM pallasync_devices WHERE deviceId = :deviceId")
    suspend fun deleteDevice(deviceId: String)

    @Query("SELECT * FROM pallasync_devices")
    suspend fun getAllDevices(): List<PallaSyncDeviceEntity>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM pallasync_inbox
            WHERE chain_id = :chainId AND record_id = :recordId
        )
        """,
    )
    suspend fun hasInboxRecord(
        chainId: String,
        recordId: String,
    ): Boolean

    @Query(
        """
        SELECT * FROM pallasync_inbox
        WHERE chain_id = :chainId AND record_id = :recordId
        LIMIT 1
        """,
    )
    suspend fun getInboxRecord(
        chainId: String,
        recordId: String,
    ): PallaSyncInboxEntity?

    @Query(
        """
        SELECT * FROM pallasync_inbox
        WHERE chain_id = :chainId AND status = 'quarantined'
        ORDER BY relay_seq ASC
        """,
    )
    suspend fun getQuarantinedInboxRecords(chainId: String): List<PallaSyncInboxEntity>

    @Query(
        "SELECT COUNT(*) FROM pallasync_inbox WHERE chain_id=:chainId AND status NOT IN ('applied','quarantined')",
    )
    suspend fun pendingInboxDispositionCount(chainId: String): Long

    @Query(
        "SELECT * FROM pallasync_inbox WHERE chain_id=:chainId AND status IN ('pending_dependency','pending_evidence') ORDER BY relay_seq ASC LIMIT :limit",
    )
    suspend fun pendingInboxEvidenceRecords(
        chainId: String,
        limit: Int,
    ): List<PallaSyncInboxEntity>

    @Query(
        "UPDATE pallasync_inbox SET status=:status, quarantine_reason=:reason WHERE chain_id=:chainId AND record_id=:recordId AND raw_record_json=:rawRecordJson",
    )
    suspend fun updateInboxDisposition(
        chainId: String,
        recordId: String,
        rawRecordJson: String,
        status: String,
        reason: String,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInboxRecords(records: List<PallaSyncInboxEntity>): List<Long>

    @Query("DELETE FROM pallasync_inbox")
    suspend fun clearInboxRecords()

    @Query("DELETE FROM pallasync_inbox WHERE chain_id = :chainId")
    suspend fun clearInboxRecords(chainId: String)

    @Query("DELETE FROM pallasync_outbox WHERE chain_id=:chainId")
    suspend fun clearOutboxForChain(chainId: String)

    @Query("DELETE FROM pallasync_chain_state WHERE chainId=:chainId")
    suspend fun clearChainState(chainId: String)

    @Query("DELETE FROM pallasync_devices WHERE chainId=:chainId")
    suspend fun clearDevicesForChain(chainId: String)

    @Transaction
    suspend fun clearVerifiedDeletedChain(chainId: String) {
        clearOutboxForChain(chainId)
        clearChainState(chainId)
        clearDevicesForChain(chainId)
        clearInboxRecords(chainId)
    }

    @Query("UPDATE pallasync_chain_state SET write_state='archived' WHERE chainId!=:chainId")
    suspend fun archiveOtherChainStates(chainId: String)

    @Transaction
    suspend fun retainOnlyChain(chainId: String) {
        archiveOtherChainStates(chainId)
    }

    /** §11.2: persist record-only downloads and their separate cursor; global D/V/M require full log bytes. */
    @Transaction
    suspend fun commitInboxPage(
        chainId: String,
        lastRelaySeq: Long,
        maxLamport: Long,
        records: List<PallaSyncInboxEntity>,
    ) {
        require(lastRelaySeq >= 0) { "lastRelaySeq must not be negative" }
        require(maxLamport >= 0) { "maxLamport must not be negative" }
        require(records.all { it.chainId == chainId && it.relaySeq <= lastRelaySeq }) {
            "Inbox page does not match its chain or cursor"
        }
        insertInboxRecords(records)
        check(advanceRecordsCursor(chainId, lastRelaySeq) == 1) {
            "Cannot advance cursor for inactive chain $chainId"
        }
        check(advanceLamport(chainId, maxLamport) == 1) {
            "Cannot advance Lamport clock for inactive chain $chainId"
        }
    }

    /** Keeps the Lamport update and the signed outbox insert in one Room transaction. */
    @Transaction
    suspend fun updateChainStateAndInsertOutbox(
        state: ChainStateEntity,
        event: OutboxEntity,
    ): Long {
        require(state.chainId == event.chainId) { "State and outbox chain IDs differ" }
        updateChainState(state)
        return insertOutboxEvent(event)
    }

    @Transaction
    suspend fun updateChainStateAndInsertOutboxEvents(
        state: ChainStateEntity,
        events: List<OutboxEntity>,
    ) {
        require(events.all { it.chainId == state.chainId }) {
            "State and outbox chain IDs differ"
        }
        updateChainState(state)
        events.forEach { insertOutboxEvent(it) }
    }

    /**
     * Archives previous chains and preserves their outbox, evidence and logical state (§9/§17).
     * Key material is committed separately by PallaSyncKeystore after the remote join
     * succeeds; the coordinator serializes those two commits.
     */
    @Transaction
    suspend fun activateChain(
        state: ChainStateEntity,
        initialEvents: List<OutboxEntity>,
    ) {
        require(initialEvents.all { it.chainId == state.chainId }) {
            "Initial outbox contains a different chain"
        }
        check(getChainState(state.chainId) == null) { "C_MISSING_DEPENDENCY" }
        archiveOtherChainStates(state.chainId)
        updateChainState(state)
        initialEvents.forEach { insertOutboxEvent(it) }
    }

    /** Explicit local-erasure entry point. A relay status never authorizes invoking it. */
    @Transaction
    suspend fun clearActiveChainData() {
        clearOutboxEvents()
        clearChainStates()
        clearDevices()
        clearInboxRecords()
    }
}
