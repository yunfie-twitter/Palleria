package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class PallaSyncRecordRepairRequestTest :
    StringSpec({
        "repair request preserves one-to-one source records and verified log entries" {
            val record =
                PallaSyncRecordRepairRequestTest::class.java
                    .getResourceAsStream("/pallasync/attestations/record.json")!!
                    .bufferedReader()
                    .use { Json.parseToJsonElement(it.readText()).jsonObject }
            val entry =
                buildJsonObject {
                    put("relay_seq", 7)
                    put("received_at_ms", 1791684000000L)
                    put("kind", "record")
                    put("object", record)
                }
            val source = PallaSyncRecordRepairRelay.SourceRecord(entry, record)
            val body =
                Json
                    .parseToJsonElement(
                        PallaSyncRecordRepairRelay.canonicalRequestBody(
                            "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
                            listOf(source),
                        ),
                    ).jsonObject

            body.getValue("records") shouldBe JsonArray(listOf(record))
            body.getValue("receipts") shouldBe JsonArray(listOf(entry))
            body.getValue("source_log_id").toString() shouldBe "\"cccccccc-cccc-4ccc-8ccc-cccccccccccc\""
        }

        "repair request rejects malformed source log identifiers and empty batches" {
            shouldThrow<IllegalStateException> {
                PallaSyncRecordRepairRelay.canonicalRequestBody("not-a-uuid", emptyList())
            }.message shouldBe "C_SCHEMA_VIOLATION"
            shouldThrow<IllegalStateException> {
                PallaSyncRecordRepairRelay.canonicalRequestBody(
                    "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
                    emptyList(),
                )
            }.message shouldBe "C_SCHEMA_VIOLATION"
        }
    })
