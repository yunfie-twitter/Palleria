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

/** Actual JNI key-array allocation, bound to independently accepted public source history. */
class PallaSyncPeerPreparedJniTest :
    StringSpec({
        "confirmed peers produce a typed subject and close overwrites all six mutable keys" {
            PallaSyncCore.setSecretForeground(true)
            val fixture = publicPeerFixture()
            PallaSyncPeerSession.subject(fixture.offer, fixture.authority.toString(), true, fixture.now).use { subject ->
                val signing = ByteArray(32) { (it + 96).toByte() } // Public deterministic test seed only.
                val kex = ByteArray(32) { it.toByte() }
                PallaSyncPeerSession.approver(fixture.offer, fixture.authority.toString(), signing, kex, fixture.now).use { approver ->
                    signing.all { it == 0.toByte() } shouldBe true
                    kex.all { it == 0.toByte() } shouldBe true
                    val visual = approver.bindRequest(checkNotNull(subject.requestJson), fixture.now)
                    visual shouldBe subject.sas(fixture.now)
                    subject.confirm(visual, true, fixture.now)
                    approver.confirm(visual, true, fixture.now)
                    subject.acceptCertificate(approver.certificate(fixture.now), fixture.now)
                    val secret = ByteArray(32) { (it + 0xa0).toByte() }
                    val envelope = approver.envelope(fixture.authority.toString(), 0, secret, fixture.now)
                    secret.all { it == 0.toByte() } shouldBe true
                    val metadata =
                        buildJsonObject {
                            put("name", "JNI paired fixture")
                            put("key_protection", "os-keystore")
                        }
                    val prepared = subject.prepareSubject(fixture.authority.toString(), envelope, metadata.toString(), fixture.now)
                    val field = PallaSyncPreparedKeys::class.java.getDeclaredField("material").apply { isAccessible = true }
                    val material = field.get(prepared) as Array<*>
                    try {
                        material.size shouldBe 6
                        prepared.snapshot().deviceId shouldBe prepared.publicResult.deviceId
                        val body = approver.approval(fixture.authority.toString(), prepared.publicResult.requestBody, fixture.now)
                        Json.parseToJsonElement(body) shouldBe Json.parseToJsonElement(prepared.publicResult.requestBody)
                        prepared.publicResult.keysAckBody?.let {
                            Json
                                .parseToJsonElement(
                                    it,
                                ).jsonObject
                                .getValue("epoch_hash")
                                .jsonPrimitive.content
                        } shouldBe
                            Json
                                .parseToJsonElement(fixture.offer)
                                .jsonObject
                                .getValue("epoch_hash")
                                .jsonPrimitive.content
                    } finally {
                        prepared.close()
                    }
                    material.all { (it as ByteArray).all { byte -> byte == 0.toByte() } } shouldBe true
                    shouldThrow<IllegalStateException> { prepared.snapshot() }.message shouldBe "C_BOOTSTRAP_TRUST_REQUIRED"
                }
            }
        }
    })

private data class PeerPreparedFixture(
    val offer: String,
    val authority: JsonObject,
    val now: Long,
)

private fun publicPeerFixture(): PeerPreparedFixture {
    val input = PallaSyncPeerPreparedJniTest::class.java.getResourceAsStream("/pallasync/python-peer-genesis.json")!!
    val fixture = Json.parseToJsonElement(input.bufferedReader().use { it.readText() }).jsonObject
    val created = fixture.getValue("created").jsonObject
    val authority =
        Json
            .parseToJsonElement(
                PallaSyncCore.validateGenesisAcceptance(
                    created.toString().toByteArray(),
                    fixture.getValue("genesis").toString(),
                    fixture.getValue("identity").toString(),
                    fixture.getValue("origin").jsonPrimitive.content,
                    fixture.getValue("relay_public_key").jsonPrimitive.content,
                ),
            ).jsonObject
            .getValue("authority")
            .jsonObject
    val now =
        created
            .getValue("upload_receipts")
            .jsonArray
            .first()
            .jsonObject
            .getValue("received_at_ms")
            .jsonPrimitive.long
    val request =
        buildJsonObject {
            put("request_id", UUID.randomUUID().toString())
            put("offer_nonce", Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 }))
            put("expires_at_ms", now + 300_000)
        }
    val offer = PallaSyncCore.createPeerOffer(authority.toString(), "11111111-1111-4111-8111-111111111111", request.toString(), now)
    return PeerPreparedFixture(offer, authority, now)
}
