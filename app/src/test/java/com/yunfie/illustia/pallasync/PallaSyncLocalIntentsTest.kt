package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Base64

/** Actual JNI signing/AEAD and Room transactions; restart discards all native session handles. */
@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PallaSyncLocalIntentsTest {
    private val chain = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
    private val device = "11111111-1111-4111-8111-111111111111"
    private val log = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"

    @Test
    fun `paused intent survives all author boundaries and is signed exactly once on resume`() =
        runBlocking<Unit> {
            for (boundary in PallaSyncLocalAuthor.Boundary.entries) {
                val db = database()
                try {
                    val store = PallaSyncLocalStore(ApplicationProvider.getApplicationContext(), db)
                    val storage = MemoryStorage()
                    store.dao.updateChainState(ChainStateEntity(chain, 20, 0, "{}", logId = log, writeState = "integrity_paused"))
                    val interrupted = author(store, storage) { if (it == boundary) error("Injected construction boundary") }
                    val queue = queue(store, interrupted)
                    queue.stage(chain, listOf(event("offline")))
                    queue.resume(chain, keys())
                    store.dao.queuedBatch(chain, 200) shouldBe emptyList()
                    store.dao.getChainState(chain)?.authorSeq shouldBe 0
                    store.protocolDao().pendingMigrationCount(PallaSyncLocalIntents.journalId(chain)) shouldBe 1
                    store.dao.updateChainState(checkNotNull(store.dao.getChainState(chain)).copy(writeState = "active"))
                    runCatching { queue.resume(chain, keys()) }.isFailure shouldBe true
                    val before =
                        store.dao
                            .queuedBatch(chain, 200)
                            .singleOrNull()
                            ?.eventJson
                    val resumed = queue(store, author(store, storage))
                    resumed.resume(chain, keys())
                    resumed.resume(chain, keys())
                    val record = store.dao.queuedBatch(chain, 200).single()
                    if (before != null) record.eventJson shouldBe before
                    store.protocolDao().pendingMigrationCount(PallaSyncLocalIntents.journalId(chain)) shouldBe 0
                    store.dao.getChainState(chain)?.authorSeq shouldBe 1
                    PallaSyncDeviceSession.begin(keys()).use {
                        val inner =
                            Json.decodeFromString<PallaSyncInnerRecord>(
                                PallaSyncCore.decryptOwnPreparedRecord(it.handle, record.eventJson),
                            )
                        inner.operations.single().lamport shouldBe 21
                        inner.operations.single().entityId shouldBe "offline"
                    }
                } finally {
                    db.close()
                }
            }
        }

    @Test
    fun `rotating bounded scan preserves old generation intents without starving later valid work`() =
        runBlocking<Unit> {
            val db = database()
            try {
                val store = PallaSyncLocalStore(ApplicationProvider.getApplicationContext(), db)
                store.dao.updateChainState(ChainStateEntity(chain, 0, 0, "{}", logId = log, writeState = "active"))
                val queue = queue(store, author(store, MemoryStorage()))
                queue.stage(chain, (1..201).map { event("old-$it") })
                store.dao.updateChainState(checkNotNull(store.dao.getChainState(chain)).copy(generation = 1, lamport = 900))
                queue.stage(chain, listOf(event("new-generation")))
                queue.resume(chain, keys())
                store.dao.queuedBatch(chain, 200) shouldBe emptyList()
                store.protocolDao().migrationChunk(PallaSyncLocalIntents.journalId(chain), 0)?.reportJson shouldBe "200"
                queue.resume(chain, keys())
                store.dao.queuedBatch(chain, 200).size shouldBe 1
                store.protocolDao().pendingMigrationCount(PallaSyncLocalIntents.journalId(chain)) shouldBe 201
                store
                    .protocolDao()
                    .entity(chain, SEARCH_HISTORY_SCHEMA_V3, "new-generation")
                    ?.winner
                    ?.lamport shouldBe 901
                store.protocolDao().entity(chain, SEARCH_HISTORY_SCHEMA_V3, "old-1") shouldBe null
                queue.resume(chain, keys())
                store.dao.queuedBatch(chain, 200).size shouldBe 1
            } finally {
                db.close()
            }
        }

    @Test
    fun `oversized unsent intent remains durable while a later valid edit proceeds`() =
        runBlocking<Unit> {
            val db = database()
            try {
                val store = PallaSyncLocalStore(ApplicationProvider.getApplicationContext(), db)
                store.dao.updateChainState(ChainStateEntity(chain, 0, 0, "{}", logId = log, writeState = "active"))
                val queue = queue(store, author(store, MemoryStorage()))
                val oversized = event("too-large").copy(body = buildJsonObject { put("query", "x".repeat(1_100_000)) })
                queue.stage(chain, listOf(oversized, event("valid")))
                queue.resume(chain, keys())
                store.dao.queuedBatch(chain, 200).size shouldBe 1
                val held = checkNotNull(store.protocolDao().migrationChunk(PallaSyncLocalIntents.journalId(chain), 1))
                held.state shouldBe "pending"
                val retained = Json.decodeFromString<PallaSyncReplayWork>(held.reportJson)
                (retained.sourceEvent ?: retained.event) shouldBe oversized
                store.dao.getChainState(chain)?.authorSeq shouldBe 1
                store.protocolDao().entity(chain, SEARCH_HISTORY_SCHEMA_V3, "valid")?.operation shouldBe "upsert"
            } finally {
                db.close()
            }
        }

    @Test
    fun `protected artifact rejection rolls back the entire unsent batch before persistence`() =
        runBlocking<Unit> {
            val db = database()
            try {
                val store = PallaSyncLocalStore(ApplicationProvider.getApplicationContext(), db)
                store.dao.updateChainState(ChainStateEntity(chain, 0, 0, "{}", logId = log, writeState = "active"))
                val queue = queue(store, author(store, MemoryStorage()))
                for (body in listOf(
                    buildJsonObject { put("nested", buildJsonObject { put("type", "PALLASYNC-RECOVERY-PACKAGE-v3") }) },
                    buildJsonObject {
                        put("mnemonic", "public fixture only")
                        put("passphrase", "")
                    },
                    buildJsonObject {
                        put("file", "PSR3:" + encode("{\"type\":\"PALLASYNC-RECOVERY-PACKAGE-v3\"}".toByteArray(Charsets.UTF_8)))
                    },
                )) {
                    val failure =
                        runCatching {
                            queue.stage(
                                chain,
                                listOf(event("valid"), event("forbidden").copy(body = body)),
                            )
                        }.exceptionOrNull()
                    (failure is IllegalStateException) shouldBe true
                    failure?.message shouldBe "C_SCHEMA_VIOLATION"
                    store.protocolDao().lastMigrationIndex(PallaSyncLocalIntents.journalId(chain)) shouldBe null
                    store.dao.queuedBatch(chain, 200) shouldBe emptyList()
                    store.dao.getChainState(chain)?.authorSeq shouldBe 0
                }
                queue.stage(chain, listOf(event("PSR3:debug")))
                queue.resume(chain, keys())
                store.dao.queuedBatch(chain, 200).size shouldBe 1
            } finally {
                db.close()
            }
        }

    @Test
    fun `frozen legacy source page and destination author mapping resume idempotently`() =
        runBlocking<Unit> {
            val db = database()
            try {
                val store = PallaSyncLocalStore(ApplicationProvider.getApplicationContext(), db)
                store.dao.updateChainState(ChainStateEntity(chain, 0, 0, "{}", logId = log, writeState = "active"))
                val intents = queue(store, author(store, MemoryStorage()))
                val migration = "legacy:source:$chain"
                val exportHash = encode(ByteArray(32) { 3 })
                val sourceHash = encode(ByteArray(32) { 4 })
                val quarantinedHash = encode(ByteArray(32) { 5 })

                intents.beginLegacyMigration(migration, "source-chain", "2.1", 2, exportHash, chain, freezeConfirmed = true)
                runCatching {
                    intents.beginLegacyMigration(migration, "source-chain", "2.1", 2, exportHash, chain, freezeConfirmed = false)
                }.isFailure shouldBe true

                intents.stageLegacyBatch(
                    migration,
                    expectedAfterSequence = 0,
                    throughSequence = 2,
                    mappings =
                        listOf(
                            PallaSyncLocalIntents.LegacyEventMapping(
                                1,
                                "01890f3e-7c00-7000-8000-000000000001",
                                0,
                                sourceHash,
                                event("imported"),
                            ),
                        ),
                    decisions =
                        listOf(
                            PallaSyncLocalIntents.LegacySourceDecision(
                                1,
                                "01890f3e-7c00-7000-8000-000000000001",
                                sourceHash,
                                "converted",
                            ),
                            PallaSyncLocalIntents.LegacySourceDecision(
                                2,
                                "01890f3e-7c00-7000-8000-000000000002",
                                quarantinedHash,
                                "quarantined",
                                "C_AEAD_FAILURE",
                            ),
                        ),
                    report = PallaSyncLocalIntents.LegacyBatchReport(2, 1, 0, 0, complete = true),
                )
                intents.legacyMigrationProgress(migration)?.let {
                    it.sourceHighWater shouldBe 2
                    it.sourceComplete shouldBe true
                    it.candidates shouldBe 1
                    it.decisions shouldBe 2
                    it.pending shouldBe 1
                    it.quarantined shouldBe 1
                    it.schemaCounts shouldBe mapOf(SEARCH_HISTORY_SCHEMA_V3 to 1L)
                }
                val migrationPage = intents.legacyMigrationProgressPage(limit = 20)
                migrationPage.items.single().migrationId shouldBe migration
                migrationPage.items.single().sourceChain shouldBe "source-chain"
                migrationPage.nextMigrationId shouldBe migration

                intents.resumeLegacyMigration(migration, keys())
                intents.resumeLegacyMigration(migration, keys())
                val record = store.dao.queuedBatch(chain, 200).single()
                store.dao.getChainState(chain)?.authorSeq shouldBe 1
                val journal = checkNotNull(store.protocolDao().migrationChunk(migration, 1))
                journal.state shouldBe "constructed"
                journal.sourceIdentity shouldBe "legacy:1:01890f3e-7c00-7000-8000-000000000001:0:$sourceHash"
                val decision = checkNotNull(store.protocolDao().migrationChunk(migration, 2))
                decision.state shouldBe "legacy_decision_converted"
                Json.decodeFromString<PallaSyncLocalIntents.LegacySourceDecision>(decision.reportJson).sourceObjectHash shouldBe sourceHash
                val quarantined = checkNotNull(store.protocolDao().migrationChunk(migration, 3))
                quarantined.state shouldBe "legacy_decision_quarantined"
                Json.decodeFromString<PallaSyncLocalIntents.LegacySourceDecision>(quarantined.reportJson).reasonCode shouldBe
                    "C_AEAD_FAILURE"
                val decisionPage = intents.legacyMigrationDecisionPage(migration, afterIndex = 0)
                decisionPage.items.map(PallaSyncLocalIntents.LegacySourceDecision::disposition) shouldBe listOf("converted", "quarantined")
                decisionPage.nextIndex shouldBe 3L
                intents.legacyMigrationProgress(migration)?.let {
                    it.pending shouldBe 0
                    it.constructed shouldBe 1
                }
                PallaSyncDeviceSession.begin(keys()).use { session ->
                    val inner =
                        Json.decodeFromString<PallaSyncInnerRecord>(
                            PallaSyncCore.decryptOwnPreparedRecord(session.handle, record.eventJson),
                        )
                    inner.operations.single().entityId shouldBe "imported"
                }
            } finally {
                db.close()
            }
        }

    private fun database() =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PallaSyncDatabase::class.java).build()

    @Test
    fun `audit handoff uncertainty is idempotent and retains the original occurrence time`() =
        runBlocking<Unit> {
            val db = database()
            try {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val store = PallaSyncLocalStore(context, db)
                store.dao.updateChainState(ChainStateEntity(chain, 20, 0, "{}", logId = log, writeState = "integrity_paused"))
                val prefs = context.getSharedPreferences("recovery-audit-handoff-unit", Context.MODE_PRIVATE)
                prefs.edit().clear().commit()
                val recovery = PallaSyncRecoverySetStore(prefs)
                val packageJson =
                    checkNotNull(javaClass.getResourceAsStream("/pallasync/python-recovery-c8-package.json"))
                        .use { it.readBytes().toString(Charsets.UTF_8) }
                val entry = recovery.observeHolder(PallaSyncRecoverySlot.Entry(packageJson, "public-test", "public-test", "public-test"))
                val event = recoveryAudit(entry, "recovery_ceremony_started", "started").copy(createdAtMs = 42)
                recovery.auditQueue.enqueue(PallaSyncRecoveryAuditQueue.Pending(chain, event))
                val author = author(store, MemoryStorage())
                val intents = queue(store, author)
                runCatching {
                    recovery.auditQueue.drain(chain) {
                        intents.stageRecoveryAudit(chain, it)
                        error("Injected after Room handoff commit")
                    }
                }.isFailure shouldBe true
                recovery.auditQueue
                    .pending(chain)
                    .single()
                    .event shouldBe event
                PallaSyncRecoveryAuditQueue(prefs).drain(chain) {
                    intents.stageRecoveryAudit(chain, it)
                    true
                }
                recovery.auditQueue.pending(chain) shouldBe emptyList()
                store.protocolDao().pendingMigrationCount(PallaSyncLocalIntents.journalId(chain)) shouldBe 1
                store.dao.updateChainState(checkNotNull(store.dao.getChainState(chain)).copy(writeState = "active"))
                intents.resume(chain, keys())
                intents.stageRecoveryAudit(chain, event)
                intents.resume(chain, keys())
                val record = store.dao.queuedBatch(chain, 200).single()
                store.dao.getChainState(chain)?.authorSeq shouldBe 1
                PallaSyncDeviceSession.begin(keys()).use { session ->
                    val inner =
                        Json.decodeFromString<PallaSyncInnerRecord>(
                            PallaSyncCore.decryptOwnPreparedRecord(session.handle, record.eventJson),
                        )
                    inner.operations.single().entityId shouldBe event.entityId
                    inner.operations.single().createdAtMs shouldBe 42
                    inner.operations.single().body shouldBe event.body
                }
            } finally {
                db.close()
            }
        }

    private fun queue(
        store: PallaSyncLocalStore,
        author: PallaSyncLocalAuthor,
    ) = PallaSyncLocalIntents(store, author) { _, events -> events }

    private fun author(
        store: PallaSyncLocalStore,
        storage: MemoryStorage,
        fault: (PallaSyncLocalAuthor.Boundary) -> Unit = {},
    ) = PallaSyncLocalAuthor(store, PallaSyncAuthorJournal(storage), epochTag = { _, _, _ -> null }, fault = fault)

    private fun keys() =
        PallaSyncKeySnapshot(
            chain,
            device,
            encryptionKeyBase64Url = encode(ByteArray(32) { 2 }),
            signingKeyBase64Url = encode(ByteArray(32) { 1 }),
            publicKeyBase64Url = "fixture",
        )

    private fun event(query: String) =
        PallaSyncPendingEvent(
            SEARCH_HISTORY_SCHEMA_V3,
            query,
            "upsert",
            buildJsonObject { put("query", query) },
        )

    private fun encode(value: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(value)

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
}
