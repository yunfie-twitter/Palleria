package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    fun `legacy source scan seals only exact staged transcript and waits for resolution before authoring`() =
        runBlocking<Unit> {
            val db = database()
            try {
                val store = PallaSyncLocalStore(ApplicationProvider.getApplicationContext(), db)
                store.dao.updateChainState(ChainStateEntity(chain, 0, 0, "{}", logId = log, writeState = "active"))
                val intents = queue(store, author(store, MemoryStorage()))
                val migration = "legacy:resume-safe:$chain"
                val exportHash = encode(ByteArray(32) { 7 })
                val sourceJson = "{\"ciphertext\":\"opaque-record\"}"
                val objectHash = checkNotNull(PallaSyncCore.objectHash(sourceJson))
                val sourceRecordId = "01890f3e-7c00-7000-8000-000000000001"
                intents.beginLegacyMigration(migration, "source-chain", "2.1", 1, exportHash, chain, freezeConfirmed = true)
                intents.archiveLegacySourceRecord(migration, 1, sourceRecordId, objectHash, sourceJson)
                intents.archiveLegacySourceRecord(migration, 1, sourceRecordId, objectHash, sourceJson)
                intents.stageLegacyBatch(
                    migration,
                    expectedAfterSequence = 0,
                    throughSequence = 1,
                    mappings =
                        listOf(
                            PallaSyncLocalIntents.LegacyEventMapping(
                                1,
                                sourceRecordId,
                                0,
                                objectHash,
                                event("candidate"),
                            ),
                        ),
                    decisions =
                        listOf(
                            PallaSyncLocalIntents.LegacySourceDecision(
                                1,
                                sourceRecordId,
                                objectHash,
                                "converted",
                            ),
                        ),
                    report = PallaSyncLocalIntents.LegacyBatchReport(1, 0, 0, 0, complete = false),
                )

                runCatching { intents.completeLegacySourceScan(migration, 1, 0, exportHash) }.isFailure shouldBe true
                intents.legacyMigrationProgress(migration)?.sourceComplete shouldBe false
                store.protocolDao().migrationChunk(migration, 0)?.state shouldBe "legacy_staging"

                intents.completeLegacySourceScan(migration, 1, 1, exportHash)
                intents.completeLegacySourceScan(migration, 1, 1, exportHash)
                intents.legacyMigrationProgress(migration)?.sourceComplete shouldBe true
                store.protocolDao().migrationChunk(migration, 0)?.state shouldBe "legacy_candidates_staged"
                val evidencePage = intents.legacyMigrationSourceEvidencePage(migration, 0)
                evidencePage.items.single().signedRecordJson shouldBe sourceJson
                evidencePage.items.single().sourceObjectHash shouldBe objectHash
                val candidatePage = intents.legacyMigrationCandidatePage(migration, 0)
                val candidate = candidatePage.items.single()
                candidate.event.entityId shouldBe "candidate"
                candidatePage.items.single().sourceSequence shouldBe 1L
                runCatching { intents.resumeLegacyMigration(migration, keys()) }.isFailure shouldBe true
                store.dao.queuedBatch(chain, 200) shouldBe emptyList()
                store.dao.getChainState(chain)?.authorSeq shouldBe 0
            } finally {
                db.close()
            }
        }

    @Test
    fun `frozen legacy source and encrypted export remain unapproved until conflict resolution`() =
        runBlocking<Unit> {
            val db = database()
            try {
                val store = PallaSyncLocalStore(ApplicationProvider.getApplicationContext(), db)
                store.dao.updateChainState(ChainStateEntity(chain, 0, 0, "{}", logId = log, writeState = "active"))
                val intents = queue(store, author(store, MemoryStorage()))
                val migration = "legacy:source:$chain"
                val exportHash = encode(ByteArray(32) { 3 })
                val sourceRecordId1 = "01890f3e-7c00-7000-8000-000000000001"
                val sourceRecordId2 = "01890f3e-7c00-7000-8000-000000000002"
                val sourceJson1 = "{\"ciphertext\":\"record-1\"}"
                val sourceJson2 = "{\"ciphertext\":\"record-2\"}"
                val sourceHash = checkNotNull(PallaSyncCore.objectHash(sourceJson1))
                val quarantinedHash = checkNotNull(PallaSyncCore.objectHash(sourceJson2))

                intents.beginLegacyMigration(migration, "source-chain", "2.1", 2, exportHash, chain, freezeConfirmed = true)
                intents.archiveLegacySourceRecord(migration, 1, sourceRecordId1, sourceHash, sourceJson1)
                intents.archiveLegacySourceRecord(migration, 2, sourceRecordId2, quarantinedHash, sourceJson2)
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
                                sourceRecordId1,
                                0,
                                sourceHash,
                                event("imported"),
                            ),
                        ),
                    decisions =
                        listOf(
                            PallaSyncLocalIntents.LegacySourceDecision(
                                1,
                                sourceRecordId1,
                                sourceHash,
                                "converted",
                            ),
                            PallaSyncLocalIntents.LegacySourceDecision(
                                2,
                                sourceRecordId2,
                                quarantinedHash,
                                "quarantined",
                                "C_AEAD_FAILURE",
                            ),
                        ),
                    report = PallaSyncLocalIntents.LegacyBatchReport(2, 1, 0, 0, complete = false),
                )
                intents.completeLegacySourceScan(migration, 2, 2, exportHash)
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
                migrationPage.nextMigrationId shouldBe null

                runCatching { intents.resumeLegacyMigration(migration, keys()) }.isFailure shouldBe true
                store.dao.queuedBatch(chain, 200) shouldBe emptyList()
                store.dao.getChainState(chain)?.authorSeq shouldBe 0
                val journal = checkNotNull(store.protocolDao().migrationChunk(migration, 1))
                journal.state shouldBe "pending"
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
                    it.pending shouldBe 1
                    it.constructed shouldBe 0
                }
                val sourcePage = intents.legacyMigrationSourceEvidencePage(migration, 0)
                sourcePage.items.map(PallaSyncLocalIntents.LegacySourceEvidence::sourceSequence) shouldBe listOf(1L, 2L)
                sourcePage.items.map(PallaSyncLocalIntents.LegacySourceEvidence::signedRecordJson) shouldBe
                    listOf(sourceJson1, sourceJson2)
            } finally {
                db.close()
            }
        }

    @Test
    fun `ambiguous legacy candidates require a persisted user choice before losers are excluded`() =
        runBlocking<Unit> {
            val db = database()
            try {
                val store = PallaSyncLocalStore(ApplicationProvider.getApplicationContext(), db)
                store.dao.updateChainState(ChainStateEntity(chain, 0, 0, "{}", logId = log, writeState = "active"))
                val intents = queue(store, author(store, MemoryStorage()))
                val migration = "legacy:choice:$chain"
                val exportHash = encode(ByteArray(32) { 5 })
                val sourceRecordId = "01890f3e-7c00-7000-8000-000000000001"
                val sourceJson = "{\"ciphertext\":\"ambiguous\"}"
                val objectHash = checkNotNull(PallaSyncCore.objectHash(sourceJson))
                intents.beginLegacyMigration(migration, "source-chain", "2.1", 1, exportHash, chain, freezeConfirmed = true)
                intents.archiveLegacySourceRecord(migration, 1, sourceRecordId, objectHash, sourceJson)
                intents.stageLegacyBatch(
                    migration,
                    expectedAfterSequence = 0,
                    throughSequence = 1,
                    mappings =
                        listOf(
                            PallaSyncLocalIntents.LegacyEventMapping(
                                1,
                                sourceRecordId,
                                0,
                                objectHash,
                                event("same-entity"),
                                normalizationCollision = true,
                            ),
                            PallaSyncLocalIntents.LegacyEventMapping(
                                1,
                                sourceRecordId,
                                1,
                                objectHash,
                                event("same-entity").copy(body = buildJsonObject { put("query", "second") }),
                                normalizationCollision = true,
                            ),
                        ),
                    decisions =
                        listOf(PallaSyncLocalIntents.LegacySourceDecision(1, sourceRecordId, objectHash, "converted")),
                    report = PallaSyncLocalIntents.LegacyBatchReport(1, 0, 0, 0, complete = false),
                )
                intents.completeLegacySourceScan(migration, 1, 1, exportHash)

                runCatching { intents.finalizeLegacyMigrationChoices(migration) }.isFailure shouldBe true
                store.protocolDao().pendingMigrationCount(migration) shouldBe 2
                store.protocolDao().migrationChunk(migration, 0)?.state shouldBe "legacy_candidates_staged"

                val candidates = intents.legacyMigrationCandidatePage(migration, 0).items
                candidates.size shouldBe 2
                intents.chooseLegacyMigrationCandidate(migration, candidates[1].groupKey, candidates[1].migrationEntryIndex)
                intents
                    .legacyMigrationCandidatePage(migration, 0)
                    .items
                    .single {
                        it.migrationEntryIndex ==
                            candidates[1].migrationEntryIndex
                    }.selectedByUser shouldBe
                    true
                intents.finalizeLegacyMigrationChoices(migration)

                store.protocolDao().migrationChunk(migration, 0)?.state shouldBe "legacy_staged"
                store.protocolDao().pendingMigrationCount(migration) shouldBe 1
                store.protocolDao().migrationStateCount(migration, "legacy_candidate_excluded") shouldBe 1
                store.dao.queuedBatch(chain, 200) shouldBe emptyList()
                store.dao.getChainState(chain)?.authorSeq shouldBe 0
            } finally {
                db.close()
            }
        }

    @Test
    fun `ordinary repeated entity updates remain separate migration operations`() =
        runBlocking<Unit> {
            val db = database()
            try {
                val store = PallaSyncLocalStore(ApplicationProvider.getApplicationContext(), db)
                store.dao.updateChainState(ChainStateEntity(chain, 0, 0, "{}", logId = log, writeState = "active"))
                val intents = queue(store, author(store, MemoryStorage()))
                val migration = "legacy:updates:$chain"
                val exportHash = encode(ByteArray(32) { 6 })
                val sourceRecordId = "01890f3e-7c00-7000-8000-000000000001"
                val sourceJson = "{\"ciphertext\":\"updates\"}"
                val objectHash = checkNotNull(PallaSyncCore.objectHash(sourceJson))
                intents.beginLegacyMigration(migration, "source-chain", "2.1", 1, exportHash, chain, freezeConfirmed = true)
                intents.archiveLegacySourceRecord(migration, 1, sourceRecordId, objectHash, sourceJson)
                intents.stageLegacyBatch(
                    migration,
                    expectedAfterSequence = 0,
                    throughSequence = 1,
                    mappings =
                        listOf(
                            PallaSyncLocalIntents.LegacyEventMapping(1, sourceRecordId, 0, objectHash, event("same-entity")),
                            PallaSyncLocalIntents.LegacyEventMapping(
                                1,
                                sourceRecordId,
                                1,
                                objectHash,
                                event("same-entity"),
                            ),
                        ),
                    decisions =
                        listOf(PallaSyncLocalIntents.LegacySourceDecision(1, sourceRecordId, objectHash, "converted")),
                    report = PallaSyncLocalIntents.LegacyBatchReport(1, 0, 0, 0, complete = false),
                )
                intents.completeLegacySourceScan(migration, 1, 1, exportHash)
                intents.finalizeLegacyMigrationChoices(migration)

                store.protocolDao().pendingMigrationCount(migration) shouldBe 2
                store.protocolDao().migrationStateCount(migration, "legacy_candidate_excluded") shouldBe 0
                val candidatePage = intents.legacyMigrationCandidatePage(migration, 0)
                val migratedEntities = candidatePage.items.map { it.event.entityId }.toSet()
                migratedEntities shouldBe setOf("same-entity")

                intents.resumeLegacyMigration(migration, keys())
                val uploadProgress = checkNotNull(intents.legacyMigrationProgress(migration))
                uploadProgress.state shouldBe "legacy_uploading"
                uploadProgress.pending shouldBe 0
                uploadProgress.constructed shouldBe 2
                val uploads = store.dao.queuedBatch(chain, 200)
                uploads.size shouldBe 2
                uploads.forEachIndexed { index, upload ->
                    val objectHash = checkNotNull(upload.objectHash)
                    val receiptHash = encode(ByteArray(32) { (index + 1).toByte() })
                    store.transaction {
                        intents.confirmLegacyMigrationReceipt(chain, checkNotNull(upload.recordId), objectHash, receiptHash)
                    }
                }
                val progress = checkNotNull(intents.legacyMigrationProgress(migration))
                progress.accepted shouldBe 2
                progress.state shouldBe "legacy_receipts_complete"
                val auditRows =
                    store
                        .protocolDao()
                        .pendingMigrationPage(PallaSyncLocalIntents.journalId(chain), 0, 10)
                auditRows.none { it.sourceIdentity == AUDIT_SCHEMA_V3 } shouldBe true
                intents.resumeLegacyMigration(migration, keys())
                val auditRowsAfterResume =
                    store
                        .protocolDao()
                        .pendingMigrationPage(PallaSyncLocalIntents.journalId(chain), 0, 10)
                auditRowsAfterResume.count { it.sourceIdentity == AUDIT_SCHEMA_V3 } shouldBe 0
                val first = uploads.first()
                val firstReceiptHash = encode(ByteArray(32) { 1 })
                store.transaction {
                    intents.confirmLegacyMigrationReceipt(
                        chain,
                        checkNotNull(first.recordId),
                        checkNotNull(first.objectHash),
                        firstReceiptHash,
                    )
                }
                runCatching {
                    store.transaction {
                        intents.confirmLegacyMigrationReceipt(
                            chain,
                            checkNotNull(first.recordId),
                            checkNotNull(first.objectHash),
                            encode(ByteArray(32) { 9 }),
                        )
                    }
                }.isFailure shouldBe true
            } finally {
                db.close()
            }
        }

    @Test
    fun `v21 migration freezes the last event in verified relay sequence order`() =
        runBlocking<Unit> {
            val db = database()
            try {
                val store = PallaSyncLocalStore(ApplicationProvider.getApplicationContext(), db)
                store.dao.updateChainState(ChainStateEntity(chain, 0, 0, "{}", logId = log, writeState = "active"))
                val intents = queue(store, author(store, MemoryStorage()))
                val migration = "legacy:sequence:$chain"
                val exportHash = encode(ByteArray(32) { 7 })
                val recordIds =
                    listOf(
                        "01890f3e-7c00-7000-8000-000000000001",
                        "01890f3e-7c00-7000-8000-000000000002",
                    )
                val sourceJson = listOf("{\"ciphertext\":\"older\"}", "{\"ciphertext\":\"newer\"}")
                val hashes = sourceJson.map { checkNotNull(PallaSyncCore.objectHash(it)) }
                intents.beginLegacyMigration(migration, "source-chain", "2.1", 2, exportHash, chain, freezeConfirmed = true)
                recordIds.indices.forEach { index ->
                    intents.archiveLegacySourceRecord(migration, index + 1L, recordIds[index], hashes[index], sourceJson[index])
                }
                intents.stageLegacyBatch(
                    migration,
                    expectedAfterSequence = 0,
                    throughSequence = 2,
                    mappings =
                        listOf(
                            PallaSyncLocalIntents.LegacyEventMapping(
                                1,
                                recordIds[0],
                                0,
                                hashes[0],
                                event("same-entity").copy(body = buildJsonObject { put("query", "older") }),
                                legacyEventSequenceOrdered = true,
                            ),
                            PallaSyncLocalIntents.LegacyEventMapping(
                                2,
                                recordIds[1],
                                0,
                                hashes[1],
                                event("same-entity").copy(body = buildJsonObject { put("query", "newer") }),
                                legacyEventSequenceOrdered = true,
                            ),
                        ),
                    decisions =
                        recordIds.indices.map { index ->
                            PallaSyncLocalIntents.LegacySourceDecision(index + 1L, recordIds[index], hashes[index], "converted")
                        },
                    report = PallaSyncLocalIntents.LegacyBatchReport(2, 0, 0, 0, complete = false),
                )
                intents.completeLegacySourceScan(migration, 2, 2, exportHash)
                intents.finalizeLegacyMigrationChoices(migration)

                val candidates = intents.legacyMigrationCandidatePage(migration, 0).items
                candidates.single { it.excluded }.event.body shouldBe buildJsonObject { put("query", "older") }
                candidates.single { !it.excluded }.event.body shouldBe buildJsonObject { put("query", "newer") }
                store.protocolDao().pendingMigrationCount(migration) shouldBe 1
            } finally {
                db.close()
            }
        }

    @Test
    fun `v20 favorite snapshots reproduce incoming-first distinct merge order`() =
        runBlocking<Unit> {
            val db = database()
            try {
                val store = PallaSyncLocalStore(ApplicationProvider.getApplicationContext(), db)
                store.dao.updateChainState(ChainStateEntity(chain, 0, 0, "{}", logId = log, writeState = "active"))
                val intents = queue(store, author(store, MemoryStorage()))
                val migration = "legacy:v20-favorite-distinct:$chain"
                val exportHash = encode(ByteArray(32) { 9 })
                val recordIds =
                    listOf(
                        "01890f3e-7c00-7000-8000-000000000005",
                        "01890f3e-7c00-7000-8000-000000000006",
                    )
                val sourceJson = listOf("{\"snapshot\":1}", "{\"snapshot\":2}")
                val hashes = sourceJson.map { checkNotNull(PallaSyncCore.objectHash(it)) }
                intents.beginLegacyMigration(migration, "source-chain", "2.0", 2, exportHash, chain, freezeConfirmed = true)
                recordIds.indices.forEach { index ->
                    intents.archiveLegacySourceRecord(migration, index + 1L, recordIds[index], hashes[index], sourceJson[index])
                }

                fun favorite(tag: String) =
                    PallaSyncPendingEvent(
                        FAVORITE_TAG_SCHEMA_V3,
                        tag,
                        "upsert",
                        buildJsonObject { put("tag", tag) },
                    )
                intents.stageLegacyBatch(
                    migration,
                    expectedAfterSequence = 0,
                    throughSequence = 2,
                    mappings =
                        listOf(
                            Triple(1, 0, "a"),
                            Triple(1, 1, "b"),
                            Triple(2, 0, "b"),
                            Triple(2, 1, "c"),
                        ).map { (source, index, tag) ->
                            PallaSyncLocalIntents.LegacyEventMapping(
                                source.toLong(),
                                recordIds[source - 1],
                                index,
                                hashes[source - 1],
                                favorite(tag),
                                legacyFavoriteSnapshotUnique = true,
                            )
                        },
                    decisions =
                        recordIds.indices.map { index ->
                            PallaSyncLocalIntents.LegacySourceDecision(index + 1L, recordIds[index], hashes[index], "converted")
                        },
                    report = PallaSyncLocalIntents.LegacyBatchReport(2, 0, 0, 0, complete = false),
                )
                intents.completeLegacySourceScan(migration, 2, 2, exportHash, listOf("b", "c", "a"))
                intents.finalizeLegacyMigrationChoices(migration)

                val candidates = intents.legacyMigrationCandidatePage(migration, 0).items
                candidates.single { it.event.entityId == "b" && !it.excluded }.sourceSequence shouldBe 2L
                candidates.single { it.event.entityId == "b" && it.excluded }.sourceSequence shouldBe 1L
                candidates.filterNot { it.excluded }.map { it.event.entityId } shouldBe listOf("a", "b", "c")
                val preparedKeys =
                    candidates
                        .filterNot { it.excluded }
                        .associate { candidate ->
                            val work =
                                Json.decodeFromString<PallaSyncReplayWork>(
                                    checkNotNull(store.protocolDao().migrationChunk(migration, candidate.migrationEntryIndex)).reportJson,
                                )
                            candidate.event.entityId to
                                work.event.context
                                    .getValue("order_key")
                                    .jsonPrimitive
                                    .content
                        }
                listOf("b", "c", "a").map(preparedKeys::getValue) shouldBe
                    listOf("b", "c", "a").map { tag -> preparedKeys.getValue(tag) }.sorted()
                store.protocolDao().pendingMigrationCount(migration) shouldBe 3
            } finally {
                db.close()
            }
        }

    @Test
    fun `v20 search snapshots merge newest first and author the final order oldest to newest`() =
        runBlocking<Unit> {
            val db = database()
            try {
                val store = PallaSyncLocalStore(ApplicationProvider.getApplicationContext(), db)
                store.dao.updateChainState(ChainStateEntity(chain, 0, 0, "{}", logId = log, writeState = "active"))
                val intents = queue(store, author(store, MemoryStorage()))
                val migration = "legacy:v20-search-merge:$chain"
                val exportHash = encode(ByteArray(32) { 6 })
                val recordIds =
                    listOf(
                        "01890f3e-7c00-7000-8000-000000000007",
                        "01890f3e-7c00-7000-8000-000000000008",
                    )
                val sourceJson = listOf("{\"snapshot\":1}", "{\"snapshot\":2}")
                val hashes = sourceJson.map { checkNotNull(PallaSyncCore.objectHash(it)) }
                intents.beginLegacyMigration(migration, "source-chain", "2.0", 2, exportHash, chain, freezeConfirmed = true)
                recordIds.indices.forEach { index ->
                    intents.archiveLegacySourceRecord(migration, index + 1L, recordIds[index], hashes[index], sourceJson[index])
                }

                // Each incoming /1 search list is most-recent-first. Reverse each list for
                // increasing v3 last_access_seq, then merge verified snapshots by source order.
                val mappings =
                    listOf(
                        Triple(1, 0, "beta"),
                        Triple(1, 1, "alpha"),
                        Triple(2, 0, "alpha"),
                        Triple(2, 1, "gamma"),
                    ).map { (source, candidateIndex, query) ->
                        PallaSyncLocalIntents.LegacyEventMapping(
                            sourceSequence = source.toLong(),
                            sourceRecordId = recordIds[source - 1],
                            candidateIndex = candidateIndex,
                            sourceObjectHash = hashes[source - 1],
                            event = event(query),
                            legacyEventSequenceOrdered = true,
                        )
                    }
                intents.stageLegacyBatch(
                    migration,
                    expectedAfterSequence = 0,
                    throughSequence = 2,
                    mappings = mappings,
                    decisions =
                        recordIds.indices.map { index ->
                            PallaSyncLocalIntents.LegacySourceDecision(index + 1L, recordIds[index], hashes[index], "converted")
                        },
                    report = PallaSyncLocalIntents.LegacyBatchReport(2, 0, 0, 0, complete = false),
                )
                intents.completeLegacySourceScan(migration, 2, 2, exportHash)
                intents.finalizeLegacyMigrationChoices(migration)

                val candidates = intents.legacyMigrationCandidatePage(migration, 0).items
                candidates.filter { it.excluded }.map { it.event.entityId } shouldBe listOf("alpha")
                candidates.filterNot { it.excluded }.map { it.event.entityId } shouldBe listOf("beta", "alpha", "gamma")
                candidates.single { it.event.entityId == "alpha" && !it.excluded }.sourceSequence shouldBe 2L
                store.protocolDao().pendingMigrationCount(migration) shouldBe 3
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
