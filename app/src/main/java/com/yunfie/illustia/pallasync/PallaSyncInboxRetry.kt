package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.PallaSyncInboxEntity

/** Local retry intent, never an authorization to skip the immutable record's verification (§19.4). */
internal enum class PallaSyncInboxRetry {
    EVIDENCE,
    LOCAL_STORAGE,
    ;

    fun accepts(record: PallaSyncInboxEntity): Boolean =
        when (this) {
            EVIDENCE -> record.status in setOf("pending_dependency", "pending_evidence")
            LOCAL_STORAGE -> record.status == "operationally_blocked" && record.quarantineReason == "C_LOCAL_STORAGE_FULL"
        }
}

/** Advisory allocation preflight, not a disk reservation or proof that a transaction will succeed (§16.5). */
internal object PallaSyncInboxStorageCapacity {
    const val MAX_RETRY_BATCH = 50
    private const val JOURNAL_HEADROOM_BYTES = 16L * 1024 * 1024
    private const val RECORD_ALLOCATION_FACTOR = 4L

    fun requireAvailable(
        record: PallaSyncInboxEntity,
        availableBytes: Long?,
    ) {
        check(PallaSyncInboxRetry.LOCAL_STORAGE.accepts(record)) { "C_SCHEMA_VIOLATION" }
        // Reserve room in the estimate for decoding, indexes and SQLite journal pages. Actual
        // disk-full still rolls back the normal materialization transaction; no history is deleted.
        val required = JOURNAL_HEADROOM_BYTES + record.rawRecordJson.toByteArray(Charsets.UTF_8).size * RECORD_ALLOCATION_FACTOR
        check(availableBytes != null && availableBytes >= required) { "C_LOCAL_STORAGE_FULL" }
    }
}
