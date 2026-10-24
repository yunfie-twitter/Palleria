package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.util.Base64
import java.util.UUID

/** Real JNI result allocation/lifetime, using independently signed public Python/relay history. */
class PallaSyncPeerJniTest :
    StringSpec({
        fun verifiedHistory(f: JsonObject): JsonObject {
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
            val created =
                f
                    .getValue("created")
                    .jsonObject
                    .getValue("upload_receipts")
                    .jsonArray
            val uploaded =
                f
                    .getValue("record_upload")
                    .jsonObject
                    .getValue("upload_receipts")
                    .jsonArray
            val receipts =
                (created + uploaded).associateBy {
                    it.jsonObject
                        .getValue("relay_seq")
                        .jsonPrimitive.long
                }
            for (page in f.getValue("log_pages").jsonArray) {
                for (entry in page.jsonObject.getValue("items").jsonArray) {
                    val receipt =
                        receipts.getValue(
                            entry.jsonObject
                                .getValue("relay_seq")
                                .jsonPrimitive.long,
                        )
                    val advanced = PallaSyncCore.advanceAuthorityHistory(authority.toString(), entry.toString(), receipt.toString())
                    authority = Json.parseToJsonElement(advanced).jsonObject
                }
            }
            return authority
        }

        fun fixture(): Triple<JsonObject, String, Long> {
            val input = PallaSyncPeerJniTest::class.java.getResourceAsStream("/pallasync/python-genesis-acceptance.json")!!
            val f = Json.parseToJsonElement(input.bufferedReader().use { it.readText() }).jsonObject
            val authority = verifiedHistory(f)
            val firstReceipt =
                f
                    .getValue("created")
                    .jsonObject
                    .getValue("upload_receipts")
                    .jsonArray
                    .first()
                    .jsonObject
            val now = firstReceipt.getValue("received_at_ms").jsonPrimitive.long
            val request =
                buildJsonObject {
                    put("request_id", UUID.randomUUID().toString())
                    put("offer_nonce", Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 }))
                    put("expires_at_ms", now + 300_000)
                }
            val device =
                f
                    .getValue("genesis")
                    .jsonObject
                    .getValue("device_record")
                    .jsonObject
            val offer =
                PallaSyncCore.createPeerOffer(
                    authority.toString(),
                    device.getValue("device_id").jsonPrimitive.content,
                    request.toString(),
                    now,
                )
            return Triple(authority, offer, now)
        }
        "typed subject JNI result contains only a public fresh request and requires explicit source and SAS" {
            PallaSyncCore.setSecretForeground(true)
            val (authority, offer, now) = fixture()
            shouldThrow<IllegalStateException> { PallaSyncPeerSession.subject(offer, authority.toString(), false, now) }.message shouldBe
                "C_BOOTSTRAP_TRUST_REQUIRED"
            PallaSyncPeerSession.subject(offer, authority.toString(), true, now).use { candidate ->
                val request = Json.parseToJsonElement(checkNotNull(candidate.requestJson)).jsonObject
                (candidate.handle > 0) shouldBe true
                request.getValue("request_id") shouldBe Json.parseToJsonElement(offer).jsonObject.getValue("request_id")
                request.keys shouldBe
                    setOf(
                        "request_id",
                        "chain_id",
                        "generation",
                        "device_id",
                        "device_public_key",
                        "device_kex_public_key",
                        "offer_nonce",
                        "created_at_ms",
                        "expires_at_ms",
                        "signature",
                    )
                val sas = candidate.sas(now)
                sas.matches(Regex("[0-9a-f]{12}")) shouldBe true
                shouldThrow<IllegalStateException> { candidate.confirm(sas, false, now) }.message shouldBe "C_BOOTSTRAP_TRUST_REQUIRED"
                shouldThrow<IllegalStateException> { candidate.sas(now) }.message shouldBe "C_BOOTSTRAP_TRUST_REQUIRED"
            }
        }

        "JNI lexical failure cannot retain an already allocated prospective device session" {
            PallaSyncCore.setSecretForeground(true)
            val (authority, offer, now) = fixture()
            PallaSyncPeerSession.subject(offer, authority.toString(), true, now).use { candidate ->
                shouldThrow<IllegalStateException> { candidate.acceptCertificate("{\"a\":1,\"a\":2}", now) }.message shouldBe
                    "C_SCHEMA_VIOLATION"
                shouldThrow<IllegalStateException> { candidate.sas(now) }.message shouldBe "C_BOOTSTRAP_TRUST_REQUIRED"
            }
        }

        "subject expiry and failed approver construction destroy their owned native or mutable inputs" {
            PallaSyncCore.setSecretForeground(true)
            val (authority, offer, now) = fixture()
            PallaSyncPeerSession.subject(offer, authority.toString(), true, now).use { candidate ->
                shouldThrow<IllegalStateException> { candidate.sas(now + 300_000) }.message shouldBe "C_BOOTSTRAP_TRUST_REQUIRED"
            }
            val signing = ByteArray(32) { 1 }
            val kex = ByteArray(32) { 2 }
            shouldThrow<IllegalStateException> { PallaSyncPeerSession.approver(offer, authority.toString(), signing, kex, now) }
            signing.all { it == 0.toByte() } shouldBe true
            kex.all { it == 0.toByte() } shouldBe true
        }
    })
