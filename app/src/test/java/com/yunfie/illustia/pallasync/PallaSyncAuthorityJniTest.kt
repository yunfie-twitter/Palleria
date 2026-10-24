package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Public Python/relay evidence crosses the actual JNI boundary without a shared crypto implementation. */
class PallaSyncAuthorityJniTest :
    StringSpec({
        fun evidence(): JsonObject =
            Json
                .parseToJsonElement(
                    PallaSyncAuthorityJniTest::class.java
                        .getResourceAsStream("/pallasync/python-genesis-acceptance.json")!!
                        .bufferedReader()
                        .use { it.readText() },
                ).jsonObject
        "reseed transparency starts from the specified chain relay T0" {
            val f = evidence()
            val receipt =
                f
                    .getValue("created")
                    .jsonObject
                    .getValue("upload_receipts")
                    .jsonArray
                    .first()
                    .jsonObject
            val t0 =
                f
                    .getValue("transparency_pages")
                    .jsonArray
                    .first()
                    .jsonObject
                    .getValue("anchor_hash")
                    .jsonPrimitive.content
            pallaSyncTransparencyZero(
                receipt.getValue("chain_id").jsonPrimitive.content,
                receipt.getValue("transparency_id").jsonPrimitive.content,
                receipt.getValue("relay_key_id").jsonPrimitive.content,
            ) shouldBe t0
        }
        "selected full history and mnemonic enrollment cross every actual JNI validation boundary" {
            val f = evidence()
            var authority =
                Json
                    .parseToJsonElement(
                        PallaSyncCore.beginAuthorityHistory(
                            f.getValue("parameters").toString().toByteArray(),
                            f.getValue("identity").toString(),
                            f.getValue("origin").jsonPrimitive.content,
                            f.getValue("relay_public_key").jsonPrimitive.content,
                        ),
                    ).jsonObject
            val pin = authority.getValue("pin").toString()
            val enrol = f.getValue("enrollment").jsonObject
            val receipts =
                (
                    f
                        .getValue("created")
                        .jsonObject
                        .getValue("upload_receipts")
                        .jsonArray +
                        f
                            .getValue("record_upload")
                            .jsonObject
                            .getValue("upload_receipts")
                            .jsonArray +
                        enrol
                            .getValue("accepted")
                            .jsonObject
                            .getValue("upload_receipts")
                            .jsonArray
                ).associateBy {
                    it.jsonObject
                        .getValue("relay_seq")
                        .jsonPrimitive.long
                }
            val pages = f.getValue("log_pages").jsonArray + enrol.getValue("log_pages").jsonArray
            pages.forEach { value ->
                val page =
                    Json
                        .parseToJsonElement(
                            PallaSyncCore.validateLogPage(
                                value.toString().toByteArray(),
                                pin,
                                0,
                                authority.getValue("sequence").jsonPrimitive.long,
                                200,
                            ),
                        ).jsonObject
                page.getValue("items").jsonArray.forEach { entry ->
                    val receipt =
                        receipts.getValue(
                            entry.jsonObject
                                .getValue("relay_seq")
                                .jsonPrimitive.long,
                        )
                    val response =
                        buildJsonObject {
                            put("entry", entry)
                            put("receipt", receipt)
                        }
                    val checked = PallaSyncCore.validateReceiptResponse(response.toString().toByteArray(), entry.toString(), pin)
                    authority =
                        Json
                            .parseToJsonElement(
                                PallaSyncCore.advanceAuthorityHistory(authority.toString(), entry.toString(), checked),
                            ).jsonObject
                }
            }
            authority.getValue("sequence").jsonPrimitive.long shouldBe 9L
            val cut =
                PallaSyncCore.validateInitialHistoryCut(
                    authority.toString(),
                    buildJsonObject {
                        put("head", enrol.getValue("transparency_head"))
                    }.toString().toByteArray(),
                )
            Json
                .parseToJsonElement(cut)
                .jsonObject
                .getValue("zero_anchor")
                .jsonObject
                .getValue("index")
                .jsonPrimitive.long shouldBe 0L
            val recovery =
                f.getValue("genesis").jsonObject.getValue("envelopes").jsonArray.single {
                    it.jsonObject
                        .getValue("recipient_kind")
                        .jsonPrimitive.content ==
                        "recovery"
                }
            Json.parseToJsonElement(
                PallaSyncCore.validateRecoveryEnvelopeResponse(
                    buildJsonObject {
                        put("envelope", recovery)
                    }.toString().toByteArray(),
                    authority.toString(),
                    0,
                ),
            ) shouldBe
                recovery
            PallaSyncCore.validateEnrollmentAcceptance(
                enrol.getValue("accepted").toString().toByteArray(),
                enrol.getValue("body").toString(),
                pin,
            )
            val ack = enrol.getValue("acked").jsonObject
            PallaSyncCore.validateKeysAck(
                ack.toString().toByteArray(),
                ack.getValue("device_id").jsonPrimitive.content,
                ack.getValue("epoch_hash").jsonPrimitive.content,
            )
            val altered = JsonObject(ack + ("epoch_hash" to JsonPrimitive("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")))
            shouldThrow<IllegalStateException> {
                PallaSyncCore.validateKeysAck(
                    altered.toString().toByteArray(),
                    ack.getValue("device_id").jsonPrimitive.content,
                    ack.getValue("epoch_hash").jsonPrimitive.content,
                )
            }
        }
        "only a logged admin-rooted subject is accepted from a mutable registry" {
            val fixture =
                Json
                    .parseToJsonElement(
                        PallaSyncAuthorityJniTest::class.java
                            .getResourceAsStream("/pallasync/python-genesis-acceptance.json")!!
                            .bufferedReader()
                            .use { it.readText() },
                    ).jsonObject
            val accepted =
                PallaSyncCore.validateGenesisAcceptance(
                    fixture.getValue("created").toString().toByteArray(),
                    fixture.getValue("genesis").toString(),
                    fixture.getValue("identity").toString(),
                    fixture.getValue("origin").jsonPrimitive.content,
                    fixture.getValue("relay_public_key").jsonPrimitive.content,
                )
            val checkpoint = Json.parseToJsonElement(accepted).jsonObject
            val authority = checkpoint.getValue("authority").toString()
            val device =
                fixture
                    .getValue("genesis")
                    .jsonObject
                    .getValue("device_record")
                    .toString()
            Json.parseToJsonElement(PallaSyncCore.validateKnownDevice(authority, device)).jsonObject.getValue("device_record") shouldBe
                Json.parseToJsonElement(device)
            val replaced =
                JsonObject(
                    Json.parseToJsonElement(device).jsonObject +
                        ("meta_nonce" to JsonPrimitive("AAECAwQFBgcICQoLDA0ODxAREhMUFRYX")),
                )
            shouldThrow<IllegalStateException> {
                PallaSyncCore.validateKnownDevice(authority, replaced.toString())
            }.message shouldBe "C_SIGNATURE_INVALID"
        }
        "the independent relay transparency range must include the exact original upload receipt" {
            val fixture = evidence()
            val checkpoint =
                Json
                    .parseToJsonElement(
                        PallaSyncCore.validateGenesisAcceptance(
                            fixture.getValue("created").toString().toByteArray(),
                            fixture.getValue("genesis").toString(),
                            fixture.getValue("identity").toString(),
                            fixture.getValue("origin").jsonPrimitive.content,
                            fixture.getValue("relay_public_key").jsonPrimitive.content,
                        ),
                    ).jsonObject
            val pin =
                buildJsonObject {
                    put("origin", fixture.getValue("origin"))
                    put("chain_id", fixture.getValue("identity").jsonObject.getValue("chain_id"))
                    put("public_key", fixture.getValue("relay_public_key"))
                    put("log_id", checkpoint.getValue("log_id"))
                }.toString()
            var anchor =
                buildJsonObject {
                    put("transparency_id", checkpoint.getValue("transparency_id"))
                    put("index", 0)
                    put("hash", checkpoint.getValue("transparency_hash"))
                }
            val local =
                buildJsonObject {
                    put("anchor", anchor)
                    put("verified_seq", checkpoint.getValue("head_seq"))
                    put("verified_hash", checkpoint.getValue("head_hash"))
                    put("parameters_hash", checkpoint.getValue("parameters_hash"))
                    put("epoch_hash", checkpoint.getValue("epoch_hash"))
                }.toString()
            val expected = fixture.getValue("transparency_head")
            val target =
                PallaSyncCore.validateTransparencyHead(
                    buildJsonObject { put("head", expected) }.toString().toByteArray(),
                    pin,
                    local,
                )
            Json.parseToJsonElement(target) shouldBe expected
            val log = PallaSyncCore.validateLogHead(fixture.getValue("log_head").toString().toByteArray())
            PallaSyncCore.compareLogHeads(log, target, pin, 0) shouldBe true
            val advanced = JsonObject(Json.parseToJsonElement(log).jsonObject + ("head_seq" to JsonPrimitive(7)))
            PallaSyncCore.compareLogHeads(advanced.toString(), target, pin, 0) shouldBe false
            val missingGeneration = JsonObject(Json.parseToJsonElement(log).jsonObject - "generation")
            shouldThrow<IllegalStateException> {
                PallaSyncCore.validateLogHead(missingGeneration.toString().toByteArray())
            }.message shouldBe "C_SCHEMA_VIOLATION"
            val pages = fixture.getValue("transparency_pages").jsonArray
            pages.forEach { page ->
                val result =
                    Json
                        .parseToJsonElement(
                            PallaSyncCore.validateTransparencyPage(page.toString().toByteArray(), pin, anchor.toString(), target, 200),
                        ).jsonObject
                anchor = result.getValue("next_anchor").jsonObject
            }
            val receipt =
                fixture
                    .getValue("record_upload")
                    .jsonObject
                    .getValue("upload_receipts")
                    .jsonArray
                    .single()
                    .toString()
            val entry =
                pages
                    .last()
                    .jsonObject
                    .getValue("items")
                    .jsonArray
                    .last()
                    .toString()
            PallaSyncCore.validateReceiptInclusion(receipt, entry, pin, anchor.toString()) shouldBe true
            shouldThrow<IllegalStateException> {
                PallaSyncCore.validateReceiptInclusion(
                    receipt,
                    pages
                        .first()
                        .jsonObject
                        .getValue("items")
                        .jsonArray
                        .first()
                        .toString(),
                    pin,
                    anchor.toString(),
                )
            }
        }
    })
