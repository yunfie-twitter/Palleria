package com.yunfie.illustia.pallasync.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PallaSyncDatabaseTest {
    @Test
    fun `cursor inbox and lamport commit atomically while a failed page rolls back`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val database =
                Room
                    .inMemoryDatabaseBuilder(context, PallaSyncDatabase::class.java)
                    .build()
            try {
                val dao = database.pallaSyncDao()
                dao.updateChainState(chainState("chain-a"))
                dao.commitInboxPage(
                    chainId = "chain-a",
                    lastRelaySeq = 12L,
                    maxLamport = 7L,
                    records =
                        listOf(
                            PallaSyncInboxEntity.applied(
                                chainId = "chain-a",
                                recordId = "record-12",
                                relaySeq = 12L,
                                rawRecordJson = "{}",
                            ),
                        ),
                )

                dao.getChainState("chain-a")?.lastRelaySeq shouldBe 12L
                dao.getChainState("chain-a")?.lamport shouldBe 7L
                dao.hasInboxRecord("chain-a", "record-12") shouldBe true

                shouldThrow<IllegalStateException> {
                    dao.commitInboxPage(
                        chainId = "missing-chain",
                        lastRelaySeq = 13L,
                        maxLamport = 8L,
                        records =
                            listOf(
                                PallaSyncInboxEntity.quarantined(
                                    chainId = "missing-chain",
                                    recordId = "rollback-record",
                                    relaySeq = 13L,
                                    rawRecordJson = "{bad}",
                                    reason = "test failure",
                                ),
                            ),
                    )
                }
                dao.hasInboxRecord("missing-chain", "rollback-record") shouldBe false
                dao.getChainState("chain-a")?.lastRelaySeq shouldBe 12L
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun `version 3 migration is additive and creates the durable cursor inbox schema`() {
        val statements = mutableListOf<String>()
        val recordingDatabase =
            Proxy.newProxyInstance(
                SupportSQLiteDatabase::class.java.classLoader,
                arrayOf(SupportSQLiteDatabase::class.java),
            ) { _, method, arguments ->
                if (method.name == "execSQL") {
                    statements += arguments?.firstOrNull() as String
                }
                null
            } as SupportSQLiteDatabase

        PallaSyncDatabase.MIGRATION_3_4.migrate(recordingDatabase)

        statements.size shouldBe 5
        statements[0].normalizedSql() shouldBe
            "ALTER TABLE pallasync_chain_state ADD COLUMN last_relay_seq INTEGER NOT NULL DEFAULT 0"
        statements[1].normalizedSql() shouldBe
            "ALTER TABLE pallasync_chain_state ADD COLUMN initial_pull_completed INTEGER NOT NULL DEFAULT 1"
        statements[2].normalizedSql().startsWith("CREATE TABLE IF NOT EXISTS pallasync_inbox") shouldBe true
        statements[3].normalizedSql() shouldBe
            "CREATE INDEX IF NOT EXISTS index_pallasync_inbox_chain_id_relay_seq ON pallasync_inbox(chain_id, relay_seq)"
        statements[4].normalizedSql() shouldBe
            "CREATE INDEX IF NOT EXISTS index_pallasync_inbox_chain_id_status ON pallasync_inbox(chain_id, status)"
        statements.none { sql ->
            val normalized = sql.normalizedSql().uppercase()
            "DROP " in normalized || "DELETE " in normalized
        } shouldBe true
    }

    @Test
    fun `version 4 to 5 migration creates indices on outbox table`() {
        val statements = mutableListOf<String>()
        val recordingDatabase =
            Proxy.newProxyInstance(
                SupportSQLiteDatabase::class.java.classLoader,
                arrayOf(SupportSQLiteDatabase::class.java),
            ) { _, method, arguments ->
                if (method.name == "execSQL") {
                    statements += arguments?.firstOrNull() as String
                }
                null
            } as SupportSQLiteDatabase

        PallaSyncDatabase.MIGRATION_4_5.migrate(recordingDatabase)

        statements.size shouldBe 2
        statements[0].normalizedSql() shouldBe
            "CREATE INDEX IF NOT EXISTS index_pallasync_outbox_status ON pallasync_outbox(status)"
        statements[1].normalizedSql() shouldBe
            "CREATE INDEX IF NOT EXISTS index_pallasync_outbox_chain_id_status ON pallasync_outbox(chain_id, status)"
    }
}

private fun chainState(chainId: String) =
    ChainStateEntity(
        chainId = chainId,
        lamport = 0L,
        keyEpoch = 1L,
        chainVectorJson = "{}",
    )

private fun String.normalizedSql(): String = trim().replace(Regex("\\s+"), " ")
