package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.AuthorityHistoryEntity
import com.yunfie.illustia.pallasync.data.OutboxEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PallaSyncReseedCutoverDaoTest {
    @Test
    fun `reseed authority promotion archives old checkpoint and is idempotent`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val dao = db.protocolDao()
                dao.commitAuthority(AuthorityHistoryEntity(CHAIN, "checkpoint", OLD_HASH, 4, OLD_JSON, "current_checkpoint"))
                dao.retainAuthority(AuthorityHistoryEntity(CHAIN, "checkpoint", NEW_HASH, 5, NEW_JSON, "reseed_candidate"))

                dao.installReseedAuthority(CHAIN, NEW_HASH, NEW_JSON)
                dao.currentAuthority(CHAIN) shouldBe NEW_JSON
                dao.archivedAuthority(CHAIN, 4)?.signedJson shouldBe OLD_JSON
                dao.reseedCandidateAuthority(CHAIN, 5) shouldBe null

                dao.installReseedAuthority(CHAIN, NEW_HASH, NEW_JSON)
                dao.currentAuthority(CHAIN) shouldBe NEW_JSON
            } finally {
                db.close()
            }
        }

    @Test
    fun `reseed rebased outbox rows remain retained but leave pending pages`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val dao = db.pallaSyncDao()
                val id =
                    dao.insertOutboxEvent(
                        OutboxEntity(
                            chainId = CHAIN,
                            deviceSeq = 9,
                            status = "uploading",
                            eventJson = "signed-source-record",
                            recordId = RECORD,
                            objectHash = HASH,
                        ),
                    )
                dao.pendingOutboxCount(CHAIN) shouldBe 1L
                dao.markReseedRebased(id, CHAIN, RECORD, HASH) shouldBe 1
                dao.pendingOutboxCount(CHAIN) shouldBe 0L
                dao.pendingOutboxPage(CHAIN, 0, 10) shouldBe emptyList()
                dao.outboxEvent(id)?.eventJson shouldBe "signed-source-record"
                dao.outboxEvent(id)?.status shouldBe "reseed_rebased"
            } finally {
                db.close()
            }
        }

    @Test
    fun `accepted outbox evidence stays accepted when reseed replay retires pending rows`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val dao = db.pallaSyncDao()
                val id =
                    dao.insertOutboxEvent(
                        OutboxEntity(
                            chainId = CHAIN,
                            deviceSeq = 10,
                            status = "accepted",
                            eventJson = "accepted-source-record",
                            recordId = RECORD,
                            objectHash = HASH,
                            receiptJson = "signed-source-receipt",
                        ),
                    )
                dao.markReseedRebased(id, CHAIN, RECORD, HASH) shouldBe 0
                dao.outboxEvent(id)?.eventJson shouldBe "accepted-source-record"
                dao.outboxEvent(id)?.receiptJson shouldBe "signed-source-receipt"
                dao.outboxEvent(id)?.status shouldBe "accepted"
                dao.pendingOutboxCount(CHAIN) shouldBe 0L
            } finally {
                db.close()
            }
        }

    private companion object {
        const val CHAIN = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
        const val OLD_HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val NEW_HASH = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
        const val OLD_JSON = "{\"authority\":\"old\"}"
        const val NEW_JSON = "{\"authority\":\"new\"}"
        const val RECORD = "11111111-1111-4111-8111-111111111111"
        const val HASH = "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"
    }
}
