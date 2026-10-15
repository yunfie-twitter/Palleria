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
                PallaSyncLegacyMigrationReport.writeReport(
                    temp,
                    progress(decisions = 2),
                    fetchPage = { after ->
                        when (after) {
                            0L -> PallaSyncLocalIntents.LegacyDecisionPage(listOf(decision(1, "item-1")), 1)
                            1L -> PallaSyncLocalIntents.LegacyDecisionPage(listOf(decision(2, "item-2")), null)
                            else -> error("unexpected cursor")
                        }
                    },
                    fetchCandidatePage = { after ->
                        when (after) {
                            0L -> PallaSyncLocalIntents.LegacyCandidatePage(listOf(candidate(1)), 1)
                            1L -> PallaSyncLocalIntents.LegacyCandidatePage(listOf(candidate(2)), null)
                            else -> error("unexpected candidate cursor")
                        }
                    },
                    fetchSourcePage = { after ->
                        when (after) {
                            0L -> PallaSyncLocalIntents.LegacySourceEvidencePage(listOf(sourceEvidence(1)), 1)
                            1L -> PallaSyncLocalIntents.LegacySourceEvidencePage(listOf(sourceEvidence(2)), null)
                            else -> error("unexpected source cursor")
                        }
                    },
                )

                val report = Json.parseToJsonElement(temp.readText()).jsonObject
                report.getValue("report_type").jsonPrimitive.content shouldBe "pallasync-legacy-migration-report"
                report.getValue("source_high_water").jsonPrimitive.content shouldBe "2"
                report.getValue("source_protocol").jsonPrimitive.content shouldBe "2.1"
                report.getValue("accepted_count").jsonPrimitive.content shouldBe "1"
                report.getValue("excluded_count").jsonPrimitive.content shouldBe "0"
                report.getValue("migration_state").jsonPrimitive.content shouldBe "legacy_uploading"
                report.getValue("source_export_hash").jsonPrimitive.content shouldBe
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                report.getValue("decisions").jsonArray.map {
                    it.jsonObject
                        .getValue("sourceRecordId")
                        .jsonPrimitive.content
                } shouldBe listOf("item-1", "item-2")
                val sourceRecords = report.getValue("source_records").jsonArray
                val sourceSequences = sourceRecords.map { it.jsonObject["sourceSequence"]!!.jsonPrimitive.content }
                sourceSequences shouldBe listOf("1", "2")
                val candidates = report.getValue("candidates").jsonArray
                candidates.map { it.jsonObject["sourceSequence"]!!.jsonPrimitive.content } shouldBe listOf("1", "2")
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
                    PallaSyncLegacyMigrationReport.writeReport(
                        temp,
                        progress(decisions = 2),
                        fetchPage = {
                            PallaSyncLocalIntents.LegacyDecisionPage(listOf(decision(1, "item-1")), null)
                        },
                        fetchCandidatePage = {
                            error("candidate callback must not run after incomplete decision report")
                        },
                        fetchSourcePage = {
                            error("source callback must not run after incomplete decision report")
                        },
                    )
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
            accepted = 1,
            excluded = 0,
            state = "legacy_uploading",
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

    private fun sourceEvidence(sequence: Long) =
        PallaSyncLocalIntents.LegacySourceEvidence(
            sourceSequence = sequence,
            sourceRecordId = "source-$sequence",
            sourceObjectHash = "hash-$sequence",
            signedRecordJson = "{\"sequence\":$sequence}",
        )

    private fun candidate(sequence: Long) =
        PallaSyncLocalIntents.LegacyCandidate(
            migrationEntryIndex = sequence,
            sourceSequence = sequence,
            sourceRecordId = "01890f3e-7c00-7000-8000-00000000000$sequence",
            candidateIndex = 0,
            sourceObjectHash = "hash-$sequence",
            event =
                PallaSyncPendingEvent(
                    SEARCH_HISTORY_SCHEMA_V3,
                    "query-$sequence",
                    "upsert",
                    Json.parseToJsonElement("{\"query\":\"query-$sequence\"}"),
                ),
        )
}
