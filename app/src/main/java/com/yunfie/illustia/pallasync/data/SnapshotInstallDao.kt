package com.yunfie.illustia.pallasync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Isolated plaintext staging; read access opens only after the whole snapshot verifies (§16.6). */
@Suppress("TooManyFunctions")
@Dao
interface SnapshotInstallDao {
    @Query("SELECT * FROM ps_snapshot_installs WHERE chain_id=:chain AND compaction_id=:compaction")
    suspend fun install(
        chain: String,
        compaction: String,
    ): SnapshotInstallEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInstall(install: SnapshotInstallEntity)

    @Query("SELECT * FROM ps_snapshot_install_parts WHERE chain_id=:chain AND compaction_id=:compaction ORDER BY part_index")
    suspend fun parts(
        chain: String,
        compaction: String,
    ): List<SnapshotInstallPartEntity>

    @Query(
        "SELECT e.* FROM ps_snapshot_install_entries e INNER JOIN ps_snapshot_installs i ON i.chain_id=e.chain_id AND i.compaction_id=e.compaction_id WHERE e.chain_id=:chain AND e.compaction_id=:compaction AND e.part_index=:part AND i.status='verified' ORDER BY e.entry_index",
    )
    suspend fun verifiedPartEntries(
        chain: String,
        compaction: String,
        part: Int,
    ): List<SnapshotInstallEntryEntity>

    @Query(
        "SELECT * FROM ps_snapshot_install_entries WHERE chain_id=:chain AND compaction_id=:compaction AND part_index=:part ORDER BY entry_index",
    )
    suspend fun stagedPartEntriesForRetry(
        chain: String,
        compaction: String,
        part: Int,
    ): List<SnapshotInstallEntryEntity>

    @Query(
        "SELECT e.* FROM ps_snapshot_install_entries e INNER JOIN ps_snapshot_installs i ON i.chain_id=e.chain_id AND i.compaction_id=e.compaction_id WHERE e.chain_id=:chain AND e.compaction_id=:compaction AND i.status='verified' AND (e.part_index>:afterPart OR (e.part_index=:afterPart AND e.entry_index>:afterEntry)) ORDER BY e.part_index,e.entry_index LIMIT :limit",
    )
    suspend fun entryPage(
        chain: String,
        compaction: String,
        afterPart: Int,
        afterEntry: Int,
        limit: Int,
    ): List<SnapshotInstallEntryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPart(part: SnapshotInstallPartEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: SnapshotInstallEntryEntity)

    @Query(
        "UPDATE ps_snapshot_installs SET status='verified' WHERE chain_id=:chain AND compaction_id=:compaction AND status IN ('staging','verified')",
    )
    suspend fun setVerified(
        chain: String,
        compaction: String,
    ): Int

    @Transaction
    suspend fun begin(install: SnapshotInstallEntity) {
        check(install.status == "staging" && install.partCount in 1..MAX_SNAPSHOT_PARTS) { "C_SNAPSHOT_INVALID" }
        val old = install(install.chainId, install.compactionId)
        if (old == null) {
            insertInstall(install)
        } else {
            check(old.copy(status = "staging") == install) { "C_SNAPSHOT_INVALID" }
        }
        check(install(install.chainId, install.compactionId)?.copy(status = "staging") == install) { "C_LOCAL_STORAGE_FULL" }
    }

    /** Retry is idempotent only when the exact same authenticated part entries are replayed. */
    @Transaction
    suspend fun retainVerifiedPart(
        part: SnapshotInstallPartEntity,
        rows: List<SnapshotInstallEntryEntity>,
    ) {
        val manifest = checkNotNull(install(part.chainId, part.compactionId)) { "C_SNAPSHOT_INVALID" }
        check(manifest.status in setOf("staging", "verified") && part.partIndex in 0 until manifest.partCount) {
            "C_SNAPSHOT_INVALID"
        }
        check(
            rows.isNotEmpty() && rows.size == part.entryCount &&
                rows.indices.all { index ->
                    rows[index].chainId == part.chainId &&
                        rows[index].compactionId == part.compactionId &&
                        rows[index].partIndex == part.partIndex &&
                        rows[index].entryIndex == index
                },
        ) { "C_SNAPSHOT_INVALID" }
        val existing = parts(part.chainId, part.compactionId).singleOrNull { it.partIndex == part.partIndex }
        if (existing == null) {
            check(manifest.status == "staging") { "C_SNAPSHOT_INVALID" }
            insertPart(part)
            for (row in rows) insertEntry(row)
        } else {
            check(existing == part && stagedPartEntriesForRetry(part.chainId, part.compactionId, part.partIndex) == rows) {
                "C_SNAPSHOT_INVALID"
            }
        }
    }

    @Transaction
    suspend fun markVerified(
        chain: String,
        compaction: String,
    ) {
        val manifest = checkNotNull(install(chain, compaction)) { "C_SNAPSHOT_INVALID" }
        val parts = parts(chain, compaction)
        check(parts.size == manifest.partCount && parts.indices.all { parts[it].partIndex == it }) { "C_SNAPSHOT_INVALID" }
        check(setVerified(chain, compaction) == 1) { "C_LOCAL_STORAGE_FULL" }
        check(install(chain, compaction)?.status == "verified") { "C_LOCAL_STORAGE_FULL" }
    }

    private companion object {
        const val MAX_SNAPSHOT_PARTS = 65_536
    }
}
