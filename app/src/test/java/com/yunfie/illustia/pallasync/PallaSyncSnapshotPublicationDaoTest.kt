package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.SnapshotPublicationEntity
import com.yunfie.illustia.pallasync.data.SnapshotPublicationPartEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PallaSyncSnapshotPublicationDaoTest {
    @Test
    fun `publication journal preserves exact parts and receipts across retry boundaries`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val dao = db.snapshotPublicationDao()
                val publication =
                    SnapshotPublicationEntity(
                        chainId = CHAIN,
                        compactionId = COMPACTION,
                        generation = 2,
                        epoch = 4,
                        coveredThroughSeq = 91,
                        logId = LOG,
                        headerJson = "{\"kind\":\"header\"}",
                        partCount = 1,
                    )
                dao.begin(publication)
                dao.begin(publication)
                dao.publication(CHAIN, COMPACTION) shouldBe publication

                val part =
                    SnapshotPublicationPartEntity(
                        chainId = CHAIN,
                        compactionId = COMPACTION,
                        partIndex = 0,
                        recordId = RECORD,
                        recordHash = HASH,
                        signedJson = "{\"immutable\":true}",
                    )
                dao.retainPrepared(part)
                dao.retainPrepared(part)
                runCatching {
                    dao.retainPrepared(part.copy(recordHash = OTHER_HASH))
                }.exceptionOrNull()?.message shouldBe "C_RECORD_ID_CONFLICT"

                runCatching {
                    dao.finalizeCompaction(CHAIN, COMPACTION, OTHER_HASH, "{\"compaction\":true}")
                }.exceptionOrNull()?.message shouldBe "C_ACCEPTANCE_UNPROVEN"

                dao.retainReceipt(part, "{\"signed_receipt\":1}")
                dao.retainReceipt(part, "{\"signed_receipt\":1}")
                runCatching {
                    dao.retainReceipt(part, "{\"signed_receipt\":2}")
                }.exceptionOrNull()?.message shouldBe "C_SNAPSHOT_INVALID"

                val commitment = HASH
                val compaction = "{\"exact\":\"signed compaction bytes\"}"
                dao.finalizeCompaction(CHAIN, COMPACTION, commitment, compaction)
                dao.finalizeCompaction(CHAIN, COMPACTION, commitment, compaction)
                dao.publication(CHAIN, COMPACTION)?.status shouldBe "compaction_ready"
                dao.parts(CHAIN, COMPACTION).single().uploadReceiptJson shouldBe "{\"signed_receipt\":1}"
                dao.markPublished(CHAIN, COMPACTION, compaction) shouldBe 1
                dao.markPublished(CHAIN, COMPACTION, compaction) shouldBe 0
                dao.publication(CHAIN, COMPACTION)?.status shouldBe "published"
            } finally {
                db.close()
            }
        }

    private companion object {
        const val CHAIN = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
        const val COMPACTION = "33333333-3333-4333-8333-333333333333"
        const val RECORD = "11111111-1111-4111-8111-111111111111"
        const val LOG = "44444444-4444-4444-8444-444444444444"
        const val HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val OTHER_HASH = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
    }
}
