package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

class PallaSyncEpochProposalJniTest :
    StringSpec({
        "foreground root prepares a complete revocation and mutable normal keys cross JNI without root JSON" {
            val f =
                Json
                    .parseToJsonElement(
                        PallaSyncEpochProposalJniTest::class.java
                            .getResourceAsStream(
                                "/pallasync/python-genesis-acceptance.json",
                            )!!
                            .bufferedReader()
                            .use {
                                it.readText()
                            },
                    ).jsonObject
            val enrol = f.getValue("enrollment").jsonObject
            var authority =
                PallaSyncCore.beginAuthorityHistory(
                    f.getValue("parameters").toString().toByteArray(),
                    f.getValue("identity").toString(),
                    f.getValue("origin").jsonPrimitive.content,
                    f.getValue("relay_public_key").jsonPrimitive.content,
                )
            val receipts =
                listOf(f.getValue("created"), f.getValue("record_upload"), enrol.getValue("accepted"))
                    .flatMap { it.jsonObject.getValue("upload_receipts").jsonArray }
                    .associateBy {
                        it.jsonObject
                            .getValue("relay_seq")
                            .jsonPrimitive.long
                    }
            for (page in f.getValue("log_pages").jsonArray + enrol.getValue("log_pages").jsonArray) {
                for (entry in page.jsonObject.getValue("items").jsonArray) {
                    val seq =
                        entry.jsonObject
                            .getValue("relay_seq")
                            .jsonPrimitive.long
                    authority = PallaSyncCore.advanceAuthorityHistory(authority, entry.toString(), receipts.getValue(seq).toString())
                }
            }
            val identity = f.getValue("identity").jsonObject
            val chain = identity.getValue("chain_id").jsonPrimitive.content
            val oldDevice =
                f
                    .getValue("genesis")
                    .jsonObject
                    .getValue("device_record")
                    .jsonObject
                    .getValue("device_id")
                    .jsonPrimitive.content
            PallaSyncCore.setSecretForeground(true)
            val root =
                PallaSyncRootSession.begin(
                    (
                        List(23) {
                            "abandon"
                        } + "art"
                    ).joinToString(
                        " ",
                    ).toByteArray(),
                    byteArrayOf(),
                    PallaSyncRootSession.Operation.REVOKE,
                    chain,
                    identity
                        .getValue(
                            "admin_public_key",
                        ).jsonPrimitive.content,
                )
            val request =
                buildJsonObject {
                    put("reason", "device_revocation")
                    put("signer_kind", "admin")
                    put("signer_device_id", JsonNull)
                    put("revoked_device_ids", JsonArray(listOf(JsonPrimitive(oldDevice))))
                    put("created_at_ms", 1791684000000L)
                }
            try {
                PallaSyncCore
                    .prepareEpochBundle(
                        root.handle,
                        root.operation.wire,
                        authority,
                        request.toString(),
                        byteArrayOf(),
                    ).use { prepared ->
                        val public = prepared.publicResult
                        public.chainId shouldBe chain
                        public.epoch shouldBe 1
                        public.requestBody
                            .getValue("envelopes")
                            .jsonArray.size shouldBe 2
                        val epoch = public.requestBody.getValue("epoch").jsonObject
                        val context =
                            buildJsonObject {
                                put("chain_id", chain)
                                put("generation", 0)
                                put("epoch", 1)
                                put("epoch_commitment", epoch.getValue("epoch_commitment"))
                                put("metadata_profile", "private")
                            }
                        val secret = prepared.epochSecret()
                        try {
                            PallaSyncCore.deriveEpochMaterial(context.toString(), secret).use { material ->
                                val previous =
                                    PallaSyncKeySnapshot(
                                        chain,
                                        encryptionKeyBase64Url = "",
                                        signingKeyBase64Url = "",
                                        publicKeyBase64Url = "",
                                    )
                                material.snapshot(previous, 1) shouldBe prepared.snapshot(previous)
                            }
                        } finally {
                            secret.fill(0)
                        }
                        secret.all { it == 0.toByte() } shouldBe true
                        prepared.close()
                        shouldThrow<IllegalStateException> { prepared.epochSecret() }.message shouldBe "C_BOOTSTRAP_TRUST_REQUIRED"
                    }
            } finally {
                root.close()
            }
            shouldThrow<IllegalStateException> {
                PallaSyncCore.prepareEpochBundle(root.handle, root.operation.wire, authority, request.toString(), byteArrayOf())
            }.message shouldBe
                "C_RECOVERY_AUTH_REQUIRED"
        }
    })
