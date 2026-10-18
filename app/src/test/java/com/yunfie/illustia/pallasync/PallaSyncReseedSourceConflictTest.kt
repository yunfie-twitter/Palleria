package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.ChainStateEntity
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test

class PallaSyncReseedSourceConflictTest {
    @Test
    fun `verified raced suffix becomes a local report with exact sequence range`() {
        val report = PallaSyncReseedSourceConflict.report(cut(), checkpoint(headSeq = 14, headHash = "head-14"), ORIGIN, 3)

        report?.getValue("source_cut_seq")?.jsonPrimitive?.long shouldBe 11
        report?.getValue("observed_head_seq")?.jsonPrimitive?.long shouldBe 14
        report?.getValue("unimported_entry_count")?.jsonPrimitive?.long shouldBe 3
        report?.getValue("unimported_first_seq")?.jsonPrimitive?.long shouldBe 12
        report?.getValue("unimported_last_seq")?.jsonPrimitive?.long shouldBe 14
        report?.getValue("source_origin")?.jsonPrimitive?.content shouldBe ORIGIN
        PallaSyncReseedSourceConflict.summary(checkNotNull(report).toString()) shouldBe
            PallaSyncReseedSourceConflict.Summary(ORIGIN, 12, 14, 3)
    }

    @Test
    fun `matching signed source cut persists zero baseline without surfacing a conflict`() {
        val report = PallaSyncReseedSourceConflict.report(cut(), checkpoint(headSeq = 11, headHash = "cut-hash"), ORIGIN, 0)

        report.getValue("unimported_entry_count").jsonPrimitive.long shouldBe 0
        PallaSyncReseedSourceConflict.summary(report.toString()) shouldBe null
    }

    @Test
    fun `report rejects incomplete suffix and rollback`() {
        runCatching {
            PallaSyncReseedSourceConflict.report(cut(), checkpoint(headSeq = 14, headHash = "head-14"), ORIGIN, 2)
        }.exceptionOrNull()?.message shouldBe "C_TRANSPARENCY_INVALID"
        runCatching {
            PallaSyncReseedSourceConflict.report(cut(), checkpoint(headSeq = 10, headHash = "older"), ORIGIN, 0)
        }.exceptionOrNull()?.message shouldBe "C_ROLLBACK_DETECTED"
    }

    private fun cut() =
        ChainStateEntity(
            chainId = CHAIN,
            lamport = 40,
            keyEpoch = 3,
            chainVectorJson = "{}",
            generation = 2,
            logId = LOG,
            parametersHash = "parameters",
            epochHash = "epoch",
            materializedSeq = 11,
            verifiedHash = "cut-hash",
        )

    private fun checkpoint(
        headSeq: Long,
        headHash: String,
    ) = PallaSyncGenesisCheckpoint(
        logId = LOG,
        headSeq = headSeq,
        headHash = headHash,
        parametersHash = "parameters",
        epochHash = "epoch",
        transparencyId = "transparency",
        transparencyIndex = 20,
        transparencyHash = "transparency-hash",
        authority = JsonObject(emptyMap()),
        generation = 2,
    )

    private companion object {
        const val CHAIN = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
        const val LOG = "11111111-1111-4111-8111-111111111111"
        const val ORIGIN = "https://relay.example"
    }
}
