package com.yunfie.illustia.pallasync.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index

/** §12.1 local tables. Recovery material is deliberately absent from these types. */
data class WinnerColumns(
    val lamport: Long,
    @ColumnInfo(name = "op_rank") val opRank: Int,
    @ColumnInfo(name = "device_id", typeAffinity = ColumnInfo.BLOB) val deviceId: ByteArray,
    @ColumnInfo(name = "record_id", typeAffinity = ColumnInfo.BLOB) val recordId: ByteArray,
    @ColumnInfo(name = "op_index") val opIndex: Int,
)

@Entity(tableName = "ps_registry", primaryKeys = ["chain_id", "generation", "device_id"])
data class RegistryHistoryEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    val generation: Long,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "record_json") val recordJson: String,
    @ColumnInfo(name = "approval_evidence") val approvalEvidence: String,
    @ColumnInfo(name = "revocation_evidence") val revocationEvidence: String?,
    val status: String,
    @ColumnInfo(name = "verification_key") val verificationKey: String,
)

@Entity(tableName = "ps_epochs", primaryKeys = ["chain_id", "epoch"])
data class EpochKeyEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    val epoch: Long,
    val generation: Long,
    @ColumnInfo(name = "epoch_json") val epochJson: String,
    @ColumnInfo(name = "envelopes_json") val envelopesJson: String,
    @ColumnInfo(name = "key_alias") val keyAlias: String?,
    @ColumnInfo(name = "wrapped_secret", typeAffinity = ColumnInfo.BLOB) val wrappedSecret: ByteArray?,
    @ColumnInfo(name = "retention_state") val retentionState: String,
)

@Entity(tableName = "ps_events", primaryKeys = ["chain_id", "record_id"], indices = [Index(value = ["log_id", "relay_seq"], unique = true)])
data class SignedEventEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "log_id") val logId: String,
    // A provisional locally signed record has no relay position. SQLite permits multiple NULLs in the unique index.
    @ColumnInfo(name = "relay_seq") val relaySeq: Long?,
    @ColumnInfo(name = "object_hash") val objectHash: String,
    @ColumnInfo(name = "signed_json") val signedJson: String,
    @ColumnInfo(name = "receipt_json") val receiptJson: String?,
    @ColumnInfo(name = "validation_state") val validationState: String,
)

@Entity(tableName = "ps_authors", primaryKeys = ["chain_id", "generation", "device_id", "device_seq"])
data class AuthorSequenceEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    val generation: Long,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "device_seq") val deviceSeq: Long,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "record_hash") val recordHash: String,
    @ColumnInfo(name = "previous_hash") val previousHash: String?,
    @ColumnInfo(name = "gap_before") val gapBefore: Boolean,
)

@Entity(tableName = "ps_entities", primaryKeys = ["chain_id", "collection", "entity_id"])
data class EntityRegisterEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    val collection: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @Embedded val winner: WinnerColumns,
    val operation: String,
    val scope: String,
    @ColumnInfo(name = "body_json") val bodyJson: String,
    @ColumnInfo(name = "context_json") val contextJson: String,
    @ColumnInfo(name = "order_key") val orderKey: String?,
)

@Entity(tableName = "ps_registers", primaryKeys = ["chain_id", "collection", "scope", "register_kind"])
data class ScopeRegisterEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    val collection: String,
    val scope: String,
    @ColumnInfo(name = "register_kind") val registerKind: String,
    @Embedded val winner: WinnerColumns,
    @ColumnInfo(name = "body_json") val bodyJson: String,
)

@Entity(
    tableName = "ps_history",
    primaryKeys = ["chain_id", "collection", "scope", "last_access_seq", "entity_id"],
    indices = [Index(value = ["chain_id", "collection", "entity_id"], unique = true)],
)
data class HistoryIndexEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    val collection: String,
    val scope: String,
    @ColumnInfo(name = "last_access_seq", typeAffinity = ColumnInfo.BLOB) val lastAccessSeq: ByteArray,
    @ColumnInfo(name = "entity_id", collate = ColumnInfo.BINARY) val entityId: String,
    @Embedded val winner: WinnerColumns,
)

@Entity(tableName = "ps_quarantine", primaryKeys = ["chain_id", "object_hash"])
data class QuarantineEvidenceEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    @ColumnInfo(name = "object_hash") val objectHash: String,
    val reason: String,
    @ColumnInfo(name = "evidence_json") val evidenceJson: String,
    @ColumnInfo(name = "resolution_state") val resolutionState: String,
)

/** Derived projection metadata, separate from deletion/policy winners (§15.6). */
@Entity(tableName = "ps_projections", primaryKeys = ["chain_id", "collection", "scope"])
data class ProjectionStateEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    val collection: String,
    val scope: String,
    @ColumnInfo(name = "live_count") val liveCount: Long,
    @ColumnInfo(name = "projection_limit") val projectionLimit: Long,
    @ColumnInfo(name = "user_limit") val userLimit: Long?,
    @ColumnInfo(name = "eviction_key", typeAffinity = ColumnInfo.BLOB) val evictionKey: ByteArray?,
    @ColumnInfo(name = "eviction_entity") val evictionEntity: String?,
    @ColumnInfo(name = "index_revision") val indexRevision: Long,
)

/** Complete raw log bytes and their original receipt, including public control entries (§11.2). */
@Entity(tableName = "ps_log", primaryKeys = ["chain_id", "log_id", "relay_seq"])
data class LogEntryEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    @ColumnInfo(name = "log_id") val logId: String,
    @ColumnInfo(name = "relay_seq") val relaySeq: Long,
    @ColumnInfo(name = "entry_hash") val entryHash: String,
    @ColumnInfo(name = "signed_json") val signedJson: String,
    @ColumnInfo(name = "receipt_json") val receiptJson: String,
    @ColumnInfo(name = "validation_state") val validationState: String,
)

@Entity(tableName = "ps_snapshots", primaryKeys = ["chain_id", "compaction_id", "part_index"])
data class SnapshotPartEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    @ColumnInfo(name = "compaction_id") val compactionId: String,
    @ColumnInfo(name = "part_index") val partIndex: Int,
    @ColumnInfo(name = "part_hash") val partHash: String,
    @ColumnInfo(name = "signed_json") val signedJson: String,
    @ColumnInfo(name = "covered_seq") val coveredSeq: Long,
    @ColumnInfo(name = "coverage_json") val coverageJson: String,
    @ColumnInfo(name = "staging_state") val stagingState: String,
)

@Entity(tableName = "ps_evidence", primaryKeys = ["origin", "relay_key_id", "chain_id", "log_id", "kind", "sequence", "object_hash"])
data class ProtectedEvidenceEntity(
    val origin: String,
    @ColumnInfo(name = "relay_key_id") val relayKeyId: String,
    @ColumnInfo(name = "chain_id") val chainId: String,
    @ColumnInfo(name = "log_id") val logId: String,
    val kind: String,
    val sequence: Long,
    @ColumnInfo(name = "object_hash") val objectHash: String,
    @ColumnInfo(name = "signed_json") val signedJson: String,
)

@Entity(tableName = "ps_authority", primaryKeys = ["chain_id", "kind", "object_hash"])
data class AuthorityHistoryEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    val kind: String,
    @ColumnInfo(name = "object_hash") val objectHash: String,
    val generation: Long,
    @ColumnInfo(name = "signed_json") val signedJson: String,
    @ColumnInfo(name = "validation_state") val validationState: String,
)

@Entity(tableName = "ps_migrations", primaryKeys = ["migration_id", "chunk_index"])
data class MigrationJournalEntity(
    @ColumnInfo(name = "migration_id") val migrationId: String,
    @ColumnInfo(name = "chunk_index") val chunkIndex: Long,
    @ColumnInfo(name = "source_hash") val sourceHash: String,
    @ColumnInfo(name = "source_identity") val sourceIdentity: String,
    @ColumnInfo(name = "target_chain") val targetChain: String?,
    val state: String,
    @ColumnInfo(name = "report_json") val reportJson: String,
)
