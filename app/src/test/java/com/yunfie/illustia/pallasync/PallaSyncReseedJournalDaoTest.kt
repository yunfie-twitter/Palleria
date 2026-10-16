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

    private companion object {
        const val CHAIN = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
        const val SOURCE_LOG = "11111111-1111-4111-8111-111111111111"
        const val STAGED_LOG = "22222222-2222-4222-8222-222222222222"
        const val COMPACTION = "33333333-3333-4333-8333-333333333333"
    }
}
