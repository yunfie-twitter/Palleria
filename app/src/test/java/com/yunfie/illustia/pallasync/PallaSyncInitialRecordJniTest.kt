package com.yunfie.illustia.pallasync

import com.yunfie.illustia.settings.SyncedCollectionsSnapshot
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class PallaSyncInitialRecordJniTest :
    StringSpec({
        val json = Json { ignoreUnknownKeys = true }

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
        json.decodeFromString<PallaSyncGenesisBundle>(
            requireNotNull(
                PallaSyncCore.createGenesisBundle(
                    seedPhrase = PallaSyncCore.generateSeedPhrase(),
                    deviceName = "JNI test device",
                    aud = "https://relay.example.com",
                ),
            ),
        )
    val inner = PallaSyncInnerRecord(collection = SEARCH_HISTORY_SCHEMA_V3, deviceSeq = 1, operations = listOf(operation))
    val encoded = json.encodeToString(inner)
    val record =
        requireNotNull(
            PallaSyncCore.createSyncRecord(
                chainId = bundle.chainId,
                generation = 0,
                recordId = newPallaSyncRecordId(),
                deviceId = bundle.deviceId,
                epoch = 0,
                collectionTag = null,
                innerRecordJson = encoded,
                recordKeyBase64 = bundle.recordKey,
                signingKeyBase64 = bundle.deviceSigningKey,
            ),
        ) { "Native SyncRecord creation returned null for a Kotlin-produced InnerRecord" }
    PallaSyncCore.verifySyncRecord(record, bundle.devicePublicKey) shouldBe true
    val decrypted = requireNotNull(PallaSyncCore.decryptSyncRecord(record, bundle.recordKey))
    json.parseToJsonElement(decrypted) shouldBe json.parseToJsonElement(encoded)
    json.decodeFromString<PallaSyncInnerRecord>(decrypted) shouldBe inner
    json.parseToJsonElement(encoded).jsonObject.containsKey("prev_record_hash") shouldBe true
}
