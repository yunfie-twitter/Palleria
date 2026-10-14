package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.nio.file.Files

class PallaSyncLegacyMigrationReportTest {
    @Test
    fun `report streams every keyset page and records migration status`() {
        runBlocking {
            val temp = Files.createTempFile("pallasync-migration-report", ".json").toFile()
            try {
                PallaSyncLegacyMigrationReport.writeReport(temp, progress(decisions = 2)) { after ->
                    when (after) {
                        0L -> PallaSyncLocalIntents.LegacyDecisionPage(listOf(decision(1, "item-1")), 1)
                        1L -> PallaSyncLocalIntents.LegacyDecisionPage(listOf(decision(2, "item-2")), null)
                        else -> error("unexpected cursor")
                    }
                }

                val report = Json.parseToJsonElement(temp.readText()).jsonObject
                report.getValue("report_type").jsonPrimitive.content shouldBe "pallasync-legacy-migration-report"
                report.getValue("source_high_water").jsonPrimitive.content shouldBe "2"
                report.getValue("source_protocol").jsonPrimitive.content shouldBe "2.1"
                report.getValue("source_export_hash").jsonPrimitive.content shouldBe
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                report.getValue("decisions").jsonArray.map {
                    it.jsonObject
                        .getValue("sourceRecordId")
                        .jsonPrimitive.content
                } shouldBe listOf("item-1", "item-2")
            } finally {
                temp.delete()
            }
        }
    }

    @Test
    fun `report fails closed if durable decision count changed while paging`() {
        runBlocking {
            val temp = Files.createTempFile("pallasync-migration-report", ".json").toFile()
            try {
                shouldThrow<IllegalStateException> {
                    PallaSyncLegacyMigrationReport.writeReport(temp, progress(decisions = 2)) {
                        PallaSyncLocalIntents.LegacyDecisionPage(listOf(decision(1, "item-1")), null)
                    }
                }.message shouldBe "C_SCHEMA_VIOLATION"
            } finally {
                temp.delete()
            }
        }
    }

    private fun progress(decisions: Long) =
        PallaSyncLocalIntents.LegacyMigrationProgress(
            migrationId = "migration-id",
            sourceChain = "source-chain",
            sourceProtocol = "2.1",
            exportHash = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            targetChain = "target-chain",
            sourceHighWater = 2,
            stagedThrough = 2,
            sourceComplete = true,
            candidates = 2,
            decisions = decisions,
            pending = 0,
            constructed = 2,
            quarantined = 0,
            schemaCounts = mapOf("pallasync.search-history/3" to 2),
        )

    private fun decision(
        sequence: Long,
        id: String,
    ) = PallaSyncLocalIntents.LegacySourceDecision(
        sourceSequence = sequence,
        sourceRecordId = id,
        sourceObjectHash = "hash-$sequence",
        disposition = "converted",
    )
}
