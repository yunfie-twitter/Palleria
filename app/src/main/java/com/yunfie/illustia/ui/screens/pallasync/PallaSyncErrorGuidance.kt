package com.yunfie.illustia.ui.screens.pallasync

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncRecoveryGuidance
import com.yunfie.illustia.pallasync.PallaSyncRecoveryHint

/**
 * Turns a failure code into the next step the user can actually take.
 *
 * Two code vocabularies reach this screen:
 * - relay response codes (`OFFER_NOT_FOUND`, `DEVICE_LIMIT_REACHED`, ...), which already have
 *   §19.2 guidance through [PallaSyncRecoveryGuidance] and must reuse it;
 * - client-side protocol failures (`C_SCHEMA_VIOLATION`, ...) raised by the native layer.
 *
 * The raw code stays visible next to the hint: the code is still the diagnostic identifier, the
 * hint only adds the action. Unknown codes fall back to a neutral hint rather than inventing a
 * cause the client cannot prove.
 */
@StringRes
internal fun pallaSyncErrorGuidanceRes(code: String?): Int {
    val normalized = code?.trim()?.uppercase().orEmpty()
    if (normalized.isEmpty()) return R.string.pallasync_error_hint_generic
    relayHintRes(normalized)?.let { return it }
    return when (normalized) {
        "C_BOOTSTRAP_TRUST_REQUIRED",
        "C_RELAY_POLICY_CONSENT_REQUIRED",
        "C_RELAY_POLICY_UNAVAILABLE",
        "C_RELAY_DISCLOSURE_INVALID",
        -> R.string.pallasync_error_hint_trust

        // Signature/acceptance failures only: the hint tells the user to re-request, which holds
        // because the cryptographic check really did fail.
        "C_SIGNATURE_INVALID",
        "C_DEVICE_KEY_MISMATCH",
        "C_RELAY_KEY_MISMATCH",
        "C_RECEIPT_INVALID",
        "C_SNAPSHOT_INVALID",
        "C_ACCEPTANCE_UNPROVEN",
        -> R.string.pallasync_error_hint_signature

        "C_RECOVERY_SESSION_EXPIRED" -> R.string.pallasync_error_hint_offer_expired

        // The hint only tells the user the file is not the one expected for this step.
        "C_SCHEMA_VIOLATION",
        "C_UNSUPPORTED_SCHEMA",
        -> R.string.pallasync_error_hint_schema

        "C_EPOCH_FORK",
        "C_RECOVERY_ROOT_MISMATCH",
        "C_RECOVERY_SET_MISMATCH",
        "C_COLLECTION_TAG_MISMATCH",
        "C_COMMITMENT_MISMATCH",
        "C_CHAIN_ID_MISMATCH",
        -> R.string.pallasync_error_hint_chain_mismatch

        // Recovery-slot failures: the hint stays on "check the device's recovery setup", which the
        // codes do describe (a retired set or an unmet threshold is a recovery-state problem).
        "C_RECOVERY_SET_RETIRED",
        "C_RECOVERY_THRESHOLD_NOT_MET",
        "C_WRITE_PAUSED_BY_POLICY",
        -> R.string.pallasync_error_hint_device

        "C_MISSING_DEPENDENCY" -> R.string.pallasync_error_hint_missing_file

        "C_LOCAL_STORAGE_FULL",
        "C_LOCAL_LIMIT",
        "C_SAFETY_LIMIT_REACHED",
        "C_COUNTER_EXHAUSTED",
        "C_ORDER_KEY_EXHAUSTED",
        -> R.string.pallasync_error_hint_storage

        "C_RELAY_UNAVAILABLE" -> R.string.pallasync_error_hint_network

        // Integrity and policy codes below are deliberately unmapped. Duplicate record ids, a
        // mis-hashed log, a bogus transparency receipt, a rollback, a revoked device, a write
        // pause or an unverified deletion do not prove any single cause, so the specific hints
        // would assert something the client cannot establish:
        //   C_DEVICE_EQUIVOCATION, C_ROLLBACK_DETECTED, C_LOG_HASH_MISMATCH,
        //   C_TRANSPARENCY_INVALID, C_DEVICE_REVOKED, C_RECORD_ID_CONFLICT,
        //   C_RECOVERY_AUTH_REQUIRED, C_RECOVERY_SHARE_COMPROMISED, C_WRITE_PAUSED,
        //   C_DELETION_UNVERIFIED
        // They fall through to the neutral hint, which still shows the raw code.
        else -> R.string.pallasync_error_hint_generic
    }
}

/** Reuses the §19.2 hint already defined for relay responses instead of restating it. */
@StringRes
private fun relayHintRes(code: String): Int? =
    when (PallaSyncRecoveryGuidance.forCode(code)) {
        PallaSyncRecoveryHint.REFRESH_TOKEN -> R.string.pallasync_recovery_hint_token
        PallaSyncRecoveryHint.EXPLICIT_ADMIN_AUTH -> R.string.pallasync_recovery_hint_admin
        PallaSyncRecoveryHint.APPROVED_DEVICE_FLOW -> R.string.pallasync_recovery_hint_device
        PallaSyncRecoveryHint.DEVICE_SLOT_RELEASE -> R.string.pallasync_recovery_hint_device_limit
        PallaSyncRecoveryHint.VERIFY_ORIGIN_AND_HISTORY -> R.string.pallasync_recovery_hint_origin
        PallaSyncRecoveryHint.VERIFY_EPOCH_HISTORY -> R.string.pallasync_recovery_hint_epoch
        PallaSyncRecoveryHint.NEW_ENROLLMENT_OFFER -> R.string.pallasync_recovery_hint_offer
        PallaSyncRecoveryHint.RESEND_AUTHORIZED_ENVELOPE -> R.string.pallasync_recovery_hint_envelope
        PallaSyncRecoveryHint.RESTORE_SNAPSHOT -> R.string.pallasync_recovery_hint_snapshot
        PallaSyncRecoveryHint.REBUILD_STALE_REQUEST -> R.string.pallasync_recovery_hint_rebuild
        PallaSyncRecoveryHint.RESTART_DURABLE_CURSOR -> R.string.pallasync_recovery_hint_cursor
        PallaSyncRecoveryHint.SPLIT_OVERSIZED_BATCH -> R.string.pallasync_recovery_hint_payload
        PallaSyncRecoveryHint.HONOR_RETRY_AFTER -> R.string.pallasync_recovery_hint_rate
        PallaSyncRecoveryHint.RETAIN_AND_RECONCILE -> R.string.pallasync_recovery_hint_reconcile
        PallaSyncRecoveryHint.EXPLICIT_RELAY_MIGRATION -> R.string.pallasync_recovery_hint_migration
        PallaSyncRecoveryHint.USER_CAPACITY_RECOVERY -> R.string.pallasync_recovery_hint_capacity
        PallaSyncRecoveryHint.WAIT_FOR_RECEIPT_EVIDENCE -> R.string.pallasync_recovery_hint_receipt
        PallaSyncRecoveryHint.CORRECT_REQUEST -> R.string.pallasync_recovery_hint_request
        PallaSyncRecoveryHint.REBUILD_AUTHORIZATION -> R.string.pallasync_recovery_hint_authorization
        PallaSyncRecoveryHint.VERIFY_IDENTITY -> R.string.pallasync_recovery_hint_identity
        PallaSyncRecoveryHint.COMPARE_EXISTING_STATE -> R.string.pallasync_recovery_hint_conflict
        PallaSyncRecoveryHint.VERIFY_DELETION -> R.string.pallasync_recovery_hint_deleted
        null -> null
    }

@Composable
internal fun pallaSyncErrorGuidance(code: String?): String = stringResource(pallaSyncErrorGuidanceRes(code))
