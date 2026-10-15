package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.LogEntryEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.SignedEventEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PallaSyncRepairDaoTest {
    @Test
    fun `repair source scan uses the captured high water mark`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val dao = db.protocolDao()
                val chain = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
                val logId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
                dao.retainLogEntry(LogEntryEntity(chain, logId, 1, "hash-1", "{}", "{}", "authority_verified"))
                val capturedThrough = checkNotNull(dao.repairSourceHighWater(chain, logId))
                dao.retainLogEntry(LogEntryEntity(chain, logId, 2, "hash-2", "{}", "{}", "authority_verified"))

                capturedThrough shouldBe 1L
                dao.logPage(chain, logId, after = 0, through = capturedThrough, limit = 200).map { it.relaySeq } shouldBe listOf(1L)
            } finally {
                db.close()
            }
        }

    @Test
    fun `verified repair receipt rebinds retained event without replacing record bytes`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val dao = db.protocolDao()
                val chain = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
                val recordId = "22222222-2222-4222-8222-222222222222"
                val record =
                    SignedEventEntity(
                        chainId = chain,
                        recordId = recordId,
                        logId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                        relaySeq = 4,
                        objectHash = "record-hash",
                        signedJson = "{\"record\":1}",
                        receiptJson = "{\"old\":true}",
                        validationState = "materialized",
                    )
                dao.insertEvent(record)
                val repaired =
                    record.copy(
                        logId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                        relaySeq = 9,
                        receiptJson = "{\"new\":true}",
                    )

                dao.retainRepairedEvent(repaired)

                dao.event(chain, recordId) shouldBe repaired
            } finally {
                db.close()
            }
        }
}
