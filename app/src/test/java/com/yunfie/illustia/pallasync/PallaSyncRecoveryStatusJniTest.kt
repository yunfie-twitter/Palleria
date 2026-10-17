package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

class PallaSyncRecoveryStatusJniTest :
    StringSpec({
        "a fresh explicitly scoped root can retire an interrupted prepared set without recovering old RMS" {
            PallaSyncCore.setSecretForeground(true)
            val phrase = (List(23) { "abandon" } + "art").joinToString(" ")
            val prepared =
                PallaSyncRootSession.beginText(phrase, "", PallaSyncRootSession.Operation.RECOVERY_CREATE).use { root ->
                    val holders =
                        JsonArray(
                            List(3) {
                                buildJsonObject {
                                    put("holder_kind", "offline")
                                    put("holder_device_id", JsonNull)
                                    put("holder_instance_id", UUID.randomUUID().toString())
                                }
                            },
                        )
                    Json
                        .parseToJsonElement(
                            PallaSyncCore.prepareRecoverySet(root.handle, root.operation.wire, 0, 2, holders.toString(), 1),
                        ).jsonObject
                }
            val manifest = prepared.getValue("manifest").jsonObject
            val statuses = prepared.getValue("status_chain").jsonArray
            val head = checkNotNull(PallaSyncCore.objectHash(statuses.last().toString()))
            PallaSyncRootSession.beginText(phrase, "", PallaSyncRootSession.Operation.RECOVERY_DISABLE).use { root ->
                val retired =
                    Json
                        .parseToJsonElement(
                            PallaSyncCore.advanceRecoveryStatus(
                                root.handle,
                                root.operation.wire,
                                manifest.toString(),
                                statuses.toString(),
                                head,
                                "retired",
                                null,
                                2,
                            ),
                        ).jsonArray
                retired.size shouldBe 2
                retired
                    .last()
                    .jsonObject
                    .getValue("state")
                    .jsonPrimitive.content shouldBe "retired"
                retired
                    .last()
                    .jsonObject
                    .getValue("previous_status_hash")
                    .jsonPrimitive.content shouldBe head
                shouldThrow<IllegalStateException> {
                    PallaSyncCore.advanceRecoveryStatus(
                        root.handle,
                        root.operation.wire,
                        manifest.toString(),
                        retired.toString(),
                        head,
                        "compromised",
                        null,
                        3,
                    )
                }.message shouldBe "C_RECOVERY_SHARE_INVALID"
                shouldThrow<IllegalStateException> { PallaSyncCore.rootIdentity(root.handle) }.message shouldBe
                    "C_RECOVERY_AUTH_REQUIRED"
            }
        }
    })
