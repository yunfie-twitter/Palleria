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
    val kinds =
        entry.manifest
            .getValue("holders")
            .jsonArray
            .map {
                it.jsonObject
                    .getValue("holder_kind")
                    .jsonPrimitive.content
            }
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
    return PallaSyncPendingEvent(
        "pallasync.audit/3",
        UUID.randomUUID().toString(),
        "upsert",
        buildJsonObject {
            put("event", if (lostShareId == null) "recovery_share_compromised" else "recovery_share_lost")
            put("subject_id", JsonNull)
            put("object_hash", JsonNull)
            put("result", "succeeded")
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
    )
}
