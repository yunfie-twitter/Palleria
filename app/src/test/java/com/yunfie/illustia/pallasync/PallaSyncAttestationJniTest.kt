package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
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
        "snapshot readback verifier is exposed through JNI" {
            shouldThrow<IllegalStateException> {
                PallaSyncSnapshotCore.validateSnapshotPartResponse(
                    "{}".toByteArray(),
                    "11111111-1111-4111-8111-111111111111",
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                    """{"origin":"https://relay.example.com","chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","public_key":"T9CZzNR9eJPf6ewkQU7LDZtUICMqrTDZHEZb4zy-ZcQ","log_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc"}""",
                )
            }.message shouldBe "C_SCHEMA_VIOLATION"
        }
    })
