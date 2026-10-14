package com.yunfie.illustia.pallasync

/** §19.3 operational/dependency states never manufacture corrupt-object evidence. */
internal fun validationState(code: String?): String =
    when (code) {
        null -> "materialized"
        "C_MISSING_DEPENDENCY" -> "pending_dependency"
        "C_ACCEPTANCE_UNPROVEN" -> "pending_evidence"
        "C_UNSUPPORTED_SCHEMA" -> "unsupported_collection"
        "C_LOCAL_STORAGE_FULL", "C_LOCAL_LIMIT", "C_WRITE_PAUSED_BY_POLICY", "C_COUNTER_EXHAUSTED" -> "operationally_blocked"
        else -> "quarantined"
    }

/** A missing epoch key is recoverable only by retrying the uncommitted record page. */
internal fun recordPageMustRemainUncommitted(code: String?): Boolean = code == "C_MISSING_DEPENDENCY"
