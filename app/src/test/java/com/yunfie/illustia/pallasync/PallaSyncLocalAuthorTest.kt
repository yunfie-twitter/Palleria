package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.MigrationJournalEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Base64

/** Actual JNI + Room; only the OS storage adapter is replaced to inject durable-boundary failures. */
@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PallaSyncLocalAuthorTest {
    private val chain = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
    private val device = "11111111-1111-4111-8111-111111111111"

    @Test
    fun `initial activation resumes each boundary and retains the active chain`() =
        runBlocking<Unit> {
            for (boundary in PallaSyncLocalAuthor.Boundary.entries) {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val db = Room.inMemoryDatabaseBuilder(context, PallaSyncDatabase::class.java).build()
                try {
                    val store = PallaSyncLocalStore(context, db)
                    val previous = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                    store.dao.updateChainState(ChainStateEntity(previous, 0, 0, "{}", writeState = "active"))
                    val staged =
                        ChainStateEntity(
                            chain,
                            0,
                            0,
                            "{}",
                            logId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                            writeState = "activation_preparing",
                        )
                    store.dao.updateChainState(staged)
                    val keys =
                        PallaSyncKeySnapshot(
                            chainId = chain,
                            deviceId = device,
                            encryptionKeyBase64Url =
                                encode(
                                    ByteArray(32) {
                                        2
                                    },
                                ),
                            signingKeyBase64Url = encode(ByteArray(32) { 1 }),
                            publicKeyBase64Url = "fixture public key",
                        )
                    val storage = MemoryStorage()
                    val journal = PallaSyncAuthorJournal(storage)
                    val interrupted =
                        PallaSyncLocalAuthor(store, journal, epochTag = { _, _, _ -> null }) {
                            if (it ==
                                boundary
                            ) {
                                error("Injected initial boundary")
                            }
                        }
                    runCatching { interrupted.initialize(chain, keys, listOf(event("first"), event("second"))) }.isFailure shouldBe true
                    store.dao.getActiveChainState()?.chainId shouldBe previous
                    val resumed = PallaSyncLocalAuthor(store, PallaSyncAuthorJournal(storage), epochTag = { _, _, _ -> null })
                    // A changed UI cache on retry cannot change the durably selected initial construction.
                    resumed.initialize(chain, keys, listOf(event("replacement")))
                    store.dao.queuedBatch(chain, 200).map { it.deviceSeq } shouldBe listOf(1L, 2L)
                    store.protocolDao().entity(chain, SEARCH_HISTORY_SCHEMA_V3, "replacement") shouldBe null
                    store.protocolDao().entity(chain, SEARCH_HISTORY_SCHEMA_V3, "second")?.operation shouldBe "upsert"
                    journal.initialProgress(checkNotNull(store.dao.getChainState(chain)), device) shouldBe 2
                    store.dao.getActiveChainState()?.chainId shouldBe previous
                    resumed.initialize(chain, keys, emptyList())
                    store.dao.queuedBatch(chain, 200).size shouldBe 2
                } finally {
                    db.close()
                }
            }
        }

    @Test
    fun `author boundaries resume exact bytes before constructing the successor`() =
        runBlocking<Unit> {
            for (boundary in PallaSyncLocalAuthor.Boundary.entries) {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val db = Room.inMemoryDatabaseBuilder(context, PallaSyncDatabase::class.java).build()
                try {
                    val store = PallaSyncLocalStore(context, db)
                    val storage = MemoryStorage()
                    val journal = PallaSyncAuthorJournal(storage)
                    val keys =
                        PallaSyncKeySnapshot(
                            chainId = chain,
                            deviceId = device,
                            encryptionKeyBase64Url = encode(ByteArray(32) { 2 }),
                            signingKeyBase64Url = encode(ByteArray(32) { 1 }),
                            publicKeyBase64Url = "fixture public key",
                        )
                    val original =
                        ChainStateEntity(chain, 0, 0, "{}", logId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", writeState = "active")
                    store.dao.updateChainState(original)
                    val interrupted =
                        PallaSyncLocalAuthor(store, journal, epochTag = { _, _, _ -> null }) {
                            if (it ==
                                boundary
                            ) {
                                error("Injected author boundary")
                            }
                        }
                    runCatching { interrupted.append(chain, keys, listOf(event("first"))) }.isFailure shouldBe true
                    val retained = journal.pending(checkNotNull(store.dao.getChainState(chain)), device)
                    val before =
                        store.dao
                            .queuedBatch(chain, 200)
                            .singleOrNull()
                            ?.eventJson ?: retained?.signed
                    // Reopen both the protected journal and operation; no in-memory native handle survives.
                    val resumed = PallaSyncLocalAuthor(store, PallaSyncAuthorJournal(storage), epochTag = { _, _, _ -> null })
                    resumed.append(chain, keys, emptyList())
                    val first = store.dao.queuedBatch(chain, 200).single()
                    if (before != null) first.eventJson shouldBe before
                    store.dao.getChainState(chain)?.authorSeq shouldBe 1
                    store.protocolDao().entity(chain, SEARCH_HISTORY_SCHEMA_V3, "first")?.operation shouldBe "upsert"
                    store.protocolDao().event(chain, checkNotNull(first.recordId))?.relaySeq shouldBe null
                    journal.pending(checkNotNull(store.dao.getChainState(chain)), device) shouldBe null
                    resumed.append(chain, keys, listOf(event("second")))
                    val queued = store.dao.queuedBatch(chain, 200)
                    queued.map { it.deviceSeq } shouldBe listOf(1L, 2L)
                    val second = decrypt(queued[1].eventJson, keys)
                    second.prevRecordHash shouldBe queued[0].objectHash
                    store.dao.getChainState(chain)?.authorSeq shouldBe 2
                    // Restoring a stale Room author tail must not reset the independent protected tail.
                    store.dao.updateChainState(original)
                    runCatching { resumed.append(chain, keys, listOf(event("third"))) }.exceptionOrNull()?.message shouldBe
                        "C_ROLLBACK_DETECTED"
                    store.dao.queuedBatch(chain, 200).size shouldBe 2
                } finally {
                    db.close()
                }
            }
        }

    @Test
    fun `fresh replacement writer resets old cursor once and preserves its durable tail on resume`() =
        runBlocking<Unit> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = Room.inMemoryDatabaseBuilder(context, PallaSyncDatabase::class.java).build()
            try {
                val store = PallaSyncLocalStore(context, db)
                val storage = MemoryStorage()
                val original =
                    ChainStateEntity(
                        chain,
                        lamport = 99,
                        keyEpoch = 1,
                        chainVectorJson = "{}",
                        logId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                        authorSeq = 4,
                        authorTailHash = "old-device-tail",
                        writeState = "active",
                    )
                store.dao.updateChainState(original)
                val replacement = testKeys(1).copy(deviceId = "22222222-2222-4222-8222-222222222222")
                val author = PallaSyncLocalAuthor(store, PallaSyncAuthorJournal(storage), epochTag = { _, _, _ -> null })

                author.prepareFreshDeviceWriter(chain, replacement)
                store.dao.getChainState(chain)?.let {
                    it.authorSeq shouldBe 0
                    it.authorTailHash shouldBe null
                    it.lamport shouldBe 99
                }
                author.append(chain, replacement, listOf(event("replacement first")))
                store.dao.getChainState(chain)?.authorSeq shouldBe 1

                val resumed = PallaSyncLocalAuthor(store, PallaSyncAuthorJournal(storage), epochTag = { _, _, _ -> null })
                resumed.prepareFreshDeviceWriter(chain, replacement)
                store.dao.getChainState(chain)?.authorSeq shouldBe 1
                resumed.append(chain, replacement, listOf(event("replacement second")))
                store.dao.getChainState(chain)?.authorSeq shouldBe 2
                store.dao.getChainState(chain)?.lamport shouldBe 101
            } finally {
                db.close()
            }
        }

    @Test
    fun `replay mapping and signed destination commit together and survive every author boundary`() =
        runBlocking<Unit> {
            for (boundary in PallaSyncLocalAuthor.Boundary.entries) {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val db = Room.inMemoryDatabaseBuilder(context, PallaSyncDatabase::class.java).build()
                try {
                    val store = PallaSyncLocalStore(context, db)
                    val state =
                        ChainStateEntity(
                            chain,
                            999,
                            1,
                            "{}",
                            logId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                            writeState = "epoch_rebase_required",
                        )
                    store.dao.updateChainState(state)
                    val keys = testKeys(1)
                    val work = PallaSyncReplayWork(event("retained edit"))
                    val reference = PallaSyncAuthorJournal.ReplayReference("local-boundary-test", 0, "source hash")
                    store.protocolDao().putMigrationChunk(
                        MigrationJournalEntity(
                            reference.migrationId,
                            0,
                            reference.sourceHash,
                            "source",
                            chain,
                            "pending",
                            Json.encodeToString(work),
                        ),
                    )
                    val storage = MemoryStorage()
                    val interrupted =
                        PallaSyncLocalAuthor(store, PallaSyncAuthorJournal(storage), epochTag = { _, _, _ -> null }) {
                            if (it == boundary) error("Injected replay boundary")
                        }
                    runCatching { interrupted.append(chain, keys, listOf(work.event), replay = reference) }.isFailure shouldBe true
                    val resumed = PallaSyncLocalAuthor(store, PallaSyncAuthorJournal(storage), epochTag = { _, _, _ -> null })
                    resumed.append(chain, keys, listOf(work.event), replay = reference)
                    resumed.append(chain, keys, listOf(work.event), replay = reference)
                    val row = checkNotNull(store.protocolDao().migrationChunk(reference.migrationId, 0))
                    row.state shouldBe "constructed"
                    val destination = Json.decodeFromString<PallaSyncReplayWork>(row.reportJson).destinationRecordId
                    val record = store.dao.queuedBatch(chain, 200).single()
                    record.recordId shouldBe destination
                    decrypt(record.eventJson, keys).operations.single().lamport shouldBe 1000
                    store.dao.getChainState(chain)?.authorSeq shouldBe 1
                    store.protocolDao().event(chain, checkNotNull(destination))?.relaySeq shouldBe null
                } finally {
                    db.close()
                }
            }
        }

    @Test
    fun `paused epoch delivery completes only the previously journaled old epoch record`() =
        runBlocking<Unit> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = Room.inMemoryDatabaseBuilder(context, PallaSyncDatabase::class.java).build()
            try {
                val store = PallaSyncLocalStore(context, db)
                val storage = MemoryStorage()
                val keys =
                    PallaSyncKeySnapshot(
                        chain,
                        device,
                        encryptionKeyBase64Url =
                            encode(
                                ByteArray(32) {
                                    2
                                },
                            ),
                        signingKeyBase64Url = encode(ByteArray(32) { 1 }),
                        publicKeyBase64Url = "fixture",
                    )
                store.dao.updateChainState(
                    ChainStateEntity(chain, 0, 0, "{}", logId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", writeState = "active"),
                )
                val interrupted =
                    PallaSyncLocalAuthor(store, PallaSyncAuthorJournal(storage), epochTag = { _, _, _ -> null }) {
                        if (it == PallaSyncLocalAuthor.Boundary.RECORD_JOURNALED) error("Injected old epoch construction")
                    }
                runCatching { interrupted.append(chain, keys, listOf(event("old epoch"))) }.isFailure shouldBe true
                val paused = checkNotNull(store.dao.getChainState(chain)).copy(writeState = "epoch_pending_keys")
                store.dao.updateChainState(paused)
                val before = checkNotNull(PallaSyncAuthorJournal(storage).pending(paused, device)).signed
                val resumed = PallaSyncLocalAuthor(store, PallaSyncAuthorJournal(storage), epochTag = { _, _, _ -> null })
                runCatching { resumed.finishPending(chain, keys.copy(epoch = 1)) }.exceptionOrNull()?.message shouldBe
                    "C_MISSING_DEPENDENCY"
                PallaSyncAuthorJournal(storage).pending(paused, device)?.signed shouldBe before
                resumed.finishPending(chain, keys)
                resumed.finishPending(chain, keys)
                store.dao
                    .queuedBatch(chain, 200)
                    .single()
                    .eventJson shouldBe before
                store.dao.getChainState(chain)?.writeState shouldBe "epoch_pending_keys"
                runCatching { resumed.append(chain, keys, listOf(event("unexpected new operation"))) }.isFailure shouldBe true
                store.dao.queuedBatch(chain, 200).size shouldBe 1
            } finally {
                db.close()
            }
        }

    private fun testKeys(epoch: Long) =
        PallaSyncKeySnapshot(
            chain,
            device,
            encryptionKeyBase64Url = encode(ByteArray(32) { 2 }),
            signingKeyBase64Url = encode(ByteArray(32) { 1 }),
            publicKeyBase64Url = "fixture",
            epoch = epoch,
        )

    private fun decrypt(
        record: String,
        keys: PallaSyncKeySnapshot,
    ): PallaSyncInnerRecord =
        PallaSyncDeviceSession.begin(keys).use {
            Json.decodeFromString(PallaSyncCore.decryptOwnPreparedRecord(it.handle, record))
        }

    private class MemoryStorage : PallaSyncAuthorJournal.Storage {
        private var saved = emptyMap<String, String>()

        override fun read(key: String): String? = saved[key]

        override fun commit(
            values: Map<String, String>,
            removed: Set<String>,
        ): Boolean {
            saved = (saved + values) - removed
            return true
        }
    }

    private fun event(query: String) =
        PallaSyncPendingEvent(
            SEARCH_HISTORY_SCHEMA_V3,
            query,
            "upsert",
            buildJsonObject { put("query", query) },
        )

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
