package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.SnapshotInstallEntity
import com.yunfie.illustia.pallasync.data.SnapshotInstallEntryEntity
import com.yunfie.illustia.pallasync.data.SnapshotInstallPartEntity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PallaSyncSnapshotInstallDaoTest {
    @Test
    fun `staged plaintext is hidden until all exact parts are retained and verified`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val dao = db.snapshotInstallDao()
                dao.begin(
                    SnapshotInstallEntity(
                        CHAIN,
                        COMPACTION,
                        "{\"compaction_id\":\"$COMPACTION\"}",
                        partCount = 2,
                        stateSha256 = "commitment",
                        coveredThroughSeq = 7,
                        coveredLogHash = "prefix",
                        generation = 0,
                        status = "staging",
                    ),
                )
                val first = part(index = 0, entry = "{\"kind\":\"header\"}")
                val second = part(index = 1, entry = "{\"kind\":\"entity\"}")
                dao.retainVerifiedPart(first.first, first.second)
                dao.retainVerifiedPart(first.first, first.second)
                dao.verifiedPartEntries(CHAIN, COMPACTION, 0) shouldBe emptyList()
                dao.entryPage(CHAIN, COMPACTION, -1, -1, 10) shouldBe emptyList()
                shouldThrow<IllegalStateException> { dao.markVerified(CHAIN, COMPACTION) }
                dao.entryPage(CHAIN, COMPACTION, -1, -1, 10) shouldBe emptyList()
                dao.retainVerifiedPart(second.first, second.second)
                dao.markVerified(CHAIN, COMPACTION)

                dao.verifiedPartEntries(CHAIN, COMPACTION, 0).map { it.entryJson } shouldBe
                    listOf("{\"kind\":\"header\"}")
                val page = dao.entryPage(CHAIN, COMPACTION, -1, -1, 1)
                page.single().entryJson shouldBe "{\"kind\":\"header\"}"
                dao.entryPage(CHAIN, COMPACTION, 0, 0, 10).map { it.entryJson } shouldBe
                    listOf("{\"kind\":\"entity\"}")
            } finally {
                db.close()
            }
        }

    private fun part(
        index: Int,
        entry: String,
    ): Pair<SnapshotInstallPartEntity, List<SnapshotInstallEntryEntity>> {
        val recordId = if (index == 0) "11111111-1111-4111-8111-111111111111" else "22222222-2222-4222-8222-222222222222"
        val part = SnapshotInstallPartEntity(CHAIN, COMPACTION, index, recordId, "hash-$index", 1)
        val rows = listOf(SnapshotInstallEntryEntity(CHAIN, COMPACTION, index, 0, entry))
        return part to rows
    }

    private companion object {
        const val CHAIN = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
        const val COMPACTION = "33333333-3333-4333-8333-333333333333"
    }
}
