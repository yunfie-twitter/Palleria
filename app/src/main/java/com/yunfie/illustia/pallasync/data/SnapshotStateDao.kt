package com.yunfie.illustia.pallasync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Keyset readers for canonical snapshot streaming (§16.6). */
@Dao
interface SnapshotStateDao {
    /** Current logical winners ordered by the complete source K, for fresh-signature state migration. */
    @Query(
        "SELECT * FROM (" +
            "SELECT 'entity' AS kind,collection,entity_id,'' AS scope,operation,body_json,context_json," +
            "lamport,op_rank,device_id,record_id,op_index " +
            "FROM ps_entities WHERE chain_id=:chainId " +
            "UNION ALL " +
            "SELECT register_kind AS kind,collection,'*' AS entity_id,scope," +
            "CASE WHEN register_kind='clear' THEN 'clear' ELSE 'upsert' END AS operation," +
            "CASE WHEN register_kind='clear' THEN '{}' ELSE body_json END AS body_json," +
            "'{}' AS context_json,lamport,op_rank,device_id,record_id,op_index " +
            "FROM ps_registers WHERE chain_id=:chainId" +
            ") WHERE (lamport>:afterLamport OR " +
            "(lamport=:afterLamport AND op_rank>:afterRank) OR " +
            "(lamport=:afterLamport AND op_rank=:afterRank AND device_id>:afterDevice) OR " +
            "(lamport=:afterLamport AND op_rank=:afterRank AND device_id=:afterDevice AND record_id>:afterRecord) OR " +
            "(lamport=:afterLamport AND op_rank=:afterRank AND device_id=:afterDevice " +
            "AND record_id=:afterRecord AND op_index>:afterIndex)) " +
            "ORDER BY lamport,op_rank,device_id,record_id,op_index LIMIT :limit",
    )
    suspend fun migrationWinnerPage(
        chainId: String,
        afterLamport: Long,
        afterRank: Int,
        afterDevice: ByteArray,
        afterRecord: ByteArray,
        afterIndex: Int,
        limit: Int,
    ): List<SnapshotMigrationWinnerRow>

    @Query("SELECT COUNT(*) FROM ps_snapshot_author_gaps WHERE chain_id=:chainId")
    suspend fun unresolvedSnapshotAuthorGapCount(chainId: String): Long

    @Query("SELECT COUNT(*) FROM ps_authors WHERE chain_id=:chainId AND gap_before=1")
    suspend fun unresolvedAuthorGapCount(chainId: String): Long

    @Query(
        "SELECT * FROM (SELECT generation,device_id,device_seq,record_hash FROM ps_authors WHERE chain_id=:chainId UNION SELECT generation,device_id,device_seq,record_hash FROM ps_snapshot_author_heads WHERE chain_id=:chainId) WHERE (generation>:afterGeneration OR (generation=:afterGeneration AND device_id>:afterDevice COLLATE BINARY) OR (generation=:afterGeneration AND device_id=:afterDevice AND device_seq>:afterSequence)) ORDER BY generation,device_id COLLATE BINARY,device_seq LIMIT :limit",
    )
    suspend fun authorEvidencePage(
        chainId: String,
        afterGeneration: Long,
        afterDevice: String,
        afterSequence: Long,
        limit: Int,
    ): List<SnapshotAuthorEvidenceRow>

    @Query(
        "SELECT * FROM ps_snapshot_author_heads WHERE chain_id=:chainId AND generation=:generation AND device_id=:deviceId ORDER BY device_seq DESC LIMIT 1",
    )
    suspend fun authorHead(
        chainId: String,
        generation: Long,
        deviceId: String,
    ): SnapshotAuthorHeadEntity?

    @Query(
        "SELECT * FROM ps_snapshot_author_gaps WHERE chain_id=:chainId AND (generation>:afterGeneration OR (generation=:afterGeneration AND device_id>:afterDevice COLLATE BINARY) OR (generation=:afterGeneration AND device_id=:afterDevice AND first_seq>:afterFirst)) ORDER BY generation,device_id COLLATE BINARY,first_seq LIMIT :limit",
    )
    suspend fun authorGapPage(
        chainId: String,
        afterGeneration: Long,
        afterDevice: String,
        afterFirst: Long,
        limit: Int,
    ): List<SnapshotAuthorGapEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSnapshotAuthorHead(head: SnapshotAuthorHeadEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSnapshotAuthorGap(gap: SnapshotAuthorGapEntity)

    @Query("DELETE FROM ps_snapshot_author_heads WHERE chain_id=:chainId")
    suspend fun deleteSnapshotAuthorHeads(chainId: String)

    @Query("DELETE FROM ps_snapshot_author_gaps WHERE chain_id=:chainId")
    suspend fun deleteSnapshotAuthorGaps(chainId: String)

    @Query(
        "SELECT * FROM ps_entities WHERE chain_id=:chainId AND (collection>:afterCollection COLLATE BINARY OR (collection=:afterCollection AND entity_id>:afterEntity COLLATE BINARY)) ORDER BY collection COLLATE BINARY,entity_id COLLATE BINARY LIMIT :limit",
    )
    suspend fun entityPage(
        chainId: String,
        afterCollection: String,
        afterEntity: String,
        limit: Int,
    ): List<EntityRegisterEntity>

    @Query(
        "SELECT * FROM ps_registers WHERE chain_id=:chainId AND register_kind=:kind AND (collection>:afterCollection COLLATE BINARY OR (collection=:afterCollection AND scope>:afterScope COLLATE BINARY)) ORDER BY collection COLLATE BINARY,scope COLLATE BINARY LIMIT :limit",
    )
    suspend fun registerPage(
        chainId: String,
        kind: String,
        afterCollection: String,
        afterScope: String,
        limit: Int,
    ): List<ScopeRegisterEntity>

    @Query(
        "SELECT * FROM ps_authors WHERE chain_id=:chainId AND (generation>:afterGeneration OR (generation=:afterGeneration AND device_id>:afterDevice COLLATE BINARY) OR (generation=:afterGeneration AND device_id=:afterDevice AND device_seq>:afterSequence)) ORDER BY generation,device_id COLLATE BINARY,device_seq LIMIT :limit",
    )
    suspend fun authorPage(
        chainId: String,
        afterGeneration: Long,
        afterDevice: String,
        afterSequence: Long,
        limit: Int,
    ): List<AuthorSequenceEntity>

    @Query(
        "SELECT * FROM ps_projections WHERE chain_id=:chainId AND (collection>:afterCollection COLLATE BINARY OR (collection=:afterCollection AND scope>:afterScope COLLATE BINARY)) ORDER BY collection COLLATE BINARY,scope COLLATE BINARY LIMIT :limit",
    )
    suspend fun projectionPage(
        chainId: String,
        afterCollection: String,
        afterScope: String,
        limit: Int,
    ): List<ProjectionStateEntity>
}

/** Materialized source state with the original full conflict key retained for ordered re-authoring. */
data class SnapshotMigrationWinnerRow(
    val kind: String,
    val collection: String,
    @androidx.room.ColumnInfo(name = "entity_id") val entityId: String,
    val scope: String,
    val operation: String,
    @androidx.room.ColumnInfo(name = "body_json") val bodyJson: String,
    @androidx.room.ColumnInfo(name = "context_json") val contextJson: String,
    val lamport: Long,
    @androidx.room.ColumnInfo(name = "op_rank") val opRank: Int,
    @androidx.room.ColumnInfo(name = "device_id") val deviceId: ByteArray,
    @androidx.room.ColumnInfo(name = "record_id") val recordId: ByteArray,
    @androidx.room.ColumnInfo(name = "op_index") val opIndex: Int,
)
