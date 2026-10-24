package com.yunfie.illustia.pallasync

import com.yunfie.illustia.settings.SyncedCollectionsSnapshot
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64

class PallaSyncInitialRecordJniTest :
    StringSpec({
        val json = Json { ignoreUnknownKeys = true }

        "epoch JNI verifies commitment and actual HPKE delivery with owned mutable input" {
            val phrase = (List(23) { "abandon" } + "art").joinToString(" ")
            PallaSyncCore.setSecretForeground(true)
            val root = PallaSyncCore.beginRootSession(phrase.toByteArray(), byteArrayOf(), "genesis", null, null)
            try {
                PallaSyncCore.createGenesisBundle(root, "epoch JNI", "os-keystore", "https://relay.example.com").use { prepared ->
                    val keys = prepared.snapshot()
                    val body = Json.parseToJsonElement(prepared.publicResult.requestBody).jsonObject
                    val epoch = body.getValue("epoch").jsonObject
                    val context =
                        buildJsonObject {
                            put("chain_id", epoch.getValue("chain_id"))
                            put("generation", epoch.getValue("generation"))
                            put("epoch", epoch.getValue("epoch"))
                            put("epoch_commitment", epoch.getValue("epoch_commitment"))
                            put("metadata_profile", "private")
                        }
                    val secret = Base64.getUrlDecoder().decode(keys.epochSecretBase64Url)
                    PallaSyncEpochSession.begin(context, secret).use { session ->
                        secret.all { it == 0.toByte() } shouldBe true
                        session.tag(SEARCH_HISTORY_SCHEMA_V3) shouldBe null
                        val envelope =
                            body.getValue("envelopes").let { it as kotlinx.serialization.json.JsonArray }.single {
                                it.jsonObject
                                    .getValue("recipient_kind")
                                    .jsonPrimitive.content == "device"
                            }
                        val private = Base64.getUrlDecoder().decode(keys.kexPrivateKeyBase64Url)
                        try {
                            PallaSyncCore.verifyDeviceKeyDelivery(session.handle, envelope.toString(), private) shouldBe true
                        } finally {
                            private.fill(0)
                        }
                        val metadata = session.decryptDevice(body.getValue("device_record").toString())
                        Json
                            .parseToJsonElement(metadata)
                            .jsonObject
                            .getValue("name")
                            .jsonPrimitive.content shouldBe "epoch JNI"
                    }
                    val bad =
                        JsonObject(
                            context +
                                (
                                    "epoch_commitment" to
                                        kotlinx.serialization.json.JsonPrimitive("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                                ),
                        )
                    val rejected = Base64.getUrlDecoder().decode(keys.epochSecretBase64Url)
                    shouldThrow<IllegalStateException> { PallaSyncEpochSession.begin(bad, rejected) }.message shouldBe
                        "C_COMMITMENT_MISMATCH"
                    rejected.all { it == 0.toByte() } shouldBe true
                }
            } finally {
                PallaSyncCore.endRootSession(root)
            }
        }

        "a typed device session reconstructs exact journal bytes and stays closed after cancellation" {
            val seed = ByteArray(32) { 1 }
            val key = ByteArray(32) { 2 }
            val handle = PallaSyncCore.beginDeviceSession(seed, key)
            val inner =
                PallaSyncInnerRecord(
                    SEARCH_HISTORY_SCHEMA_V3,
                    1,
                    null,
                    listOf(
                        PallaSyncOperation("journal", "upsert", 1, 1, body = buildJsonObject { put("query", "journal") }),
                    ),
                )
            val frame = PallaSyncCore.prepareRecordFrame(json.encodeToString(inner).toByteArray())
            val context =
                buildJsonObject {
                    put("chain_id", "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc")
                    put("generation", 0)
                    put("record_id", "11111111-1111-4111-8111-111111111111")
                    put("device_id", "22222222-2222-4222-8222-222222222222")
                    put("epoch", 0)
                    put("collection_tag", kotlinx.serialization.json.JsonNull)
                }.toString()
            val nonce = ByteArray(24) { 3 }
            try {
                val record = PallaSyncCore.createPreparedRecord(handle, context, frame, nonce)
                PallaSyncCore.createPreparedRecord(handle, context, frame, nonce) shouldBe record
                Json.parseToJsonElement(
                    checkNotNull(
                        PallaSyncCore.decryptOwnPreparedRecord(handle, record),
                    ),
                ) shouldBe
                    Json.parseToJsonElement(json.encodeToString(inner))
                PallaSyncCore.endDeviceSession(handle)
                shouldThrow<IllegalStateException> { PallaSyncCore.createPreparedRecord(handle, context, frame, nonce) }.message shouldBe
                    "C_BOOTSTRAP_TRUST_REQUIRED"
            } finally {
                PallaSyncCore.endDeviceSession(handle)
                seed.fill(0)
                key.fill(0)
                frame.fill(0)
            }
        }

        "initial settings with an empty operation context survive real JNI encryption and decryption" {
            val event = buildInitialSettingsSyncEvents(SyncedCollectionsSnapshot(searchHistory = listOf("first query"))).single()
            val operation =
                PallaSyncOperation(
                    entityId = event.entityId,
                    operation = event.operation,
                    lamport = 1,
                    createdAtMs = 1,
                    context = event.context,
                    body = event.body as JsonObject,
                )
            assertNativeRecordRoundTrip(json, operation)
        }

        "delete and clear keep the mandatory empty body through the real JNI boundary" {
            listOf(
                PallaSyncOperation(entityId = "first query", operation = "delete", lamport = 1, createdAtMs = 1),
                PallaSyncOperation(
                    entityId = "*",
                    operation = "clear",
                    lamport = 1,
                    createdAtMs = 1,
                    context = buildJsonObject { put("scope", "all") },
                ),
            ).forEach { assertNativeRecordRoundTrip(json, it) }
        }
    })

private fun assertNativeRecordRoundTrip(
    json: Json,
    operation: PallaSyncOperation,
) {
    val bundle =
        PallaSyncRootSession
            .begin(
                PallaSyncCore.generateSeedPhrase().toByteArray(Charsets.UTF_8),
                byteArrayOf(),
                PallaSyncRootSession.Operation.GENESIS,
            ).use { root ->
                PallaSyncCore
                    .createGenesisBundle(root.handle, "JNI test device", aud = "https://relay.example.com")
                    .use { it.snapshot() }
            }
    val inner = PallaSyncInnerRecord(collection = SEARCH_HISTORY_SCHEMA_V3, deviceSeq = 1, operations = listOf(operation))
    val encoded = json.encodeToString(inner)
    val context =
        buildJsonObject {
            put("chain_id", checkNotNull(bundle.chainId))
            put("generation", 0)
            put("record_id", newPallaSyncRecordId())
            put("device_id", checkNotNull(bundle.deviceId))
            put("epoch", 0)
            put("collection_tag", kotlinx.serialization.json.JsonNull)
        }
    val input = encoded.toByteArray()
    val frame =
        try {
            PallaSyncCore.prepareRecordFrame(input)
        } finally {
            input.fill(0)
        }
    val record =
        try {
            PallaSyncDeviceSession.begin(bundle).use { session ->
                val nonce = ByteArray(24).also(java.security.SecureRandom()::nextBytes)
                PallaSyncCore.createPreparedRecord(session.handle, context.toString(), frame, nonce)
            }
        } finally {
            frame.fill(0)
        }
    PallaSyncCore.verifySyncRecord(record, bundle.publicKeyBase64Url) shouldBe true
    val decrypted = PallaSyncDeviceSession.begin(bundle).use { PallaSyncCore.decryptOwnPreparedRecord(it.handle, record) }
    json.parseToJsonElement(decrypted) shouldBe json.parseToJsonElement(encoded)
    json.decodeFromString<PallaSyncInnerRecord>(decrypted) shouldBe inner
    json.parseToJsonElement(encoded).jsonObject.containsKey("prev_record_hash") shouldBe true
}
