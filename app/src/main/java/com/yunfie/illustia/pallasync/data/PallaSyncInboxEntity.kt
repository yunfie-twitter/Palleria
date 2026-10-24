package com.yunfie.illustia.pallasync.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

data class PallaSyncInboxDispositionCount(
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "count") val count: Long,
)

/**
 * Durable receipt for a relay record.
 *
 * A row is written only after a record has either been applied or retained with
 * an explicit §19 validation disposition. Together with
 * [ChainStateEntity.lastRelaySeq], this makes relay page processing idempotent
 * across process death and retries.
 */
@Entity(
    tableName = "pallasync_inbox",
    primaryKeys = ["chain_id", "record_id"],
    indices = [
        Index(value = ["chain_id", "relay_seq"]),
        Index(value = ["chain_id", "status"]),
    ],
)
data class PallaSyncInboxEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "relay_seq") val relaySeq: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "raw_record_json") val rawRecordJson: String,
    @ColumnInfo(name = "quarantine_reason") val quarantineReason: String? = null,
    @ColumnInfo(name = "received_at_ms") val receivedAtMs: Long = System.currentTimeMillis(),
) {
    init {
        require(relaySeq >= 0) { "relaySeq must not be negative" }
        require(status in VALID_STATUSES) {
            "Unsupported inbox status: $status"
        }
        require(status == STATUS_APPLIED || quarantineReason != null) {
            "Every unresolved inbox record must retain its validation reason"
        }
    }

    companion object {
        const val STATUS_APPLIED = "applied"
        const val STATUS_QUARANTINED = "quarantined"
        const val STATUS_PENDING_DEPENDENCY = "pending_dependency"
        const val STATUS_PENDING_EVIDENCE = "pending_evidence"
        const val STATUS_UNSUPPORTED_COLLECTION = "unsupported_collection"
        const val STATUS_OPERATIONALLY_BLOCKED = "operationally_blocked"
        const val STATUS_CHAIN_SUSPENDED = "chain_suspended"
        const val STATUS_RECOVERY_SESSION_FAILED = "recovery_session_failed"
        const val STATUS_RECOVERY_ARTIFACT_QUARANTINED = "recovery_artifact_quarantined"
        const val STATUS_SAFETY_LIMITED = "safety_limited"
        const val STATUS_RECOVERY_ROTATION_INCOMPLETE = "recovery_rotation_incomplete"

        private val VALID_STATUSES =
            setOf(
                STATUS_APPLIED,
                STATUS_QUARANTINED,
                STATUS_PENDING_DEPENDENCY,
                STATUS_PENDING_EVIDENCE,
                STATUS_UNSUPPORTED_COLLECTION,
                STATUS_OPERATIONALLY_BLOCKED,
                STATUS_CHAIN_SUSPENDED,
                STATUS_RECOVERY_SESSION_FAILED,
                STATUS_RECOVERY_ARTIFACT_QUARANTINED,
                STATUS_SAFETY_LIMITED,
                STATUS_RECOVERY_ROTATION_INCOMPLETE,
            )

        fun applied(
            chainId: String,
            recordId: String,
            relaySeq: Long,
            rawRecordJson: String,
        ) = PallaSyncInboxEntity(
            chainId = chainId,
            recordId = recordId,
            relaySeq = relaySeq,
            status = STATUS_APPLIED,
            rawRecordJson = rawRecordJson,
        )

        fun quarantined(
            chainId: String,
            recordId: String,
            relaySeq: Long,
            rawRecordJson: String,
            reason: String,
        ) = PallaSyncInboxEntity(
            chainId = chainId,
            recordId = recordId,
            relaySeq = relaySeq,
            status = STATUS_QUARANTINED,
            rawRecordJson = rawRecordJson,
            quarantineReason = reason,
        )
    }
}
