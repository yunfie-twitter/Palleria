package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.SignedEventEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PallaSyncRetentionProjectionTest {
    @Test
    fun `finite floor moves down after delete and restoring null exposes the preserved archive`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val chain = "test-chain"
                val device = "11111111-1111-4111-8111-111111111111"
                val log = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
                val dao = db.protocolDao()
                db.pallaSyncDao().updateChainState(ChainStateEntity(chain, 0, 0, "{}"))
                var previous: String? = null

                suspend fun apply(
                    number: Int,
                    operations: List<PallaSyncOperation>,
                ) {
                    val id = "019b0000-0000-4000-8000-${number.toString().padStart(12, '0')}"
                    val signed = SignedEventEntity(chain, id, log, number.toLong(), "hash-$number", "{}", null, "verified")
                    dao.applyVerifiedRecord(
                        signed,
                        0,
                        device,
                        PallaSyncInnerRecord(SEARCH_HISTORY_SCHEMA_V3, number.toLong(), previous, operations),
                    )
                    previous = signed.objectHash
                }

                fun query(
                    name: String,
                    lamport: Long,
                    kind: String = "upsert",
                ) = PallaSyncOperation(
                    name,
                    kind,
                    lamport,
                    1,
                    body = buildJsonObject { if (kind == "upsert") put("query", name) },
                )

                fun policy(
                    lamport: Long,
                    finite: Boolean,
                ) = PallaSyncOperation(
                    "*",
                    "upsert",
                    lamport,
                    1,
                    context = buildJsonObject { put("scope", "retention") },
                    body =
                        buildJsonObject {
                            put("scope", "all")
                            if (finite) put("limit", 2) else put("limit", JsonNull)
                            put("stop_on_safety_limit", false)
                        },
                )
                apply(1, listOf(query("old", 1), query("middle", 2), query("new", 3), policy(4, true)))
                val finite = dao.historyProjection(chain, SEARCH_HISTORY_SCHEMA_V3, "all")
                finite.liveCount shouldBe 3L
                finite.evictionEntity shouldBe "middle"
                dao.retainedHistoryIds(chain, SEARCH_HISTORY_SCHEMA_V3, "all", listOf("old")) shouldBe emptyList()
                dao.liveEntityIds(chain, SEARCH_HISTORY_SCHEMA_V3, listOf("old")) shouldBe listOf("old")
                apply(2, listOf(query("new", 5, "delete")))
                dao.historyProjection(chain, SEARCH_HISTORY_SCHEMA_V3, "all").evictionEntity shouldBe "old"
                apply(3, listOf(query("middle", 6, "delete")))
                dao.historyProjection(chain, SEARCH_HISTORY_SCHEMA_V3, "all").evictionKey shouldBe null
                apply(4, listOf(query("middle", 7), query("new", 8), policy(9, false)))
                dao.historyProjection(chain, SEARCH_HISTORY_SCHEMA_V3, "all").userLimit shouldBe null
                dao.retainedHistoryIds(chain, SEARCH_HISTORY_SCHEMA_V3, "all", listOf("old")) shouldBe listOf("old")
                dao.entity(chain, SEARCH_HISTORY_SCHEMA_V3, "old")?.winner?.lamport shouldBe 1L
            } finally {
                db.close()
            }
        }
}
