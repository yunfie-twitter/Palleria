package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.AuthorSequenceEntity
import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.EntityRegisterEntity
import com.yunfie.illustia.pallasync.data.OutboxEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.ProjectionStateEntity
import com.yunfie.illustia.pallasync.data.ScopeRegisterEntity
import com.yunfie.illustia.pallasync.data.WinnerColumns
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
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
    fun `reseed snapshot header uses new incarnation zero boundary and preserves source lamport`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val chain = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
                val logId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
                db.pallaSyncDao().updateChainState(
                    ChainStateEntity(
                        chainId = chain,
                        lamport = 19,
                        keyEpoch = 4,
                        chainVectorJson = "{}",
                        generation = 1,
                        logId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                        epochHash = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        verifiedSeq = 7,
                        materializedSeq = 7,
                        verifiedHash = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
                        indexRevision = 12,
                    ),
                )
                val pendingOutboxId =
                    db.pallaSyncDao().insertOutboxEvent(
                        OutboxEntity(chainId = chain, deviceSeq = 1, status = "queued", eventJson = "{}"),
                    )
                val chainBytes =
                    java.util.Base64
                        .getUrlDecoder()
                        .decode(chain)
                val id = UUID.fromString(logId)
                val hash =
                    java.security.MessageDigest.getInstance("SHA-256").digest(
                        "PALLASYNC-LOG-v3\u0000".toByteArray(Charsets.US_ASCII) + chainBytes +
                            ByteBuffer
                                .allocate(16)
                                .putLong(id.mostSignificantBits)
                                .putLong(id.leastSignificantBits)
                                .array(),
                    )
                val coveredHash =
                    java.util.Base64
                        .getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(hash)
                val header =
                    buildJsonObject {
                        put("kind", "header")
                        put("chain_id", chain)
                        put("generation", 2)
                        put("log_id", logId)
                        put("covered_through_seq", 0)
                        put("covered_log_hash", coveredHash)
                        put("epoch_hash", "Qzw3mcqTS3voWwnO-9MYyi7rPGjqJyvi_H1EOpmiLA8")
                        put("lamport_max", 19)
                    }

                val entries = mutableListOf<kotlinx.serialization.json.JsonObject>()
                PallaSyncSnapshotStateReader(db).stream(chain, reseedHeader = header, preservePendingOutbox = true, emit = entries::add)
                entries.first() shouldBe header
                entries.first()["covered_through_seq"]!!.jsonPrimitive.long shouldBe 0L
                entries.first()["generation"]!!.jsonPrimitive.long shouldBe 2L
                db.pallaSyncDao().pendingOutboxCount(chain) shouldBe 1L
                db.pallaSyncDao().outboxEvent(pendingOutboxId)?.status shouldBe "queued"

                val badHeader =
                    buildJsonObject {
                        header.forEach { (key, value) -> put(key, value) }
                        put("extra", true)
                    }
                runCatching {
                    PallaSyncSnapshotStateReader(db).stream(chain, reseedHeader = badHeader, preservePendingOutbox = true) {}
                }.exceptionOrNull()?.message shouldBe "C_SNAPSHOT_INVALID"
            } finally {
                db.close()
            }
        }

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

    @Test
    fun `new root migration reauthors every entity and register in complete source conflict order`() =
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
                        lamport = 4,
                        keyEpoch = 0,
                        chainVectorJson = "{}",
                        logId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                        epochHash = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        verifiedSeq = 4,
                        materializedSeq = 4,
                        verifiedHash = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
                        indexRevision = 3,
                    ),
                )
                val protocol = db.protocolDao()
                val entityOne = PallaSyncConflictResolver.key(1, "upsert", DEVICE_A, RECORD_A, 0)
                val clear = PallaSyncConflictResolver.key(2, "clear", DEVICE_A, RECORD_B, 0)
                val tombstone = PallaSyncConflictResolver.key(3, "delete", DEVICE_A, "44444444-4444-4444-8444-444444444444", 0)
                val retention = PallaSyncConflictResolver.key(4, "upsert", DEVICE_A, "55555555-5555-4555-8555-555555555555", 0)
                protocol.putEntity(
                    EntityRegisterEntity(
                        chain,
                        "collection",
                        "before-clear",
                        entityOne,
                        "upsert",
                        "all",
                        "{\"v\":1}",
                        "{}",
                        null,
                    ),
                )
                protocol.putRegister(ScopeRegisterEntity(chain, "collection", "all", "clear", clear, "{}"))
                protocol.putEntity(
                    EntityRegisterEntity(
                        chain,
                        "collection",
                        "after-clear",
                        tombstone,
                        "delete",
                        "all",
                        "{}",
                        "{}",
                        null,
                    ),
                )
                protocol.putRegister(
                    ScopeRegisterEntity(
                        chain,
                        "collection",
                        "all",
                        "retention",
                        retention,
                        """{"scope":"all","limit":20,"stop_on_safety_limit":true}""",
                    ),
                )

                val events = mutableListOf<PallaSyncPendingEvent>()
                PallaSyncSnapshotStateReader(db, pageSize = 2)
                    .streamForNewRootMigration(chain, events::add)

                events.map { it.entityId } shouldBe listOf("before-clear", "*", "after-clear", "*")
                events.map { it.operation } shouldBe listOf("upsert", "clear", "delete", "upsert")
                events[1].context["scope"]!!.jsonPrimitive.content shouldBe "all"
                events[3].context["scope"]!!.jsonPrimitive.content shouldBe "retention"
                events[3]
                    .body
                    .jsonObject["limit"]!!
                    .jsonPrimitive.long shouldBe 20L

                val pagedReader = PallaSyncSnapshotStateReader(db, pageSize = 2)
                val firstPage = pagedReader.newRootMigrationPage(chain)
                firstPage.rows shouldBe events.take(2)
                firstPage.complete shouldBe false
                val secondPage = pagedReader.newRootMigrationPage(chain, firstPage.anchor, firstPage.next)
                secondPage.rows shouldBe events.drop(2)
                secondPage.complete shouldBe false
                val finalPage = pagedReader.newRootMigrationPage(chain, secondPage.anchor, secondPage.next)
                finalPage.rows shouldBe emptyList()
                finalPage.complete shouldBe true
                runCatching {
                    pagedReader.newRootMigrationPage(
                        chain,
                        firstPage.anchor,
                        NewRootMigrationCursor(lamport = 0, rank = 1),
                    )
                }.exceptionOrNull()?.message shouldBe "C_SNAPSHOT_INVALID"
                db.pallaSyncDao().updateChainState(
                    checkNotNull(db.pallaSyncDao().getChainState(chain)).copy(indexRevision = 4),
                )
                runCatching { pagedReader.newRootMigrationPage(chain, firstPage.anchor, firstPage.next) }
                    .exceptionOrNull()
                    ?.message shouldBe "C_ROLLBACK_DETECTED"

                db.snapshotStateDao().insertSnapshotAuthorGap(
                    com.yunfie.illustia.pallasync.data.SnapshotAuthorGapEntity(
                        chain,
                        0,
                        DEVICE_A,
                        1,
                        1,
                        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                    ),
                )
                runCatching { PallaSyncSnapshotStateReader(db).streamForNewRootMigration(chain) {} }
                    .exceptionOrNull()
                    ?.message shouldBe "C_SNAPSHOT_INVALID"
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
