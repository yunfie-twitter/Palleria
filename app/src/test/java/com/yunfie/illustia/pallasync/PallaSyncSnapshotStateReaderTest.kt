package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.AuthorSequenceEntity
import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.EntityRegisterEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.ProjectionStateEntity
import com.yunfie.illustia.pallasync.data.ScopeRegisterEntity
import com.yunfie.illustia.pallasync.data.WinnerColumns
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.nio.ByteBuffer
import java.util.UUID

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PallaSyncSnapshotStateReaderTest {
    @Test
    @Suppress("LongMethod")
    fun `streams complete authoritative state in canonical kind order across keyset pages`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val chain = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
                db.pallaSyncDao().updateChainState(
                    ChainStateEntity(
                        chainId = chain,
                        lamport = 9,
                        keyEpoch = 0,
                        chainVectorJson = "{}",
                        generation = 1,
                        logId = "log",
                        epochHash = "epoch-hash",
                        verifiedSeq = 10,
                        materializedSeq = 10,
                        verifiedHash = "log-hash",
                        indexRevision = 7,
                    ),
                )
                val protocol = db.protocolDao()
                val winner = PallaSyncConflictResolver.key(1, "upsert", DEVICE_A, RECORD_A, 0)
                for (id in listOf("b", "a", "c")) {
                    protocol.putEntity(EntityRegisterEntity(chain, "collection", id, winner, "upsert", "all", "{\"v\":1}", "{}", null))
                }
                protocol.putEntity(
                    EntityRegisterEntity(chain, "collection", "deleted", winner.copy(opRank = 1), "delete", "all", "{}", "{}", null),
                )
                protocol.putRegister(ScopeRegisterEntity(chain, "collection", "all", "clear", winner.copy(opRank = 1), "{}"))
                protocol.putRegister(
                    ScopeRegisterEntity(
                        chain,
                        "collection",
                        "all",
                        "retention",
                        winner,
                        """{"scope":"all","limit":null,"stop_on_safety_limit":true}""",
                    ),
                )
                protocol.insertAuthor(AuthorSequenceEntity(chain, 1, DEVICE_A, 1, RECORD_A, "hash-1", null, false))
                protocol.insertAuthor(AuthorSequenceEntity(chain, 1, DEVICE_A, 3, RECORD_B, "hash-3", "hash-1", true))
                protocol.putProjection(
                    ProjectionStateEntity(
                        chain,
                        "collection",
                        "all",
                        2,
                        10,
                        null,
                        PallaSyncConflictResolver.encoded(winner),
                        "b",
                        7,
                    ),
                )

                val entries = mutableListOf<kotlinx.serialization.json.JsonObject>()
                PallaSyncSnapshotStateReader(db, pageSize = 2).stream(chain, entries::add)

                entries.map { it["kind"]!!.jsonPrimitive.content } shouldBe
                    listOf("header", "entity", "entity", "entity", "entity", "clear", "retention", "author", "author_gap", "projection")
                entries.filter { it["kind"]!!.jsonPrimitive.content == "entity" }.map { it["entity_id"]!!.jsonPrimitive.content } shouldBe
                    listOf("a", "b", "c", "deleted")
                entries.single { it["kind"]!!.jsonPrimitive.content == "author" }["device_seq"]!!.jsonPrimitive.long shouldBe 3L
                val gap = entries.single { it["kind"]!!.jsonPrimitive.content == "author_gap" }
                gap["first"]!!.jsonPrimitive.long shouldBe 2L
                gap["last"]!!.jsonPrimitive.long shouldBe 2L

                val stableState = checkNotNull(db.pallaSyncDao().getChainState(chain))
                val changedDuringStream =
                    runCatching {
                        PallaSyncSnapshotStateReader(db, pageSize = 2).stream(chain) { entry ->
                            if (entry["kind"]?.jsonPrimitive?.content == "header") {
                                db.pallaSyncDao().updateChainState(stableState.copy(indexRevision = stableState.indexRevision + 1))
                            }
                        }
                    }.exceptionOrNull()
                changedDuringStream?.message shouldBe "C_SNAPSHOT_INVALID"
            } finally {
                db.close()
            }
        }

    private companion object {
        const val DEVICE_A = "11111111-1111-4111-8111-111111111111"
        const val RECORD_A = "22222222-2222-4222-8222-222222222222"
        const val RECORD_B = "33333333-3333-4333-8333-333333333333"
    }
}
