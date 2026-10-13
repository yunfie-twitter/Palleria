package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

private val recoveryTestPhrase = (List(23) { "abandon" } + "art").joinToString(" ")

class PallaSyncRecoveryJniTest :
    StringSpec({
        fun holders() =
            JsonArray(
                List(3) {
                    buildJsonObject {
                        put("holder_kind", "offline")
                        put("holder_device_id", JsonNull)
                        put("holder_instance_id", UUID.randomUUID().toString())
                    }
                },
            )

        "prepared recovery output is public only and denial consumes a typed pair session" {
            PallaSyncCore.setSecretForeground(true)
            PallaSyncRootSession.beginText(recoveryTestPhrase, "", PallaSyncRootSession.Operation.RECOVERY_CREATE).use { root ->
                val set =
                    Json
                        .parseToJsonElement(
                            PallaSyncCore.prepareRecoverySet(root.handle, root.operation.wire, 0, 2, holders().toString(), 1),
                        ).jsonObject
                set.keys shouldBe setOf("manifest", "blob", "status_chain")
                val manifest = set.getValue("manifest").jsonObject
                manifest.getValue("profile").jsonPrimitive.content shouldBe "shamir-gf256-aes/1"
                val ceremony = UUID.randomUUID().toString()
                val session = UUID.randomUUID().toString()
                PallaSyncCore
                    .beginRecoveryTransfer(
                        manifest.toString(),
                        root.identity.adminPublicKey,
                        ceremony,
                        session,
                        "initiator",
                        "dealer",
                        "create",
                    ).use { dealer ->
                        PallaSyncCore
                            .beginRecoveryTransfer(
                                manifest.toString(),
                                root.identity.adminPublicKey,
                                ceremony,
                                session,
                                "responder",
                                "holder",
                                "create",
                            ).use { holder ->
                                val visual = PallaSyncCore.bindRecoveryTransfer(dealer.handle, holder.descriptorJson)
                                visual shouldBe PallaSyncCore.bindRecoveryTransfer(holder.handle, dealer.descriptorJson)
                                visual.matches(Regex("[0-9a-f]{4} [0-9a-f]{4} [0-9a-f]{4}")) shouldBe true
                                shouldThrow<IllegalStateException> {
                                    PallaSyncCore.confirmRecoveryTransfer(
                                        dealer.handle,
                                        visual,
                                        false,
                                    )
                                }.message shouldBe
                                    "C_RECOVERY_AUTH_REQUIRED"
                                shouldThrow<IllegalStateException> {
                                    PallaSyncCore.bindRecoveryTransfer(
                                        dealer.handle,
                                        holder.descriptorJson,
                                    )
                                }.message shouldBe
                                    "C_RECOVERY_SESSION_EXPIRED"
                            }
                    }
            }
        }

        "a verified transfer gap waits without consumption and exact missing packets resume through JNI" {
            PallaSyncCore.setSecretForeground(true)
            PallaSyncRootSession.beginText(recoveryTestPhrase, "", PallaSyncRootSession.Operation.RECOVERY_CREATE).use { root ->
                val set =
                    Json
                        .parseToJsonElement(
                            PallaSyncCore.prepareRecoverySet(root.handle, root.operation.wire, 0, 2, holders().toString(), 1),
                        ).jsonObject
                val manifest = set.getValue("manifest").toString()
                val ceremony = UUID.randomUUID().toString()
                val session = UUID.randomUUID().toString()
                PallaSyncCore
                    .beginRecoveryTransfer(
                        manifest,
                        root.identity.adminPublicKey,
                        ceremony,
                        session,
                        "initiator",
                        "dealer",
                        "create",
                    ).use { dealer ->
                        PallaSyncCore
                            .beginRecoveryTransfer(
                                manifest,
                                root.identity.adminPublicKey,
                                ceremony,
                                session,
                                "responder",
                                "holder",
                                "create",
                            ).use { holder ->
                                val visual = PallaSyncCore.bindRecoveryTransfer(dealer.handle, holder.descriptorJson)
                                visual shouldBe PallaSyncCore.bindRecoveryTransfer(holder.handle, dealer.descriptorJson)
                                PallaSyncCore.confirmRecoveryTransfer(dealer.handle, visual, true)
                                PallaSyncCore.confirmRecoveryTransfer(holder.handle, visual, true)
                                val first = PallaSyncCore.createRecoveryResult(dealer.handle, "succeeded", null)
                                val second = PallaSyncCore.createRecoveryResult(dealer.handle, "failed", "C_RECOVERY_AUTH_REQUIRED")
                                shouldThrow<IllegalStateException> { PallaSyncCore.receiveRecoveryResult(holder.handle, second) }
                                    .message shouldBe "C_MISSING_DEPENDENCY"
                                PallaSyncCore.retryRecoveryTransfer(dealer.handle, 1) shouldBe first
                                val accepted = PallaSyncCore.receiveRecoveryResult(holder.handle, first)
                                Json
                                    .parseToJsonElement(accepted)
                                    .jsonObject
                                    .getValue("result")
                                    .jsonPrimitive.content shouldBe "succeeded"
                                Json
                                    .parseToJsonElement(PallaSyncCore.receiveRecoveryResult(holder.handle, second))
                                    .jsonObject
                                    .getValue("result")
                                    .jsonPrimitive.content shouldBe "failed"
                                PallaSyncCore.receiveRecoveryResult(holder.handle, first) shouldBe accepted
                            }
                    }
            }
        }

        "invalid threshold closes the operation root and cannot return private material" {
            PallaSyncCore.setSecretForeground(true)
            PallaSyncRootSession.beginText(recoveryTestPhrase, "", PallaSyncRootSession.Operation.RECOVERY_CREATE).use { root ->
                shouldThrow<IllegalStateException> {
                    PallaSyncCore.prepareRecoverySet(root.handle, root.operation.wire, 0, 1, holders().toString(), 1)
                }.message shouldBe
                    "C_RECOVERY_PARAMETER_INVALID"
                shouldThrow<IllegalStateException> { root.identity }.message shouldBe "C_RECOVERY_AUTH_REQUIRED"
            }
        }
    })
