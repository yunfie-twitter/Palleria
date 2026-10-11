package com.yunfie.illustia.pallasync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class PallaSyncHttpClassificationTest :
    StringSpec({
        val json = Json { ignoreUnknownKeys = true }

        "only 410 is destructive gone" {
            classifyPallaSyncHttpStatus(410) shouldBe PallaSyncHttpResult.Gone
            classifyPallaSyncHttpStatus(404) shouldBe
                PallaSyncHttpResult.ProtocolError(
                    "PallaSync server returned HTTP 404",
                    404,
                )
        }

        "rate limits and server failures are retryable while 2xx continues parsing" {
            (classifyPallaSyncHttpStatus(429) is PallaSyncHttpResult.Retryable) shouldBe true
            (classifyPallaSyncHttpStatus(500) is PallaSyncHttpResult.Retryable) shouldBe true
            classifyPallaSyncHttpStatus(200) shouldBe null
        }

        "parseDeviceIdsResponseBody accepts wrapped and legacy device arrays" {
            parseDeviceIdsResponseBody(json, """{"devices":[{"device_id":"wrapped"}]}""") shouldBe
                PallaSyncHttpResult.Success(listOf("""{"device_id":"wrapped"}"""))
            parseDeviceIdsResponseBody(json, """[{"device_id":"legacy"}]""") shouldBe
                PallaSyncHttpResult.Success(listOf("""{"device_id":"legacy"}"""))
        }

        "parseDeviceIdsResponseBody distinguishes a missing devices field" {
            parseDeviceIdsResponseBody(json, """{"other":[]}""") shouldBe
                PallaSyncHttpResult.ProtocolError("Device response devices field was missing")
        }

        "parseDeviceIdsResponseBody rejects malformed JSON and non-array device values" {
            parseDeviceIdsResponseBody(json, "not-json") shouldBe
                PallaSyncHttpResult.ProtocolError("Device response was not valid JSON")
            parseDeviceIdsResponseBody(json, """{"devices":{}}""") shouldBe
                PallaSyncHttpResult.ProtocolError("Device response was not valid JSON")
        }

        "parseRecordsResponseBody succeeds on empty genesis response" {
            val emptyBody = """{"items":[],"has_more":false,"scan_through_seq":0}"""
            val result = parseRecordsResponseBody(json, emptyBody, afterSeq = 0L)
            result shouldBe PallaSyncHttpResult.Success(PallaSyncRecordsPage(records = emptyList(), nextSeq = 0L, hasMore = false))
        }

        "parseRecordsResponseBody parses object response with records and nextCursor" {
            val body = """{
                "items": [{
                    "relay_seq": 10,
                    "received_at_ms": 1726930000000,
                    "kind": "record",
                    "object": {
                        "version": "3.0",
                        "chain_id": "test-chain",
                        "generation": 0,
                        "record_id": "018f0c2a-7b9d-7000-8000-000000000001",
                        "device_id": "018f0c2a-7b9d-7000-8000-000000000002",
                        "epoch": 0,
                        "payload_nonce": "YWJj",
                        "encrypted_payload": "YWJj",
                        "signature": "c2ln"
                    }
                }],
                "next_cursor": "10",
                "has_more": true,
                "scan_through_seq": 10
            }"""
            val result = parseRecordsResponseBody(json, body, afterSeq = 0L)
            (result is PallaSyncHttpResult.Success) shouldBe true
            val page = (result as PallaSyncHttpResult.Success).value
            page.records.size shouldBe 1
            page.records
                .first()
                .wireRecord
                ?.recordId shouldBe "018f0c2a-7b9d-7000-8000-000000000001"
            page.nextSeq shouldBe 10L
            page.hasMore shouldBe true
        }

        "parseRecordsResponseBody parses legacy array response" {
            val body = """[{
                "version": "3.0",
                "chain_id": "test-chain",
                "generation": 0,
                "record_id": "018f0c2a-7b9d-7000-8000-000000000001",
                "device_id": "018f0c2a-7b9d-7000-8000-000000000002",
                "epoch": 0,
                "payload_nonce": "YWJj",
                "encrypted_payload": "YWJj",
                "signature": "c2ln"
            }]"""
            val result = parseRecordsResponseBody(json, body, afterSeq = 0L)
            (result is PallaSyncHttpResult.Success) shouldBe true
            val page = (result as PallaSyncHttpResult.Success).value
            page.records.size shouldBe 1
            page.nextSeq shouldBe 1L
            page.hasMore shouldBe false
        }

        "parseRecordsResponseBody returns ProtocolError on non-JSON response" {
            val body = "<html>502 Bad Gateway</html>"
            val result = parseRecordsResponseBody(json, body, afterSeq = 0L)
            result shouldBe PallaSyncHttpResult.ProtocolError("Relay page was not valid JSON")
        }
    })
