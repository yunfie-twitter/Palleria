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

        "parseRecordsResponseBody succeeds on empty genesis response" {
            val emptyBody = """{"records":[],"next_cursor":null,"server_time_ms":1726930000000}"""
            val result = parseRecordsResponseBody(json, emptyBody, nextSeqHeader = null, hasMoreHeader = null, afterSeq = 0L)
            result shouldBe PallaSyncHttpResult.Success(PallaSyncRecordsPage(records = emptyList(), nextSeq = 0L, hasMore = false))
        }

        "parseRecordsResponseBody parses object response with records and nextCursor" {
            val body = """{
                "records": [{
                    "protocol_version": "2.1",
                    "chain_id": "test-chain",
                    "record_id": "018f0c2a-7b9d-7000-8000-000000000001",
                    "collection_name": "palleria.favorite_tag/2",
                    "action": "upsert",
                    "encrypted_payload": "YWJj",
                    "device_id": "018f0c2a-7b9d-7000-8000-000000000002",
                    "created_at_ms": 1726930000000,
                    "signature": "c2ln"
                }],
                "next_cursor": "10",
                "server_time_ms": 1726930000000
            }"""
            val result = parseRecordsResponseBody(json, body, nextSeqHeader = null, hasMoreHeader = null, afterSeq = 0L)
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
                "protocol_version": "2.1",
                "chain_id": "test-chain",
                "record_id": "018f0c2a-7b9d-7000-8000-000000000001",
                "collection_name": "palleria.favorite_tag/2",
                "action": "upsert",
                "encrypted_payload": "YWJj",
                "device_id": "018f0c2a-7b9d-7000-8000-000000000002",
                "created_at_ms": 1726930000000,
                "signature": "c2ln"
            }]"""
            val result = parseRecordsResponseBody(json, body, nextSeqHeader = 5L, hasMoreHeader = true, afterSeq = 0L)
            (result is PallaSyncHttpResult.Success) shouldBe true
            val page = (result as PallaSyncHttpResult.Success).value
            page.records.size shouldBe 1
            page.nextSeq shouldBe 5L
            page.hasMore shouldBe true
        }

        "parseRecordsResponseBody returns ProtocolError on non-JSON response" {
            val body = "<html>502 Bad Gateway</html>"
            val result = parseRecordsResponseBody(json, body, nextSeqHeader = null, hasMoreHeader = null, afterSeq = 0L)
            result shouldBe PallaSyncHttpResult.ProtocolError("Relay page was not valid JSON")
        }
    })
