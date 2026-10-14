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

    @Query(
        "UPDATE ps_snapshots SET upload_receipt_json=:receipt WHERE chain_id=:chain AND compaction_id=:compaction AND part_index=:index AND part_hash=:partHash AND signed_json=:signedJson AND covered_seq=:coveredSeq AND coverage_json=:coverageJson AND staging_state='downloaded' AND upload_receipt_json='null'",
    )
    suspend fun attachReceiptToLegacyPart(
        chain: String,
        compaction: String,
        index: Int,
        partHash: String,
        signedJson: String,
        coveredSeq: Long,
        coverageJson: String,
        receipt: String,
    ): Int

    /** Retries are idempotent only for the same signed ciphertext and compaction coverage. */
    @Transaction
    suspend fun retainDownloaded(part: SnapshotPartEntity) {
        check(part.stagingState == "downloaded") { "C_SNAPSHOT_INVALID" }
        val old = part(part.chainId, part.compactionId, part.partIndex)
        when {
            old == null -> {
                insert(part)
            }

            old == part -> {
                Unit
            }

            old.uploadReceiptJson == "null" && old.copy(uploadReceiptJson = part.uploadReceiptJson) == part -> {
                check(
                    attachReceiptToLegacyPart(
                        part.chainId,
                        part.compactionId,
                        part.partIndex,
                        part.partHash,
                        part.signedJson,
                        part.coveredSeq,
                        part.coverageJson,
                        part.uploadReceiptJson,
                    ) == 1,
                ) { "C_SNAPSHOT_INVALID" }
            }

            else -> {
                error("C_SNAPSHOT_INVALID")
            }
        }
        check(part(part.chainId, part.compactionId, part.partIndex) == part) { "C_LOCAL_STORAGE_FULL" }
    }
}
