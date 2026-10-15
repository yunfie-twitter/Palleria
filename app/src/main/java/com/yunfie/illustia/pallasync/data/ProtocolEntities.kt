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

/** A compact author-chain anchor is hash evidence, not a fabricated authored record (§16.6). */
@Entity(tableName = "ps_snapshot_author_heads", primaryKeys = ["chain_id", "generation", "device_id"])
data class SnapshotAuthorHeadEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    val generation: Long,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "device_seq") val deviceSeq: Long,
    @ColumnInfo(name = "record_hash") val recordHash: String,
    @ColumnInfo(name = "compaction_id") val compactionId: String,
)

/** Historical holes stay explicit after snapshot install and are never inferred to be repaired. */
@Entity(tableName = "ps_snapshot_author_gaps", primaryKeys = ["chain_id", "generation", "device_id", "first_seq", "last_seq"])
data class SnapshotAuthorGapEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    val generation: Long,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "first_seq") val first: Long,
    @ColumnInfo(name = "last_seq") val last: Long,
    @ColumnInfo(name = "compaction_id") val compactionId: String,
)

/** Ordered union row used to stream the newest author head from retained events and snapshots. */
data class SnapshotAuthorEvidenceRow(
    val generation: Long,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "device_seq") val deviceSeq: Long,
    @ColumnInfo(name = "record_hash") val recordHash: String,
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

data class QuarantineSummaryRow(
    @ColumnInfo(name = "object_hash") val objectHash: String,
    val reason: String,
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
    @ColumnInfo(name = "upload_receipt_json", defaultValue = "'null'") val uploadReceiptJson: String,
    @ColumnInfo(name = "covered_seq") val coveredSeq: Long,
    @ColumnInfo(name = "coverage_json") val coverageJson: String,
    @ColumnInfo(name = "staging_state") val stagingState: String,
)

/** Restart-safe local publication journal; it contains only signed ciphertext and relay receipts. */
@Entity(tableName = "ps_snapshot_publications", primaryKeys = ["chain_id", "compaction_id"])
data class SnapshotPublicationEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    @ColumnInfo(name = "compaction_id") val compactionId: String,
    val generation: Long,
    val epoch: Long,
    @ColumnInfo(name = "covered_through_seq") val coveredThroughSeq: Long,
    @ColumnInfo(name = "log_id") val logId: String,
    @ColumnInfo(name = "header_json") val headerJson: String,
    @ColumnInfo(name = "part_count") val partCount: Int,
    @ColumnInfo(name = "state_sha256") val stateSha256: String? = null,
    @ColumnInfo(name = "compaction_json") val compactionJson: String? = null,
    val status: String = "preparing",
)

@Entity(tableName = "ps_snapshot_publication_parts", primaryKeys = ["chain_id", "compaction_id", "part_index"])
data class SnapshotPublicationPartEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    @ColumnInfo(name = "compaction_id") val compactionId: String,
    @ColumnInfo(name = "part_index") val partIndex: Int,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "record_hash") val recordHash: String,
    @ColumnInfo(name = "signed_json") val signedJson: String,
    @ColumnInfo(name = "upload_receipt_json") val uploadReceiptJson: String? = null,
    val status: String = "prepared",
)

/** Plaintext state recovered from parts stays isolated until every part verifies as one stream. */
@Entity(tableName = "ps_snapshot_installs", primaryKeys = ["chain_id", "compaction_id"])
data class SnapshotInstallEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    @ColumnInfo(name = "compaction_id") val compactionId: String,
    @ColumnInfo(name = "compaction_json") val compactionJson: String,
    @ColumnInfo(name = "part_count") val partCount: Int,
    @ColumnInfo(name = "state_sha256") val stateSha256: String,
    @ColumnInfo(name = "covered_through_seq") val coveredThroughSeq: Long,
    @ColumnInfo(name = "covered_log_hash") val coveredLogHash: String,
    @ColumnInfo(name = "generation") val generation: Long,
    @ColumnInfo(name = "status") val status: String,
)

@Entity(
    tableName = "ps_snapshot_install_parts",
    primaryKeys = ["chain_id", "compaction_id", "part_index"],
)
data class SnapshotInstallPartEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    @ColumnInfo(name = "compaction_id") val compactionId: String,
    @ColumnInfo(name = "part_index") val partIndex: Int,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "record_hash") val recordHash: String,
    @ColumnInfo(name = "entry_count") val entryCount: Int,
)

@Entity(
    tableName = "ps_snapshot_install_entries",
    primaryKeys = ["chain_id", "compaction_id", "part_index", "entry_index"],
)
data class SnapshotInstallEntryEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    @ColumnInfo(name = "compaction_id") val compactionId: String,
    @ColumnInfo(name = "part_index") val partIndex: Int,
    @ColumnInfo(name = "entry_index") val entryIndex: Int,
    @ColumnInfo(name = "entry_json") val entryJson: String,
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

@Entity(
    tableName = "ps_migrations",
    primaryKeys = ["migration_id", "chunk_index"],
    indices = [Index(value = ["target_chain", "destination_record_id"])],
)
data class MigrationJournalEntity(
    @ColumnInfo(name = "migration_id") val migrationId: String,
    @ColumnInfo(name = "chunk_index") val chunkIndex: Long,
    @ColumnInfo(name = "source_hash") val sourceHash: String,
    @ColumnInfo(name = "source_identity") val sourceIdentity: String,
    @ColumnInfo(name = "target_chain") val targetChain: String?,
    val state: String,
    @ColumnInfo(name = "report_json") val reportJson: String,
    @ColumnInfo(name = "destination_record_id") val destinationRecordId: String? = null,
    @ColumnInfo(name = "destination_object_hash") val destinationObjectHash: String? = null,
    @ColumnInfo(name = "verified_receipt_hash") val verifiedReceiptHash: String? = null,
)

@Entity(
    tableName = "ps_legacy_migration_candidates",
    primaryKeys = ["migration_id", "entry_index"],
    indices = [Index(value = ["migration_id", "group_key"]), Index(value = ["migration_id", "source_sequence", "candidate_index"])],
)
data class LegacyMigrationCandidateEntity(
    @ColumnInfo(name = "migration_id") val migrationId: String,
    @ColumnInfo(name = "entry_index") val entryIndex: Long,
    @ColumnInfo(name = "group_key") val groupKey: String,
    @ColumnInfo(name = "source_sequence") val sourceSequence: Long,
    @ColumnInfo(name = "candidate_index") val candidateIndex: Int,
    @ColumnInfo(name = "source_record_id") val sourceRecordId: String,
    @ColumnInfo(name = "source_object_hash") val sourceObjectHash: String,
    val schema: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val operation: String,
    @ColumnInfo(name = "requires_choice") val requiresChoice: Boolean,
)

@Entity(tableName = "ps_legacy_migration_choices", primaryKeys = ["migration_id", "group_key"])
data class LegacyMigrationChoiceEntity(
    @ColumnInfo(name = "migration_id") val migrationId: String,
    @ColumnInfo(name = "group_key") val groupKey: String,
    @ColumnInfo(name = "selected_entry_index") val selectedEntryIndex: Long,
    @ColumnInfo(name = "user_confirmed") val userConfirmed: Boolean,
)

/** Restart-safe public checkpoint for the staged generation replacement ceremony (§17.5). */
@Entity(tableName = "ps_reseed_journal", primaryKeys = ["chain_id"])
data class ReseedJournalEntity(
    @ColumnInfo(name = "chain_id") val chainId: String,
    @ColumnInfo(name = "source_log_id") val sourceLogId: String,
    @ColumnInfo(name = "source_generation") val sourceGeneration: Long,
    @ColumnInfo(name = "target_origin") val targetOrigin: String,
    @ColumnInfo(name = "staged_log_id") val stagedLogId: String? = null,
    @ColumnInfo(name = "compaction_id") val compactionId: String? = null,
    @ColumnInfo(name = "target_pin_json") val targetPinJson: String,
    @ColumnInfo(name = "prepared_bundle_json") val preparedBundleJson: String? = null,
    @ColumnInfo(name = "commit_body_json") val commitBodyJson: String? = null,
    @ColumnInfo(name = "expected_entries_json") val expectedEntriesJson: String? = null,
    @ColumnInfo(name = "transparency_head_json") val transparencyHeadJson: String? = null,
    @ColumnInfo(name = "transparency_anchor_json") val transparencyAnchorJson: String? = null,
    @ColumnInfo(name = "verified_receipt_indices_json") val verifiedReceiptIndicesJson: String = "[]",
    val phase: Int = 0,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
)
