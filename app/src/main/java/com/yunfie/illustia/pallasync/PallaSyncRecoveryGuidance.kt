package com.yunfie.illustia.pallasync

/** User-facing §19.2 next-step groups; these hints never release or authorize a request. */
internal enum class PallaSyncRecoveryHint {
    REFRESH_TOKEN,
    EXPLICIT_ADMIN_AUTH,
    APPROVED_DEVICE_FLOW,
    DEVICE_SLOT_RELEASE,
    VERIFY_ORIGIN_AND_HISTORY,
    VERIFY_EPOCH_HISTORY,
    NEW_ENROLLMENT_OFFER,
    RESEND_AUTHORIZED_ENVELOPE,
    RESTORE_SNAPSHOT,
    REBUILD_STALE_REQUEST,
    RESTART_DURABLE_CURSOR,
    SPLIT_OVERSIZED_BATCH,
    HONOR_RETRY_AFTER,
    RETAIN_AND_RECONCILE,
    EXPLICIT_RELAY_MIGRATION,
    USER_CAPACITY_RECOVERY,
    WAIT_FOR_RECEIPT_EVIDENCE,
}

internal object PallaSyncRecoveryGuidance {
    private val byCode =
        mapOf(
            "TOKEN_REQUIRED" to PallaSyncRecoveryHint.REFRESH_TOKEN,
            "TOKEN_NOT_YET_VALID" to PallaSyncRecoveryHint.REFRESH_TOKEN,
            "TOKEN_EXPIRED" to PallaSyncRecoveryHint.REFRESH_TOKEN,
            "TOKEN_REPLAYED" to PallaSyncRecoveryHint.REFRESH_TOKEN,
            "ADMIN_REQUIRED" to PallaSyncRecoveryHint.EXPLICIT_ADMIN_AUTH,
            "ROTATION_NOT_ALLOWED" to PallaSyncRecoveryHint.EXPLICIT_ADMIN_AUTH,
            "DEVICE_PENDING_KEYS" to PallaSyncRecoveryHint.APPROVED_DEVICE_FLOW,
            "DEVICE_UNKNOWN" to PallaSyncRecoveryHint.APPROVED_DEVICE_FLOW,
            "ENROLLMENT_NOT_ALLOWED" to PallaSyncRecoveryHint.APPROVED_DEVICE_FLOW,
            "DEVICE_LIMIT_REACHED" to PallaSyncRecoveryHint.DEVICE_SLOT_RELEASE,
            "CHAIN_NOT_FOUND" to PallaSyncRecoveryHint.VERIFY_ORIGIN_AND_HISTORY,
            "LOG_ID_MISMATCH" to PallaSyncRecoveryHint.VERIFY_ORIGIN_AND_HISTORY,
            "LOG_PRUNED" to PallaSyncRecoveryHint.VERIFY_ORIGIN_AND_HISTORY,
            "TRANSPARENCY_ID_MISMATCH" to PallaSyncRecoveryHint.VERIFY_ORIGIN_AND_HISTORY,
            "TRANSPARENCY_RANGE_UNAVAILABLE" to PallaSyncRecoveryHint.VERIFY_ORIGIN_AND_HISTORY,
            "DEVICE_NOT_FOUND" to PallaSyncRecoveryHint.APPROVED_DEVICE_FLOW,
            "EPOCH_NOT_FOUND" to PallaSyncRecoveryHint.VERIFY_EPOCH_HISTORY,
            "EPOCH_STALE" to PallaSyncRecoveryHint.VERIFY_EPOCH_HISTORY,
            "HISTORY_KEY_EXPIRED" to PallaSyncRecoveryHint.VERIFY_EPOCH_HISTORY,
            "OFFER_NOT_FOUND" to PallaSyncRecoveryHint.NEW_ENROLLMENT_OFFER,
            "ENVELOPE_NOT_FOUND" to PallaSyncRecoveryHint.RESEND_AUTHORIZED_ENVELOPE,
            "RECOVERY_ENVELOPE_REQUIRED" to PallaSyncRecoveryHint.RESEND_AUTHORIZED_ENVELOPE,
            "SNAPSHOT_PART_NOT_FOUND" to PallaSyncRecoveryHint.RESTORE_SNAPSHOT,
            "SNAPSHOT_NOT_FOUND" to PallaSyncRecoveryHint.RESTORE_SNAPSHOT,
            "SNAPSHOT_INCOMPLETE" to PallaSyncRecoveryHint.RESTORE_SNAPSHOT,
            "COMPACTION_UNSAFE" to PallaSyncRecoveryHint.RESTORE_SNAPSHOT,
            "CAS_MISMATCH" to PallaSyncRecoveryHint.REBUILD_STALE_REQUEST,
            "GENERATION_MISMATCH" to PallaSyncRecoveryHint.REBUILD_STALE_REQUEST,
            "MEMBER_SET_MISMATCH" to PallaSyncRecoveryHint.REBUILD_STALE_REQUEST,
            "REPAIR_PROOF_REQUIRED" to PallaSyncRecoveryHint.REBUILD_STALE_REQUEST,
            "CURSOR_EXPIRED" to PallaSyncRecoveryHint.RESTART_DURABLE_CURSOR,
            "PAYLOAD_TOO_LARGE" to PallaSyncRecoveryHint.SPLIT_OVERSIZED_BATCH,
            "RATE_LIMITED" to PallaSyncRecoveryHint.HONOR_RETRY_AFTER,
            "REQUEST_TIMEOUT" to PallaSyncRecoveryHint.RETAIN_AND_RECONCILE,
            "INTERNAL_ERROR" to PallaSyncRecoveryHint.RETAIN_AND_RECONCILE,
            "BAD_GATEWAY" to PallaSyncRecoveryHint.RETAIN_AND_RECONCILE,
            "RELAY_UNAVAILABLE" to PallaSyncRecoveryHint.RETAIN_AND_RECONCILE,
            "GATEWAY_TIMEOUT" to PallaSyncRecoveryHint.RETAIN_AND_RECONCILE,
            "RELAY_SHUTTING_DOWN" to PallaSyncRecoveryHint.EXPLICIT_RELAY_MIGRATION,
            "QUOTA_EXCEEDED" to PallaSyncRecoveryHint.USER_CAPACITY_RECOVERY,
            "STORAGE_UNAVAILABLE" to PallaSyncRecoveryHint.USER_CAPACITY_RECOVERY,
            "RECEIPT_NOT_FOUND" to PallaSyncRecoveryHint.WAIT_FOR_RECEIPT_EVIDENCE,
        )

    fun forCode(code: String): PallaSyncRecoveryHint? = byCode[code]

    /** Normative §19.2 retryable server codes, used to keep UI coverage auditable. */
    internal val retryableServerCodes: Set<String> = byCode.keys
}
