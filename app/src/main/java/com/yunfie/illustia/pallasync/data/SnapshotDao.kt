package com.yunfie.illustia.pallasync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Exact-byte, resumable snapshot ciphertext staging (§16.6). */
@Dao
interface SnapshotDao {
    @Query("SELECT * FROM ps_snapshots WHERE chain_id=:chain AND compaction_id=:compaction AND part_index=:index")
    suspend fun part(
        chain: String,
        compaction: String,
        index: Int,
    ): SnapshotPartEntity?

    @Query("SELECT * FROM ps_snapshots WHERE chain_id=:chain AND compaction_id=:compaction ORDER BY part_index")
    suspend fun parts(
        chain: String,
        compaction: String,
    ): List<SnapshotPartEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(part: SnapshotPartEntity)

    /** Retries are idempotent only for the same signed ciphertext and compaction coverage. */
    @Transaction
    suspend fun retainDownloaded(part: SnapshotPartEntity) {
        check(part.stagingState == "downloaded") { "C_SNAPSHOT_INVALID" }
        val old = part(part.chainId, part.compactionId, part.partIndex)
        check(old == null || old == part) { "C_SNAPSHOT_INVALID" }
        if (old == null) insert(part)
        check(part(part.chainId, part.compactionId, part.partIndex) == part) { "C_LOCAL_STORAGE_FULL" }
    }
}
