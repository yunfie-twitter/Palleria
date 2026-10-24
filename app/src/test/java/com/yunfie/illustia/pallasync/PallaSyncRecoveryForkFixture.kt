package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Two independently signed successors of the same prepared status, using only the public C.8 test root. */
internal fun recoveryStatusForks(packageJson: String): List<String> {
    PallaSyncCore.setSecretForeground(true)
    val value = Json.parseToJsonElement(packageJson).jsonObject
    val prepared = JsonArray(listOf(value.getValue("status_chain").jsonArray.first()))
    val head = checkNotNull(PallaSyncCore.objectHash(prepared.first().toString()))
    val phrase = (List(23) { "abandon" } + "art").joinToString(" ")
    val now = System.currentTimeMillis()
    return PallaSyncRootSession.beginText(phrase, "", PallaSyncRootSession.Operation.RECOVERY_DISABLE).use { root ->
        listOf(now, now + 1).map { timestamp ->
            PallaSyncCore.advanceRecoveryStatus(
                root.handle,
                root.operation.wire,
                value.getValue("manifest").toString(),
                prepared.toString(),
                head,
                "retired",
                null,
                timestamp,
            )
        }
    }
}
