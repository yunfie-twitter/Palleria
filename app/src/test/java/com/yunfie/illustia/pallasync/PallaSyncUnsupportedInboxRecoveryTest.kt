package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.PallaSyncInboxEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PallaSyncUnsupportedInboxRecoveryTest {
    @Test
    fun `unresolved first page does not starve later compatible rows or include new arrivals`() =
        runBlocking {
            val database = database()
            try {
                val dao = database.pallaSyncDao()
                val originals = (0 until 58).map { record(it, if (it < 55) 1 else 2) }
                val excluded =
                    listOf(
                        record(60, 1).copy(chainId = "other"),
                        record(61, 1).copy(status = "quarantined"),
                        record(62, 1).copy(quarantineReason = "C_SIGNATURE_INVALID"),
                    )
                dao.insertInboxRecords(originals + excluded)
                val visited = mutableListOf<String>()
                val applied =
                    PallaSyncUnsupportedInboxRecovery(dao).retry("chain") { row ->
                        if (visited.isEmpty()) dao.insertInboxRecords(listOf(record(63, 3)))
                        visited += row.recordId
                        val supported = row.relaySeq == 2L
                        if (supported) dao.updateInboxDisposition("chain", row.recordId, row.rawRecordJson, "applied", "")
                        supported
                    }
                applied shouldBe 3L
                visited shouldBe originals.map { it.recordId }
                (originals.take(55) + excluded + record(63, 3)).forEach { row ->
                    dao.getInboxRecord(row.chainId, row.recordId) shouldBe row
                }
                originals.takeLast(3).forEach { row ->
                    dao.getInboxRecord("chain", row.recordId)?.rawRecordJson shouldBe row.rawRecordJson
                    dao.getInboxRecord("chain", row.recordId)?.status shouldBe "applied"
                }
            } finally {
                database.close()
            }
        }

    @Test
    fun `cancellation keeps originals and a later explicit pass starts from retained rows`() =
        runBlocking<Unit> {
            val database = database()
            try {
                val dao = database.pallaSyncDao()
                val originals = (0 until 3).map { record(it, it.toLong()) }
                dao.insertInboxRecords(originals)
                var attempts = 0
                val job =
                    launch {
                        PallaSyncUnsupportedInboxRecovery(dao).retry("chain") {
                            attempts++
                            throw CancellationException("test cancellation")
                        }
                    }
                job.join()
                job.isCancelled shouldBe true
                attempts shouldBe 1
                originals.forEach { dao.getInboxRecord("chain", it.recordId) shouldBe it }
                PallaSyncUnsupportedInboxRecovery(dao).retry("chain") {
                    attempts++
                    false
                } shouldBe 0L
                attempts shouldBe 4
            } finally {
                database.close()
            }
        }

    @Test
    fun `schema retry cannot authorize other dispositions`() {
        val row = record(0, 1)
        PallaSyncInboxRetry.SCHEMA_SUPPORT.accepts(row) shouldBe true
        PallaSyncInboxRetry.EVIDENCE.accepts(row) shouldBe false
        PallaSyncInboxRetry.LOCAL_STORAGE.accepts(row) shouldBe false
        listOf("quarantined", "chain_suspended", "pending_dependency", "operationally_blocked", "applied").forEach {
            PallaSyncInboxRetry.SCHEMA_SUPPORT.accepts(row.copy(status = it)) shouldBe false
        }
        PallaSyncInboxRetry.SCHEMA_SUPPORT.accepts(row.copy(quarantineReason = "C_SIGNATURE_INVALID")) shouldBe false
    }

    private fun database(): PallaSyncDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PallaSyncDatabase::class.java).build()

    private fun record(
        number: Int,
        sequence: Long,
    ) = PallaSyncInboxEntity(
        chainId = "chain",
        recordId = number.toString().padStart(3, '0'),
        relaySeq = sequence,
        status = "unsupported_collection",
        rawRecordJson = "{\"ciphertext\":\"immutable-$number\"}",
        quarantineReason = "C_UNSUPPORTED_SCHEMA",
        receivedAtMs = 1,
    )
}
