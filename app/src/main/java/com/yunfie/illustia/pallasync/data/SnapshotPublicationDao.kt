package com.yunfie.illustia.pallasync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Exact-object, CAS-updated resume journal for outbound snapshot publication (§11.1/§16.6). */
@Dao
@Suppress("TooManyFunctions") // The bounded publication journal's immutability checks share transaction semantics.
interface SnapshotPublicationDao {
    companion object {
        const val MAX_SNAPSHOT_PARTS = 65_536
    }

    @Query("SELECT * FROM ps_snapshot_publications WHERE chain_id=:chain AND compaction_id=:compaction")
    suspend fun publication(
        chain: String,
        compaction: String,
    ): SnapshotPublicationEntity?

    @Query(
        "SELECT * FROM ps_snapshot_publications WHERE chain_id=:chain AND generation=:generation AND epoch=:epoch AND status='published' ORDER BY covered_through_seq DESC,compaction_id LIMIT 1",
    )
    suspend fun latestPublishedForEpoch(
        chain: String,
        generation: Long,
        epoch: Long,
    ): SnapshotPublicationEntity?

    /** At most two rows let the caller fail closed if multiple resumable publications exist. */
    @Query(
        "SELECT * FROM ps_snapshot_publications WHERE chain_id=:chain AND status IN ('preparing','uploading','compaction_ready') ORDER BY compaction_id LIMIT 2",
    )
    suspend fun resumablePublications(chain: String): List<SnapshotPublicationEntity>

    @Query("SELECT * FROM ps_snapshot_publication_parts WHERE chain_id=:chain AND compaction_id=:compaction ORDER BY part_index")
    suspend fun parts(
        chain: String,
        compaction: String,
    ): List<SnapshotPublicationPartEntity>

    @Query("SELECT * FROM ps_snapshot_publication_parts WHERE chain_id=:chain AND compaction_id=:compaction AND part_index=:index")
    suspend fun part(
        chain: String,
        compaction: String,
        index: Int,
    ): SnapshotPublicationPartEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPublication(publication: SnapshotPublicationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPart(part: SnapshotPublicationPartEntity)

    @Query(
        "UPDATE ps_snapshot_publication_parts SET upload_receipt_json=:receipt,status='receipted' " +
            "WHERE chain_id=:chain AND compaction_id=:compaction AND part_index=:index AND record_id=:recordId " +
            "AND record_hash=:recordHash AND signed_json=:signedJson AND upload_receipt_json IS NULL AND status='prepared'",
    )
    suspend fun attachReceipt(
        chain: String,
        compaction: String,
        index: Int,
        recordId: String,
        recordHash: String,
        signedJson: String,
        receipt: String,
    ): Int

    @Query(
        "UPDATE ps_snapshot_publications SET state_sha256=:commitment,compaction_json=:compactionJson,status='compaction_ready' " +
            "WHERE chain_id=:chain AND compaction_id=:compaction AND part_count=:partCount " +
            "AND status IN ('preparing','uploading','compaction_ready') " +
            "AND (state_sha256 IS NULL OR state_sha256=:commitment) AND (compaction_json IS NULL OR compaction_json=:compactionJson)",
    )
    suspend fun markCompactionReady(
        chain: String,
        compaction: String,
        partCount: Int,
        commitment: String,
        compactionJson: String,
    ): Int

    @Query("UPDATE ps_snapshot_publications SET status=:next WHERE chain_id=:chain AND compaction_id=:compaction AND status=:expected")
    suspend fun compareAndSetStatus(
        chain: String,
        compaction: String,
        expected: String,
        next: String,
    ): Int

    @Query(
        "UPDATE ps_snapshot_publications SET status='published' " +
            "WHERE chain_id=:chain AND compaction_id=:compaction AND status='compaction_ready' AND compaction_json=:compactionJson",
    )
    suspend fun markPublished(
        chain: String,
        compaction: String,
        compactionJson: String,
    ): Int

    @Transaction
    suspend fun begin(publication: SnapshotPublicationEntity) {
        check(publication.partCount in 1..MAX_SNAPSHOT_PARTS && publication.status == "preparing") { "C_SNAPSHOT_INVALID" }
        val old = publication(publication.chainId, publication.compactionId)
        if (old == null) {
            insertPublication(publication)
        } else {
            check(
                old.chainId == publication.chainId &&
                    old.compactionId == publication.compactionId &&
                    old.generation == publication.generation &&
                    old.epoch == publication.epoch &&
                    old.coveredThroughSeq == publication.coveredThroughSeq &&
                    old.logId == publication.logId &&
                    old.headerJson == publication.headerJson &&
                    old.partCount == publication.partCount &&
                    old.status in setOf("preparing", "uploading", "compaction_ready", "published"),
            ) { "C_SNAPSHOT_INVALID" }
        }
        check(this.publication(publication.chainId, publication.compactionId) != null) { "C_LOCAL_STORAGE_FULL" }
    }

    @Transaction
    suspend fun retainPrepared(part: SnapshotPublicationPartEntity) {
        check(part.partIndex >= 0 && part.status == "prepared" && part.uploadReceiptJson == null) { "C_SNAPSHOT_INVALID" }
        val publication = publication(part.chainId, part.compactionId) ?: error("C_MISSING_DEPENDENCY")
        check(publication.status in setOf("preparing", "uploading")) { "C_SNAPSHOT_INVALID" }
        val old = part(part.chainId, part.compactionId, part.partIndex)
        if (old == null) {
            insertPart(part)
        } else {
            check(
                old.chainId == part.chainId && old.compactionId == part.compactionId &&
                    old.partIndex == part.partIndex && old.recordId == part.recordId &&
                    old.recordHash == part.recordHash && old.signedJson == part.signedJson,
            ) { "C_RECORD_ID_CONFLICT" }
        }
        check(
            this.part(part.chainId, part.compactionId, part.partIndex)?.let {
                it.recordId == part.recordId && it.recordHash == part.recordHash && it.signedJson == part.signedJson
            } == true,
        ) { "C_LOCAL_STORAGE_FULL" }
        compareAndSetStatus(part.chainId, part.compactionId, "preparing", "uploading")
    }

    @Transaction
    suspend fun retainReceipt(
        part: SnapshotPublicationPartEntity,
        receipt: String,
    ) {
        val publication = publication(part.chainId, part.compactionId) ?: error("C_MISSING_DEPENDENCY")
        check(publication.status in setOf("preparing", "uploading")) { "C_SNAPSHOT_INVALID" }
        val old = part(part.chainId, part.compactionId, part.partIndex) ?: error("C_MISSING_DEPENDENCY")
        check(
            old.recordId == part.recordId && old.recordHash == part.recordHash && old.signedJson == part.signedJson,
        ) { "C_RECORD_ID_CONFLICT" }
        if (old.uploadReceiptJson == null) {
            check(
                attachReceipt(
                    part.chainId,
                    part.compactionId,
                    part.partIndex,
                    part.recordId,
                    part.recordHash,
                    part.signedJson,
                    receipt,
                ) == 1,
            ) { "C_SNAPSHOT_INVALID" }
        } else {
            check(old.uploadReceiptJson == receipt && old.status == "receipted") { "C_SNAPSHOT_INVALID" }
        }
        check(this.part(part.chainId, part.compactionId, part.partIndex)?.uploadReceiptJson == receipt) {
            "C_LOCAL_STORAGE_FULL"
        }
    }

    @Transaction
    suspend fun finalizeCompaction(
        chain: String,
        compaction: String,
        commitment: String,
        compactionJson: String,
    ) {
        val publication = publication(chain, compaction) ?: error("C_MISSING_DEPENDENCY")
        check(publication.status in setOf("preparing", "uploading", "compaction_ready")) { "C_SNAPSHOT_INVALID" }
        val savedParts = parts(chain, compaction)
        check(
            savedParts.size == publication.partCount &&
                savedParts.map { it.partIndex } == (0 until publication.partCount).toList() &&
                savedParts.all { it.status == "receipted" && it.uploadReceiptJson != null },
        ) { "C_ACCEPTANCE_UNPROVEN" }
        check(
            markCompactionReady(chain, compaction, publication.partCount, commitment, compactionJson) == 1,
        ) { "C_SNAPSHOT_INVALID" }
        check(
            publication(chain, compaction)?.let {
                it.stateSha256 == commitment && it.compactionJson == compactionJson && it.status == "compaction_ready"
            } == true,
        ) { "C_LOCAL_STORAGE_FULL" }
    }
}
