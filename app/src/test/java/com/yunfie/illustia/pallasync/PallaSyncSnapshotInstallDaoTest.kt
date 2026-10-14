package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.ChainStateEntity
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
    fun `verified snapshot atomically replaces logical prefix and keeps author head evidence`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                db.pallaSyncDao().updateChainState(
                    ChainStateEntity(
                        CHAIN,
                        lamport = 0,
                        keyEpoch = 0,
                        chainVectorJson = "{}",
                        generation = 0,
                        logId = LOG,
                        epochHash = EPOCH_HASH,
                    ),
                )
                val dao = db.snapshotInstallDao()
                dao.begin(
                    SnapshotInstallEntity(
                        CHAIN,
                        COMPACTION,
                        "{}",
                        partCount = 1,
                        stateSha256 = "commitment",
                        coveredThroughSeq = 1,
                        coveredLogHash = COVERED_HASH,
                        generation = 0,
                        status = "staging",
                    ),
                )
                val rows =
                    listOf(
                        snapshotEntry(0, header()),
                        snapshotEntry(1, entity()),
                        snapshotEntry(2, author()),
                        snapshotEntry(3, authorGap()),
                    )
                dao.retainVerifiedPart(
                    SnapshotInstallPartEntity(CHAIN, COMPACTION, 0, RECORD, "part-hash", rows.size),
                    rows,
                )
                dao.markVerified(CHAIN, COMPACTION)
                dao.installVerifiedState(CHAIN, COMPACTION, expectedIndexRevision = 0)

                db.protocolDao().entity(CHAIN, COLLECTION, ENTITY)?.operation shouldBe "upsert"
                db.protocolDao().snapshotAuthorHead(CHAIN, 0, DEVICE, 4)?.recordHash shouldBe AUTHOR_HASH
                db.protocolDao().unresolvedAuthorGapCount(CHAIN) shouldBe 1L
                db.pallaSyncDao().getChainState(CHAIN)?.let {
                    listOf(it.verifiedSeq, it.materializedSeq, it.indexRevision, it.verifiedHash) shouldBe
                        listOf(1L, 1L, 1L, COVERED_HASH)
                }
                dao.install(CHAIN, COMPACTION)?.status shouldBe "installed"
            } finally {
                db.close()
            }
        }

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
                val conflictingRetry = part(index = 0, entry = "{\"kind\":\"tampered\"}")
                dao.retainVerifiedPart(first.first, first.second)
                dao.retainVerifiedPart(first.first, first.second)
                shouldThrow<IllegalStateException> { dao.retainVerifiedPart(conflictingRetry.first, conflictingRetry.second) }
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
                PallaSyncSnapshotSemanticComparator.compare(dao, CHAIN, COMPACTION) { emit ->
                    emit("{\"kind\":\"header\"}")
                    emit("{\"kind\":\"entity\"}")
                }
                shouldThrow<IllegalStateException> {
                    PallaSyncSnapshotSemanticComparator.compare(dao, CHAIN, COMPACTION) { emit ->
                        emit("{\"kind\":\"header\"}")
                    }
                }
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

    private fun snapshotEntry(
        index: Int,
        json: String,
    ) = SnapshotInstallEntryEntity(CHAIN, COMPACTION, 0, index, json)

    private fun header() =
        """{"chain_id":"$CHAIN","covered_log_hash":"$COVERED_HASH","covered_through_seq":1,"epoch_hash":"$EPOCH_HASH","generation":0,"kind":"header","lamport_max":1,"log_id":"$LOG"}"""

    private fun entity() =
        """{"body":{"value":"saved"},"collection":"$COLLECTION","context":{},"entity_id":"$ENTITY","key":[1,0,"$DEVICE","$RECORD",0],"kind":"entity","operation":"upsert"}"""

    private fun author() = """{"device_id":"$DEVICE","device_seq":4,"generation":0,"kind":"author","record_hash":"$AUTHOR_HASH"}"""

    private fun authorGap() = """{"device_id":"$DEVICE","first":1,"generation":0,"kind":"author_gap","last":2}"""

    private companion object {
        const val CHAIN = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
        const val COMPACTION = "33333333-3333-4333-8333-333333333333"
        const val LOG = "44444444-4444-4444-8444-444444444444"
        const val DEVICE = "55555555-5555-4555-8555-555555555555"
        const val RECORD = "66666666-6666-4666-8666-666666666666"
        const val COLLECTION = "palleria.search_history/3"
        const val ENTITY = "all"
        const val EPOCH_HASH = "epoch-hash"
        const val COVERED_HASH = "covered-hash"
        const val AUTHOR_HASH = "author-hash"
    }
}
