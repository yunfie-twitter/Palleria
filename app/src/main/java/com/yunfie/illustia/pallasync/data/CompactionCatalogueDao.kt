package com.yunfie.illustia.pallasync.data

import androidx.room.Dao
import androidx.room.Query

/** Bounded lookups for matching the relay snapshot catalogue to verified local log evidence. */
@Dao
interface CompactionCatalogueDao {
    @Query(
        "SELECT * FROM ps_log WHERE chain_id=:chainId AND log_id=:logId AND validation_state='authority_verified' AND signed_json LIKE '%\"kind\":\"compaction\",\"object\":%' AND signed_json LIKE '%' || '\"compaction_id\":\"' || :compactionId || '\"%' LIMIT 2",
    )
    suspend fun verifiedEntries(
        chainId: String,
        logId: String,
        compactionId: String,
    ): List<LogEntryEntity>

    @Query(
        "SELECT COUNT(*) FROM ps_log WHERE chain_id=:chainId AND log_id=:logId AND validation_state='authority_verified' AND signed_json LIKE '%\"kind\":\"compaction\",\"object\":%' AND signed_json LIKE '%\"compaction_id\":\"%'",
    )
    suspend fun verifiedCount(
        chainId: String,
        logId: String,
    ): Int
}
