package com.yunfie.illustia.pallasync

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PallaSyncSnapshotVerifierAndroidTest {
    @Test
    fun jniCreatesReservedSnapshotRecordAndWipesTransferredDeviceSeed() {
        val chain = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
        val secret = ByteArray(32) { (0xa0 + it).toByte() }
        val context =
            buildJsonObject {
                put("chain_id", chain)
                put("generation", 0)
                put("epoch", 0)
                put("epoch_commitment", "3fT41zGvEMHJhoQzcQO_4BC_hPtkbiz6Ea7K-rmG9JM")
                put("metadata_profile", "queryable")
            }
        val header =
            buildJsonObject {
                put("kind", "header")
                put("chain_id", chain)
                put("generation", 0)
                put("log_id", "44444444-4444-4444-8444-444444444444")
                put("covered_through_seq", 0)
                put("covered_log_hash", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                put("epoch_hash", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                put("lamport_max", 0)
            }
        val part =
            buildJsonObject {
                put("type", "PALLASYNC-SNAPSHOT-v3")
                put("collection", "pallasync.snapshot/3")
                put("compaction_id", "55555555-5555-4555-8555-555555555555")
                put("part_index", 0)
                put("part_count", 1)
                put("entries", kotlinx.serialization.json.buildJsonArray { add(header) })
            }
        val seed = ByteArray(32) { 9 }
        PallaSyncEpochSession.begin(context, secret).use { session ->
            PallaSyncSnapshotVerifier
                .beginFromEpochSession(
                    session,
                    header.toString(),
                    "55555555-5555-4555-8555-555555555555",
                    1,
                ).use { verifier ->
                    verifier.acceptDecryptedAuthenticatedPart(checkNotNull(PallaSyncCore.canonicalJson(part.toString())))
                    assertNotNull(verifier.finishCommitment())
                }
            val record =
                Json
                    .parseToJsonElement(
                        session.createSnapshotRecord(
                            "11111111-1111-4111-8111-111111111111",
                            "22222222-2222-4222-8222-222222222222",
                            part.toString(),
                            seed,
                        ),
                    ).jsonObject
            assertEquals(chain, record.getValue("chain_id").jsonPrimitive.content)
            assertEquals(session.tag("pallasync.snapshot/3"), record.getValue("collection_tag").jsonPrimitive.content)
            assertNotNull(record.getValue("signature"))
        }
        assertEquals(true, seed.all { it == 0.toByte() })
        assertEquals(true, secret.all { it == 0.toByte() })
    }

    @Test
    fun jniVerifierConsumesCompleteStreamOnceAndRejectsWrongCommitment() {
        val key =
            byteArrayOf(
                0xD9.toByte(),
                0x6F,
                0xF7.toByte(),
                0x6D,
                0x5C,
                0x3A,
                0xD5.toByte(),
                0xDF.toByte(),
                0x74,
                0x29,
                0x15,
                0x3B,
                0x52,
                0x12,
                0x6E,
                0x43,
                0xA8.toByte(),
                0x18,
                0xC2.toByte(),
                0x23,
                0xF5.toByte(),
                0x3F,
                0xA3.toByte(),
                0xF5.toByte(),
                0x2D,
                0x7C,
                0xE1.toByte(),
                0x02,
                0x02,
                0x6C,
                0x09,
                0x74,
            )
        val header =
            """{"chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","covered_log_hash":"0zeJRLV2xNFxHkbAzYH-TSb1kOcmVyl9r4kiBsQLCHQ","covered_through_seq":0,"epoch_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","generation":0,"kind":"header","lamport_max":0,"log_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc"}"""
        val part =
            """{"collection":"pallasync.snapshot/3","compaction_id":"55555555-5555-4555-8555-555555555555","entries":[$header],"part_count":1,"part_index":0,"type":"PALLASYNC-SNAPSHOT-v3"}"""
        PallaSyncSnapshotVerifier.begin(key, header, "55555555-5555-4555-8555-555555555555", 1).use { verifier ->
            verifier.acceptDecryptedAuthenticatedPart(part)
            verifier.finish("cEWZYwyPF0UbkBBP8J9BJnU69OPDBciCrjHKk2gt1bU")
            assertThrows(IllegalStateException::class.java) { verifier.acceptDecryptedAuthenticatedPart(part) }
        }

        PallaSyncSnapshotVerifier.begin(key, header, "55555555-5555-4555-8555-555555555555", 1).use { verifier ->
            verifier.acceptDecryptedAuthenticatedPart(part)
            assertEquals("cEWZYwyPF0UbkBBP8J9BJnU69OPDBciCrjHKk2gt1bU", verifier.finishCommitment())
        }

        PallaSyncSnapshotVerifier.begin(key, header, "55555555-5555-4555-8555-555555555555", 1).use { verifier ->
            verifier.acceptDecryptedAuthenticatedPart(part)
            assertThrows(IllegalStateException::class.java) {
                verifier.finish("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            }
        }
        assertEquals(null, PallaSyncCore.objectHash("{\"same\":1,\"same\":2}"))
    }

    @Test
    fun jniSnapshotPartUploadValidatorRejectsEmptySubmittedBatch() {
        val pin =
            JsonObject(
                mapOf(
                    "origin" to kotlinx.serialization.json.JsonPrimitive("https://relay.example.com"),
                    "chain_id" to kotlinx.serialization.json.JsonPrimitive("ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"),
                    "public_key" to kotlinx.serialization.json.JsonPrimitive("T9CZzNR9eJPf6ewkQU7LDZtUICMqrTDZHEZb4zy-ZcQ"),
                    "log_id" to kotlinx.serialization.json.JsonPrimitive("cccccccc-cccc-4ccc-8ccc-cccccccccccc"),
                ),
            )
        val response = """{"accepted":[],"rejected":[],"upload_receipts":[]}""".toByteArray()
        assertThrows(IllegalStateException::class.java) {
            PallaSyncSnapshotVerifier.validateUploadedParts(response, JsonArray(emptyList()), pin)
        }
    }
}
