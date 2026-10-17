package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Real JNI: Kotlin receives public evidence only after Rust verifies the complete C.9 receipt. */
class PallaSyncAttestationJniTest :
    StringSpec({
        fun vector(name: String): String =
            PallaSyncAttestationJniTest::class.java
                .getResourceAsStream("/pallasync/attestations/$name.json")!!
                .bufferedReader()
                .use { it.readText() }
        "signed receipt verification binds exact bytes and fails without proof" {
            val record = Json.parseToJsonElement(vector("record")).jsonObject
            val receipt = Json.parseToJsonElement(vector("receipt")).jsonObject
            val pin =
                buildJsonObject {
                    put("origin", "https://relay.example.com")
                    put("chain_id", record.getValue("chain_id"))
                    put("public_key", "T9CZzNR9eJPf6ewkQU7LDZtUICMqrTDZHEZb4zy-ZcQ")
                    put("log_id", receipt.getValue("log_id"))
                }.toString()
            val body =
                """
                {"accepted":[{"record_id":${record.getValue("record_id")},"duplicate":false,
                "receipt":{"relay_seq":7,"received_at_ms":1791684000000,"kind":"record","object":$record}}],
                "rejected":[],"upload_receipts":[$receipt],"generation":0,"log_id":${receipt.getValue("log_id")},
                "head_seq":7,"head_hash":${receipt.getValue("resulting_head_hash")}}
                """.trimIndent()
            val result = PallaSyncCore.validateRecordUpload(body.toByteArray(), "[$record]", pin)
            Json.parseToJsonElement(result) shouldBe Json.parseToJsonElement(body)
            val repairBody = body.dropLast(1) + ",\"repair_required\":true}"
            Json.parseToJsonElement(
                PallaSyncCore.validateRecordRepair(repairBody.toByteArray(), "[$record]", pin),
            ) shouldBe Json.parseToJsonElement(repairBody)
            shouldThrow<IllegalStateException> {
                PallaSyncCore.validateRecordRepair(body.toByteArray(), "[$record]", pin)
            }.message shouldBe "C_SCHEMA_VIOLATION"
            shouldThrow<IllegalStateException> {
                PallaSyncCore.validateRecordUpload(body.replace("[$receipt]", "[]").toByteArray(), "[$record]", pin)
            }.message shouldBe "C_ACCEPTANCE_UNPROVEN"
            shouldThrow<IllegalStateException> {
                PallaSyncCore.validateRecordUpload(body.replace("1791684000000", "1791684000001").toByteArray(), "[$record]", pin)
            }.message shouldBe "C_SIGNATURE_INVALID"
            shouldThrow<IllegalStateException> {
                PallaSyncCore.validateRecordUpload(body.replace("\"generation\":0", "\"generation\":-0").toByteArray(), "[$record]", pin)
            }.message shouldBe "C_SCHEMA_VIOLATION"
        }
        "export attestation verifier rejects unsigned receipt and transparency evidence" {
            val pin =
                """{"origin":"https://relay.example.com","chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","public_key":"T9CZzNR9eJPf6ewkQU7LDZtUICMqrTDZHEZb4zy-ZcQ","log_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc"}"""
            shouldThrow<IllegalStateException> {
                PallaSyncCore.validateExportAttestation(
                    """{"kind":"upload_receipt","receipt":{}}""",
                    pin,
                )
            }.message shouldBe "C_SCHEMA_VIOLATION"
            shouldThrow<IllegalStateException> {
                PallaSyncCore.validateExportAttestation(
                    """{"entry":{},"kind":"transparency_entry"}""",
                    pin,
                )
            }.message shouldBe "C_SCHEMA_VIOLATION"
            shouldThrow<IllegalStateException> {
                PallaSyncCore.validateExportAttestation(
                    """{"head":{},"kind":"transparency_head"}""",
                    pin,
                )
            }.message shouldBe "C_SCHEMA_VIOLATION"
        }
        "export transparency verifier advances a signed contiguous T0 prefix" {
            val pin =
                """{"origin":"https://relay.example.com","chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","public_key":"T9CZzNR9eJPf6ewkQU7LDZtUICMqrTDZHEZb4zy-ZcQ","log_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc"}"""
            val entry =
                PallaSyncAttestationJniTest::class.java
                    .getResourceAsStream("/pallasync/attestations/transparency.json")!!
                    .bufferedReader()
                    .use { it.readText() }
                    .trim()
            val line = checkNotNull(PallaSyncCore.canonicalJson("""{"entry":$entry,"kind":"transparency_entry"}"""))
            val initial = PallaSyncCore.initialExportTransparencyHash("dddddddd-dddd-4ddd-8ddd-dddddddddddd", pin)
            Json
                .parseToJsonElement(entry)
                .jsonObject
                .getValue("previous_transparency_hash")
                .jsonPrimitive.content shouldBe initial
            PallaSyncCore.advanceExportTransparency(line, pin, 0, "") shouldBe "RQ_hKTkRnkvv4C1-0loGPI52MjYF9TstitAW_hbYkN4"
            shouldThrow<IllegalStateException> {
                PallaSyncCore.advanceExportTransparency(line, pin, 1, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            }.message shouldBe "C_TRANSPARENCY_INVALID"
        }
        "export accepted transparency entry is bound to its exact receipt" {
            val receipt = vector("receipt")
            val entry = vector("transparency")
            val pin =
                """{"origin":"https://relay.example.com","chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","public_key":"T9CZzNR9eJPf6ewkQU7LDZtUICMqrTDZHEZb4zy-ZcQ","log_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc"}"""
            PallaSyncCore.validateExportReceiptTransparency(receipt, entry, pin) shouldBe true
            shouldThrow<IllegalStateException> {
                PallaSyncCore.validateExportReceiptTransparency(
                    receipt.replace(
                        "\"object_hash\":\"SbcfMuMxz3xcVxxMP15gIw0QgQkkl_d4oYRiWn10Z7Y\"",
                        "\"object_hash\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"",
                    ),
                    entry,
                    pin,
                )
            }
        }
        "snapshot readback verifier is exposed through JNI" {
            shouldThrow<IllegalStateException> {
                PallaSyncSnapshotCore.validateSnapshotPartResponse(
                    "{}".toByteArray(),
                    "11111111-1111-4111-8111-111111111111",
                    """{"origin":"https://relay.example.com","chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","public_key":"T9CZzNR9eJPf6ewkQU7LDZtUICMqrTDZHEZb4zy-ZcQ","log_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc"}""",
                )
            }.message shouldBe "C_SCHEMA_VIOLATION"
        }
        "snapshot catalogue page is strictly checked through JNI" {
            val pin =
                """{"origin":"https://relay.example.com","chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","public_key":"T9CZzNR9eJPf6ewkQU7LDZtUICMqrTDZHEZb4zy-ZcQ","log_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc"}"""
            val body =
                """{"items":[{"compaction_id":"33333333-3333-4333-8333-333333333333","chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","generation":0,"device_id":"11111111-1111-4111-8111-111111111111","log_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc","covered_through_seq":0,"covered_log_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","epoch_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","snapshot_parts":[{"record_id":"22222222-2222-4222-8222-222222222222","record_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}],"state_sha256":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","signature":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}],"next_cursor":null,"has_more":false,"log_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc","generation":0,"head_seq":8}"""
            Json.parseToJsonElement(PallaSyncSnapshotCore.validateCompactionPage(body.toByteArray(), pin, 0, 8, 200)) shouldBe
                Json.parseToJsonElement(body)
            shouldThrow<IllegalStateException> {
                PallaSyncSnapshotCore.validateCompactionPage(body.replace("\"head_seq\":8", "\"head_seq\":9").toByteArray(), pin, 0, 8, 200)
            }.message shouldBe "C_SCHEMA_VIOLATION"
        }
    })
