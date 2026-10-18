package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.ReseedJournalEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PallaSyncReseedJournalDaoTest {
    @Test
    fun `reseed journal preserves selected target and only advances verified checkpoints`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val dao = db.reseedJournalDao()
                val initial =
                    ReseedJournalEntity(
                        chainId = CHAIN,
                        sourceLogId = SOURCE_LOG,
                        sourceGeneration = 4,
                        targetOrigin = "https://relay.example",
                        targetPinJson = "{\"origin\":\"https://relay.example\"}",
                        updatedAtMs = 10,
                    )
                dao.saveMonotone(initial)
                val staged = initial.copy(stagedLogId = STAGED_LOG, compactionId = COMPACTION, phase = 1, updatedAtMs = 11)
                dao.saveMonotone(staged)
                dao.get(CHAIN) shouldBe staged

                val changedTargetError =
                    runCatching {
                        dao.saveMonotone(
                            staged.copy(targetOrigin = "https://attacker.example"),
                        )
                    }.exceptionOrNull()
                changedTargetError?.message shouldBe "C_ROLLBACK_DETECTED"
                val phaseRollbackError =
                    runCatching {
                        dao.saveMonotone(staged.copy(phase = 0))
                    }.exceptionOrNull()
                phaseRollbackError?.message shouldBe "C_ROLLBACK_DETECTED"

                val proving =
                    staged.copy(
                        transparencyHeadJson = "{\"head\":true}",
                        transparencyAnchorJson = "{\"index\":4,\"transparency_id\":\"proof\"}",
                        verifiedReceiptIndicesJson = "[2,4]",
                        commitResultJson = "{\"head_seq\":7}",
                        phase = 5,
                        updatedAtMs = 12,
                    )
                dao.saveMonotone(proving)
                val laterProven =
                    proving.copy(
                        transparencyAnchorJson = "{\"index\":5,\"transparency_id\":\"proof\"}",
                        verifiedReceiptIndicesJson = "[2,4,5]",
                        updatedAtMs = 13,
                    )
                dao.saveMonotone(
                    laterProven,
                )
                runCatching {
                    dao.saveMonotone(laterProven.copy(commitResultJson = "{\"head_seq\":8}"))
                }.exceptionOrNull()?.message shouldBe "C_ROLLBACK_DETECTED"
                val proofRollbackError =
                    runCatching {
                        dao.saveMonotone(
                            proving.copy(
                                transparencyAnchorJson = "{\"index\":3,\"transparency_id\":\"proof\"}",
                                verifiedReceiptIndicesJson = "[2]",
                            ),
                        )
                    }.exceptionOrNull()
                proofRollbackError?.message shouldBe "C_ROLLBACK_DETECTED"
            } finally {
                db.close()
            }
        }

    @Test
    fun `source conflict report advances only along the same verified cut`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val dao = db.reseedJournalDao()
                val initial =
                    ReseedJournalEntity(
                        CHAIN,
                        SOURCE_LOG,
                        4,
                        "https://relay.example",
                        targetPinJson = "{}",
                        phase = 0,
                        updatedAtMs = 1,
                    )
                dao.saveMonotone(initial)
                val committed = initial.copy(phase = 4, updatedAtMs = 2)
                dao.saveMonotone(committed)
                val cutBaseline = sourceConflictReport(head = 7, headHash = "cut", count = 0)
                dao.saveMonotone(committed.copy(sourceConflictReportJson = cutBaseline, updatedAtMs = 3))
                val first = sourceConflictReport(head = 9, headHash = "head-9", count = 2)
                dao.saveMonotone(committed.copy(sourceConflictReportJson = first, updatedAtMs = 4))
                val extended = sourceConflictReport(head = 10, headHash = "head-10", count = 3)
                dao.saveMonotone(committed.copy(sourceConflictReportJson = extended, updatedAtMs = 5))
                dao.get(CHAIN)?.sourceConflictReportJson shouldBe extended
                runCatching {
                    dao.saveMonotone(committed.copy(sourceConflictReportJson = first, updatedAtMs = 5))
                }.exceptionOrNull()?.message shouldBe "C_ROLLBACK_DETECTED"
                val equivocatedHead =
                    first.replace("\"observed_parameters_hash\":\"parameters-9\"", "\"observed_parameters_hash\":\"other-parameters\"")
                runCatching {
                    dao.saveMonotone(committed.copy(sourceConflictReportJson = equivocatedHead, updatedAtMs = 6))
                }.exceptionOrNull()?.message shouldBe "C_ROLLBACK_DETECTED"
                val advancedSequenceWithSameHash =
                    sourceConflictReport(head = 10, headHash = "head-9", count = 3)
                        .replace("parameters-10", "parameters-9")
                        .replace("epoch-10", "epoch-9")
                runCatching {
                    dao.saveMonotone(
                        committed.copy(sourceConflictReportJson = advancedSequenceWithSameHash, updatedAtMs = 7),
                    )
                }.exceptionOrNull()?.message shouldBe "C_ROLLBACK_DETECTED"
            } finally {
                db.close()
            }
        }

    private fun sourceConflictReport(
        head: Long,
        headHash: String,
        count: Long,
    ): String =
        """{"source_origin":"https://relay.example","source_log_id":"$SOURCE_LOG","source_generation":4,"source_authority_hash":"authority","source_cut_seq":7,"source_cut_hash":"cut","source_cut_parameters_hash":"params","source_cut_epoch_hash":"epoch","observed_head_seq":$head,"observed_head_hash":"$headHash","observed_parameters_hash":"parameters-$head","observed_epoch_hash":"epoch-$head","unimported_entry_count":$count,"unimported_first_seq":8,"unimported_last_seq":$head}"""

    private companion object {
        const val CHAIN = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
        const val SOURCE_LOG = "11111111-1111-4111-8111-111111111111"
        const val STAGED_LOG = "22222222-2222-4222-8222-222222222222"
        const val COMPACTION = "33333333-3333-4333-8333-333333333333"
    }
}
