package com.yunfie.illustia.pallasync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.util.UUID

class PallaSyncV3HandshakeTest :
    StringSpec({
        val json = Json { ignoreUnknownKeys = true }
        val origin = "https://api.yunfi.f5.si"
        // Anonymous production health observed on 2026-10-12 JST; all values are public.
        val health =
            """
            {
              "protocol":"pallasync/3","relay_origin":"$origin","server_time_ms":1791735292803,
              "durability_tier":"single-durable",
              "limits":{"max_request_bytes":8388608,"max_records_per_upload":64,"page_limit":1000,"max_devices":128},
              "shutdown_at_ms":null,"migration_origin":null,
              "relay_public_key":"0H9ecAawH0mwezZB--Yf8Ju02-6U3SYpu6mjo6OfpTs",
              "relay_key_id":"kaIMLJ9IFYPfw8DsH-79tONuUTPOs4ruaZoK1Wtnlzc"
            }
            """.trimIndent()

        "v3 health is accepted without invented status or protocol_version fields" {
            val parsed = parseHealthResponseBody(health, origin)
            (parsed is PallaSyncHttpResult.Success) shouldBe true
            (parsed as PallaSyncHttpResult.Success).value.limits.maxRecordsPerUpload shouldBe 64
        }

        "missing mandatory health fields and wrong origin protocol or fingerprint fail closed" {
            listOf(
                health.replace("\"protocol\":\"pallasync/3\",", ""),
                health.replace("pallasync/3", "pallasync/2"),
                health.replace(origin, "https://other.example"),
                health.replace("kaIMLJ9I", "aaIMLJ9I"),
                health.replace("8388608", "8388609"),
                """{"status":"ok","protocol_version":"3.0"}""",
            ).forEach { (parseHealthResponseBody(it, origin) is PallaSyncHttpResult.ProtocolError) shouldBe true }
        }

        "new record IDs use UUIDv4 required by the relay" {
            repeat(20) { UUID.fromString(newPallaSyncRecordId()).version() shouldBe 4 }
        }

        "device registry unwraps signed objects and keeps status outside their signature" {
            val body =
                """
                {"items":[{"device_record":{"device_id":"device","signature":"signed"},
                "status":"active","enrolled_seq":2,"revoked_seq":null}],
                "next_cursor":"next","has_more":true,"log_id":"log","generation":0,"head_seq":5}
                """.trimIndent()
            val page = (parseDevicesResponseBody(json, body) as PallaSyncHttpResult.Success).value
            page.items
                .single()
                .deviceRecord
                .toString() shouldBe """{"device_id":"device","signature":"signed"}"""
            page.items.single().status shouldBe "active"
            page.nextCursor shouldBe "next"
            (parseDevicesResponseBody(json, body.replace("\"next\"", "null")) is PallaSyncHttpResult.ProtocolError) shouldBe true
            (parseDevicesResponseBody(json, """{"devices":[]}""") is PallaSyncHttpResult.ProtocolError) shouldBe true
        }

        "HTTP 200 with per-item rejection preserves unaccepted records" {
            val submitted =
                mapOf(
                    "one" to json.parseToJsonElement("""{"record_id":"one","signature":"a"}""").jsonObject,
                    "two" to json.parseToJsonElement("""{"record_id":"two","signature":"b"}""").jsonObject,
                )
            val accepted = submitted.getValue("one")
            val body =
                """
                {"accepted":[{"record_id":"one","duplicate":false,
                "receipt":{"relay_seq":6,"received_at_ms":10,"kind":"record","object":$accepted}}],
                "rejected":[{"record_id":"two","status":409,"code":"EPOCH_STALE","retryable":true}],
                "upload_receipts":[],"generation":0,"log_id":"log","head_seq":6,"head_hash":"hash"}
                """.trimIndent()
            val result = (parseRecordUploadResponseBody(json, body, submitted) as PallaSyncHttpResult.Success).value
            result.accepted.map { it.recordId } shouldBe listOf("one")
            result.rejected.single().recordId shouldBe "two"
            result.rejected.single().retryable shouldBe true
            (
                parseRecordUploadResponseBody(
                    json,
                    body.replace("\"two\"", "\"unknown\""),
                    submitted,
                ) is PallaSyncHttpResult.ProtocolError
            ) shouldBe
                true
            (
                parseRecordUploadResponseBody(
                    json,
                    """{"accepted":[],"rejected":[]}""",
                    submitted,
                ) is PallaSyncHttpResult.ProtocolError
            ) shouldBe
                true
            val corruptReceipt = body.replace("\"signature\":\"a\"", "\"signature\":\"changed\"")
            (parseRecordUploadResponseBody(json, corruptReceipt, submitted) is PallaSyncHttpResult.ProtocolError) shouldBe true
        }

        "batches adapt to both record count and UTF-8 request byte limits" {
            val limits = PallaSyncLimits(8_388_608, 64, 1000, 128)
            recordUploadBatchSize(List(70) { "{}" }, limits) shouldBe 64
            recordUploadBatchSize(List(5) { "{}" }, limits.copy(maxRecordsPerUpload = 2)) shouldBe 2
            recordUploadBatchSize(listOf("\"あ\"", "{}"), limits.copy(maxRequestBytes = 19)) shouldBe 1
            recordUploadBatchSize(listOf("{}"), limits.copy(maxRequestBytes = 15)) shouldBe 0
        }
    })
