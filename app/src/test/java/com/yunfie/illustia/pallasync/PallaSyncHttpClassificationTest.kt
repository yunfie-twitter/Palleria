package com.yunfie.illustia.pallasync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class PallaSyncHttpClassificationTest :
    StringSpec({
        val json = Json
        val logId = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
        val hash = "9rmeVaK9mN98hMLU6FDa4ZGL77Mgrd5fwceAipags-o"
        val emptyBody =
            """{"items":[],"next_cursor":null,"has_more":false,"scan_through_seq":5,
            "log_id":"$logId","generation":0,"head_seq":5,"head_hash":"$hash"}"""

        "410 is an unverified deletion hint and 404 never implies erasure" {
            classifyPallaSyncHttpStatus(410) shouldBe PallaSyncHttpResult.Gone
            classifyPallaSyncHttpStatus(404) shouldBe
                PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION", 404)
        }

        "receipt absence is pending evidence and remains retryable while other 404s remain errors" {
            val missingReceipt = PallaSyncProblemHint(404, "RECEIPT_NOT_FOUND", true)
            val chainId = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
            val receiptRequest =
                okhttp3.Request
                    .Builder()
                    .url("https://relay.example.com/pallasync/v3/chains/$chainId/receipts/5?log_id=$logId")
                    .get()
                    .build()
            classifyPallaSyncHttpStatus(404, missingReceipt, receiptRequest) shouldBe
                PallaSyncHttpResult.Retryable("RECEIPT_NOT_FOUND", statusCode = 404)

            classifyPallaSyncHttpStatus(404, PallaSyncProblemHint(404, "CHAIN_NOT_FOUND", true)) shouldBe
                PallaSyncHttpResult.ProtocolError("CHAIN_NOT_FOUND", 404)
            classifyPallaSyncHttpStatus(404, missingReceipt) shouldBe
                PallaSyncHttpResult.ProtocolError("RECEIPT_NOT_FOUND", 404)
            classifyPallaSyncHttpStatus(404, PallaSyncProblemHint(404, "RECEIPT_NOT_FOUND", false), receiptRequest) shouldBe
                PallaSyncHttpResult.ProtocolError("RECEIPT_NOT_FOUND", 404)
        }

        "rate limits and server failures are retryable while 2xx continues parsing" {
            (
                classifyPallaSyncHttpStatus(
                    429,
                    PallaSyncProblemHint(429, "RATE_LIMITED", true),
                ) is PallaSyncHttpResult.Retryable
            ) shouldBe true
            (classifyPallaSyncHttpStatus(500) is PallaSyncHttpResult.Retryable) shouldBe true
            classifyPallaSyncHttpStatus(200) shouldBe null
        }

        "client-action status without a validated problem never selects a semantic recovery code" {
            for (status in listOf(400, 401, 403, 404, 408, 409, 413, 429)) {
                classifyPallaSyncHttpStatus(status) shouldBe
                    PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION", status)
            }
        }

        "retryable semantic hints require lifecycle changes and 500 requires reconciliation" {
            for ((status, code) in listOf(
                403 to "DEVICE_PENDING_KEYS",
                409 to "EPOCH_STALE",
                507 to "QUOTA_EXCEEDED",
                507 to "STORAGE_UNAVAILABLE",
            )) {
                classifyPallaSyncHttpStatus(status, PallaSyncProblemHint(status, code, true)) shouldBe
                    PallaSyncHttpResult.ProtocolError(code, status)
            }
            (classifyPallaSyncHttpStatus(500) as PallaSyncHttpResult.Retryable).reconciliationRequired shouldBe true
        }

        "TOKEN_REQUIRED renews the authorized token while invalid token signatures stay terminal" {
            val required = PallaSyncProblemHint(401, "TOKEN_REQUIRED", true)
            val retry = classifyPallaSyncHttpStatus(401, required)
            (retry is PallaSyncHttpResult.Retryable) shouldBe true
            (retry as PallaSyncHttpResult.Retryable).statusCode shouldBe 401

            classifyPallaSyncHttpStatus(401, PallaSyncProblemHint(401, "TOKEN_SIGNATURE_INVALID", false)) shouldBe
                PallaSyncHttpResult.ProtocolError("TOKEN_SIGNATURE_INVALID", 401)
        }

        "an empty records page still carries control scan and complete pinned metadata" {
            val result = parseRecordsResponseBody(json, emptyBody, afterSeq = 0L)
            result shouldBe
                PallaSyncHttpResult.Success(
                    PallaSyncRecordsPage(
                        records = emptyList(),
                        nextSeq = 5L,
                        hasMore = false,
                        logId = logId,
                        generation = 0,
                        headSeq = 5,
                        headHash = hash,
                    ),
                )
        }

        "parseRecordsResponseBody parses object response with records and nextCursor" {
            val body = """{
                "items": [{
                    "relay_seq": 10,
                    "received_at_ms": 1726930000000,
                    "kind": "record",
                    "object": {
                        "version": "3.0",
                        "chain_id": "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc",
                        "generation": 0,
                        "record_id": "22222222-2222-4222-8222-222222222222",
                        "device_id": "11111111-1111-4111-8111-111111111111",
                        "epoch": 0,
                        "collection_tag": null,
                        "payload_nonce": "YWJj",
                        "encrypted_payload": "YWJj",
                        "signature": "c2ln"
                    }
                }],
                "next_cursor": "10",
                "has_more": true,
                "scan_through_seq": 10,
                "log_id": "$logId","generation":0,"head_seq":20,"head_hash":"$hash"
            }"""
            val result = parseRecordsResponseBody(json, body, afterSeq = 0L)
            (result is PallaSyncHttpResult.Success) shouldBe true
            val page = (result as PallaSyncHttpResult.Success).value
            page.records.size shouldBe 1
            page.records
                .first()
                .wireRecord
                ?.recordId shouldBe "22222222-2222-4222-8222-222222222222"
            page.nextSeq shouldBe 10L
            page.hasMore shouldBe true
        }

        "legacy arrays and missing mandatory nullable fields are rejected" {
            parseRecordsResponseBody(json, "[]", 0L) shouldBe PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
            parseRecordsResponseBody(json, emptyBody.replace("\"next_cursor\":null,", ""), 0L) shouldBe
                PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
            parseRecordsResponseBody(json, emptyBody.replace("\"has_more\":false", "\"has_more\":true"), 0L) shouldBe
                PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
        }

        "parseRecordsResponseBody returns ProtocolError on non-JSON response" {
            val body = "<html>502 Bad Gateway</html>"
            val result = parseRecordsResponseBody(json, body, afterSeq = 0L)
            result shouldBe PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
        }
    })
