package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

/** §8.6: construct only the recovery audit allowlist; never include holder IDs or encrypted artifacts. */
internal fun recoverySafetyAudit(
    entry: PallaSyncRecoverySetStore.Entry,
    lostShareId: String?,
): PallaSyncPendingEvent {
    val holderKind =
        lostShareId?.let { id ->
            checkNotNull(
                entry.manifest.getValue("holders").jsonArray.singleOrNull {
                    it.jsonObject
                        .getValue("recovery_share_id")
                        .jsonPrimitive.content == id
                },
            ) { "C_RECOVERY_SET_MISMATCH" }.jsonObject.getValue("holder_kind").jsonPrimitive.content
        }
    return recoveryAudit(entry, if (lostShareId == null) "recovery_share_compromised" else "recovery_share_lost", "succeeded", holderKind)
}

internal fun recoveryAudit(
    entry: PallaSyncRecoverySetStore.Entry,
    event: String,
    result: String,
    holderKind: String? = null,
): PallaSyncPendingEvent {
    check(event in recoveryAuditEvents && result in setOf("started", "succeeded", "failed", "blocked")) { "C_SCHEMA_VIOLATION" }
    check(holderKind == null || holderKind in setOf("device", "offline")) { "C_SCHEMA_VIOLATION" }
    check(holderKind == null || !(event.startsWith("recovery_share_set_") || event.startsWith("recovery_ceremony_"))) {
        "C_SCHEMA_VIOLATION"
    }
    PallaSyncCore.validateRecoverySetState(entry.manifestJson, entry.statusesJson, entry.admin, entry.head)
    val kinds =
        entry.manifest
            .getValue("holders")
            .jsonArray
            .map {
                it.jsonObject
                    .getValue("holder_kind")
                    .jsonPrimitive.content
            }
    return PallaSyncPendingEvent(
        "pallasync.audit/3",
        UUID.randomUUID().toString(),
        "upsert",
        buildJsonObject {
            put("event", event)
            put("subject_id", JsonNull)
            put("object_hash", JsonNull)
            put("result", result)
            put(
                "ext",
                buildJsonObject {
                    put(
                        "org.palleria.recovery",
                        buildJsonObject {
                            put("recovery_set_id", entry.id)
                            put("recovery_secret_fingerprint", entry.manifest.getValue("recovery_secret_fingerprint"))
                            put("threshold", entry.threshold)
                            put("total_shares", entry.total)
                            put("holder_kind", holderKind?.let(::JsonPrimitive) ?: JsonNull)
                            put(
                                "holder_counts",
                                buildJsonObject {
                                    put("device", kinds.count { it == "device" })
                                    put("offline", kinds.count { it == "offline" })
                                },
                            )
                        },
                    )
                },
            )
        },
        createdAtMs = System.currentTimeMillis(),
    )
}

internal val recoveryAuditEvents =
    setOf(
        "recovery_share_set_created",
        "recovery_share_set_rotated",
        "recovery_share_holder_added",
        "recovery_share_holder_removed",
        "recovery_ceremony_started",
        "recovery_ceremony_succeeded",
        "recovery_ceremony_failed",
        "recovery_share_lost",
        "recovery_share_compromised",
    )
