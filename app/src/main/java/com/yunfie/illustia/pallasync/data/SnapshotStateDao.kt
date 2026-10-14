package com.yunfie.illustia.pallasync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Keyset readers for canonical snapshot streaming (§16.6). */
@Dao
interface SnapshotStateDao {
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
