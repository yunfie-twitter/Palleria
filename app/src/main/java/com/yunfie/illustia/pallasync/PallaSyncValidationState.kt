package com.yunfie.illustia.pallasync

/** §19.3 operational/dependency states never manufacture corrupt-object evidence. */
private val operationalValidationCodes =
    setOf(
        "C_LOCAL_LIMIT",
        "C_LOCAL_STORAGE_FULL",
        "C_WRITE_PAUSED_BY_POLICY",
        "C_COUNTER_EXHAUSTED",
        "C_ORDER_KEY_EXHAUSTED",
        "C_RECOVERY_PARAMETER_INVALID",
        "C_RECOVERY_THRESHOLD_NOT_MET",
        "C_RECOVERY_SET_MISMATCH",
        "C_RECOVERY_SHARE_ALREADY_USED",
        "C_RECOVERY_WRAP_KEY_UNAVAILABLE",
        "C_RECOVERY_AUTH_REQUIRED",
        "C_RECOVERY_STORAGE_UNSAFE",
        "C_RELAY_DISCLOSURE_INVALID",
        "C_RELAY_POLICY_UNAVAILABLE",
        "C_RELAY_POLICY_CONSENT_REQUIRED",
        "C_BOOTSTRAP_TRUST_REQUIRED",
    )

private val suspendedValidationCodes =
    setOf(
        "C_DELETION_UNVERIFIED",
        "C_RELAY_KEY_MISMATCH",
        "C_EPOCH_FORK",
        "C_ROLLBACK_DETECTED",
        "C_DEVICE_EQUIVOCATION",
        "C_LOG_HASH_MISMATCH",
        "C_RECEIPT_INVALID",
        "C_TRANSPARENCY_INVALID",
        "C_RECOVERY_SHARE_COMPROMISED",
        "C_RECOVERY_SET_RETIRED",
        "C_RECOVERY_ROOT_MISMATCH",
    )

/** Convert an internal failure into a durable progress class without treating local prerequisites as bad records. */
internal fun validationState(code: String?): String =
    when {
        code == null -> "materialized"
        code == "C_MISSING_DEPENDENCY" -> "pending_dependency"
        code == "C_ACCEPTANCE_UNPROVEN" -> "pending_evidence"
        code == "C_UNSUPPORTED_SCHEMA" -> "unsupported_collection"
        code in operationalValidationCodes -> "operationally_blocked"
        code in suspendedValidationCodes -> "chain_suspended"
        code == "C_RECOVERY_SAS_MISMATCH" || code == "C_RECOVERY_SESSION_EXPIRED" -> "recovery_session_failed"
        code == "C_RECOVERY_SHARE_INVALID" || code == "C_RECOVERY_BLOB_INVALID" -> "recovery_artifact_quarantined"
        code == "C_SAFETY_LIMIT_REACHED" -> "safety_limited"
        code == "C_RECOVERY_ROTATION_INCOMPLETE" -> "recovery_rotation_incomplete"
        else -> "quarantined"
    }

/** A missing epoch key is recoverable only by retrying the uncommitted record page. */
internal fun recordPageMustRemainUncommitted(code: String?): Boolean = code == "C_MISSING_DEPENDENCY"
