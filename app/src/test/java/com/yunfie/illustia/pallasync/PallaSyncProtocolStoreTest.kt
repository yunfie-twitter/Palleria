package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.AuthorSequenceEntity
import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.CompactionCatalogueDao
import com.yunfie.illustia.pallasync.data.LogEntryEntity
import com.yunfie.illustia.pallasync.data.OutboxEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.ProtectedEvidenceEntity
import com.yunfie.illustia.pallasync.data.QuarantineEvidenceEntity
import com.yunfie.illustia.pallasync.data.SignedEventEntity
import com.yunfie.illustia.pallasync.data.SnapshotAuthorGapEntity
import com.yunfie.illustia.pallasync.data.SnapshotAuthorHeadEntity
import com.yunfie.illustia.pallasync.data.SnapshotPartEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
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
class PallaSyncProtocolStoreTest {
    private val chain = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
    private val deviceA = "11111111-1111-4111-8111-111111111111"
    private val deviceB = "ffffffff-ffff-4fff-8fff-ffffffffffff"
    private val log = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"

    @Test
    fun `compaction catalogue lookup selects only authority-verified entries`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val dao: CompactionCatalogueDao = db.compactionCatalogueDao()
                val id = "22222222-2222-4222-8222-222222222222"
                val first =
                    LogEntryEntity(
                        chain,
                        log,
                        1,
                        "hash-1",
                        "{\"kind\":\"compaction\",\"object\":{\"compaction_id\":\"$id\"}}",
                        "{}",
                        "authority_verified",
                    )
                db.protocolDao().putLogEntry(first)
                db.protocolDao().putLogEntry(
                    first.copy(relaySeq = 2, entryHash = "hash-2", validationState = "pending"),
                )
                db.protocolDao().putLogEntry(
                    first.copy(
                        relaySeq = 3,
                        entryHash = "hash-3",
                        signedJson = "{\"kind\":\"admin\",\"object\":{\"target\":{\"compaction_id\":\"$id\"}}}",
                    ),
                )
                dao.verifiedEntries(chain, log, id) shouldBe listOf(first)
                dao.verifiedCount(chain, log) shouldBe 1
            } finally {
                db.close()
            }
        }

    @Test
    fun `snapshot staging resumes exact ciphertext and rejects conflicting part identity`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val protocol = db.snapshotDao()
                val first =
                    SnapshotPartEntity(
                        chainId = chain,
                        compactionId = log,
                        partIndex = 0,
                        partHash = "record-hash",
                        signedJson = "{\"ciphertext\":\"opaque\"}",
                        uploadReceiptJson = "{\"receipt\":\"signed\"}",
                        coveredSeq = 42,
                        coverageJson = "{\"compaction\":\"signed\"}",
                        stagingState = "downloaded",
                    )
                protocol.retainDownloaded(first.copy(uploadReceiptJson = "null"))
                protocol.retainDownloaded(first)
                protocol.part(chain, log, 0) shouldBe first
                protocol.parts(chain, log) shouldBe listOf(first)
                runCatching { protocol.retainDownloaded(first.copy(partHash = "different-hash")) }
                    .exceptionOrNull()
                    ?.message shouldBe "C_SNAPSHOT_INVALID"
            } finally {
                db.close()
            }
        }

    @Test
    fun `progress counts outstanding author gaps and unresolved quarantine states`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val protocol = db.protocolDao()
                protocol.insertAuthor(AuthorSequenceEntity(chain, 0, deviceA, 2, "record-2", "hash-2", "hash-1", true))
                protocol.insertAuthor(AuthorSequenceEntity(chain, 0, deviceA, 3, "record-3", "hash-3", "hash-2", false))
                protocol.quarantine(QuarantineEvidenceEntity(chain, "q1", "C_SIGNATURE_INVALID", "{}", "unresolved"))
                protocol.quarantine(QuarantineEvidenceEntity(chain, "q2", "C_MISSING_DEPENDENCY", "{}", "pending_resolution"))
                protocol.quarantine(QuarantineEvidenceEntity(chain, "q3", "C_SCHEMA_VIOLATION", "{}", "resolved"))

                protocol.unresolvedAuthorGapCount(chain) shouldBe 1
                protocol.unresolvedQuarantineCount(chain) shouldBe 2
            } finally {
                db.close()
            }
        }

    @Test
    fun `verified deletion retains signed proof while erasing chain rows and other evidence`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val protocol = db.protocolDao()
                val dao = db.pallaSyncDao()
                dao.updateChainState(ChainStateEntity(chain, 0, 0, "{}", logId = log, writeState = "active"))
                db.snapshotStateDao().insertSnapshotAuthorHead(
                    SnapshotAuthorHeadEntity(chain, 0, deviceA, 5, "snapshot-head-hash", log),
                )
                db.snapshotStateDao().insertSnapshotAuthorGap(SnapshotAuthorGapEntity(chain, 0, deviceA, 1, 2, log))
                val request =
                    ProtectedEvidenceEntity(
                        "https://relay.example",
                        "key",
                        chain,
                        log,
                        "delete_request",
                        0,
                        "request-hash",
                        "{\"request\":true}",
                    )
                val proof =
                    ProtectedEvidenceEntity(
                        "https://relay.example",
                        "key",
                        chain,
                        log,
                        "delete_receipt",
                        9,
                        "proof-hash",
                        "{\"proof\":true}",
                    )
                protocol.retainEvidence(request)
                db.withTransaction {
                    protocol.retainDeletionProof(proof)
                    protocol.eraseChainState(chain)
                    dao.clearVerifiedDeletedChain(chain)
                }
                dao.getChainState(chain) shouldBe null
                protocol.snapshotAuthorHead(chain, 0, deviceA, 5) shouldBe null
                protocol.unresolvedAuthorGapCount(chain) shouldBe 0
                protocol.evidenceAt("https://relay.example", "key", chain, log, "delete_request", 0) shouldBe emptyList()
                protocol.evidenceAt("https://relay.example", "key", chain, log, "delete_receipt", 9) shouldBe listOf("{\"proof\":true}")
            } finally {
                db.close()
            }
        }

    @Test
    fun `deletion completion revisions remain separate and latest signed receipt is selected`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val protocol = db.protocolDao()
                val base =
                    ProtectedEvidenceEntity("https://relay.example", "key", chain, log, "delete_receipt", 9, "original", "original-receipt")
                val completion = base.copy(objectHash = "completion", signedJson = "completed-receipt")
                protocol.retainEvidence(base)
                protocol.retainEvidence(completion)

                db.deletionEvidenceDao().latestReceiptRevision(chain, log) shouldBe "completed-receipt"
                db.deletionEvidenceDao().latestReceiptAtSequence("https://relay.example", "key", chain, log, 9) shouldBe "completed-receipt"
                protocol.evidenceAt("https://relay.example", "key", chain, log, "delete_receipt", 9).toSet() shouldBe
                    setOf("original-receipt", "completed-receipt")
            } finally {
                db.close()
            }
        }

    @Test
    fun `deletion evidence bundle revisions remain separately retrievable for one receipt`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val protocol = db.protocolDao()
                val empty =
                    ProtectedEvidenceEntity(
                        "https://relay.example",
                        "key",
                        chain,
                        log,
                        "deletion_evidence_bundle",
                        11,
                        "bundle-empty",
                        "empty-witness-archive",
                    )
                val withWitness = empty.copy(objectHash = "bundle-witness", signedJson = "one-witness-archive")
                protocol.retainDeletionProof(empty)
                protocol.retainDeletionProof(withWitness)

                protocol.evidenceAt("https://relay.example", "key", chain, log, "deletion_evidence_bundle", 11).toSet() shouldBe
                    setOf("empty-witness-archive", "one-witness-archive")
            } finally {
                db.close()
            }
        }

    @Test
    fun `provisional acceptance binds a position without applying effects twice`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val protocol = db.protocolDao()
                val dao = db.pallaSyncDao()
                dao.updateChainState(ChainStateEntity(chain, 0, 0, "{}", logId = log, writeState = "active"))
                val first = event(record(1), 6).copy(relaySeq = null)
                val second = event(record(2), 7).copy(relaySeq = null)
                val one = PallaSyncInnerRecord(SEARCH_HISTORY_SCHEMA_V3, 1, null, listOf(operation("first", "upsert", 1)))
                val two = PallaSyncInnerRecord(SEARCH_HISTORY_SCHEMA_V3, 2, first.objectHash, listOf(operation("second", "upsert", 2)))
                runCatching {
                    db.withTransaction {
                        protocol.applyVerifiedRecord(first, 0, deviceA, one)
                        dao.insertOutboxEvent(
                            OutboxEntity(
                                chainId = chain,
                                deviceSeq = 1,
                                status = "queued",
                                recordId = first.recordId,
                                eventJson = first.signedJson,
                                objectHash = first.objectHash,
                            ),
                        )
                        dao.commitAuthorTail(chain, 0, 1, first.objectHash, 1) shouldBe 1
                        error("Injected local commit failure")
                    }
                }.isFailure shouldBe true
                protocol.event(chain, first.recordId) shouldBe null
                dao.outboxRecord(chain, first.recordId) shouldBe null
                dao.getChainState(chain)?.authorSeq shouldBe 0
                db.withTransaction {
                    protocol.applyVerifiedRecord(first, 0, deviceA, one) shouldBe true
                    protocol.applyVerifiedRecord(second, 0, deviceA, two) shouldBe true
                }
                val revision = dao.getChainState(chain)?.indexRevision
                protocol.applyVerifiedRecord(first.copy(relaySeq = 6, receiptJson = "original receipt"), 0, deviceA, one) shouldBe false
                protocol.event(chain, first.recordId)?.relaySeq shouldBe 6
                protocol.event(chain, second.recordId)?.relaySeq shouldBe null
                protocol.event(chain, first.recordId)?.receiptJson shouldBe "original receipt"
                dao.getChainState(chain)?.indexRevision shouldBe revision
                protocol.historyPage(chain, SEARCH_HISTORY_SCHEMA_V3, "all", null, "", 200).size shouldBe 2
                runCatching { protocol.applyVerifiedRecord(first.copy(relaySeq = 9), 0, deviceA, one) }.exceptionOrNull()?.message shouldBe
                    "C_RECORD_ID_CONFLICT"
            } finally {
                db.close()
            }
        }

    @Test
    fun `record-only page commit is atomic and cannot advance global prefix marks`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val dao = db.pallaSyncDao()
                val protocol = db.protocolDao()
                dao.updateChainState(ChainStateEntity(chain, 0, 0, "{}"))
                val inner = PallaSyncInnerRecord(SEARCH_HISTORY_SCHEMA_V3, 1, null, listOf(operation("atomic", "upsert", 1)))
                val attempt =
                    runCatching {
                        db.withTransaction {
                            protocol.applyVerifiedRecord(event(record(1), 6), 0, deviceA, inner)
                            dao.commitInboxPage(chain, 7, 1, emptyList())
                            error("Injected page commit failure")
                        }
                    }
                attempt.isFailure shouldBe true
                protocol.entity(chain, SEARCH_HISTORY_SCHEMA_V3, "atomic") shouldBe null
                protocol.author(chain, 0, deviceA, 1) shouldBe null
                protocol.event(chain, record(1)) shouldBe null
                dao.getChainState(chain)?.lamport shouldBe 0
                dao.getChainState(chain)?.downloadedSeq shouldBe 0
                db.withTransaction {
                    protocol.applyVerifiedRecord(event(record(1), 6), 0, deviceA, inner)
                    dao.commitInboxPage(chain, 7, 1, emptyList())
                }
                dao.getChainState(chain)?.lastRelaySeq shouldBe 7
                dao.getChainState(chain)?.downloadedSeq shouldBe 0
                dao.getChainState(chain)?.verifiedSeq shouldBe 0
                dao.getChainState(chain)?.materializedSeq shouldBe 0
                protocol.entity(chain, SEARCH_HISTORY_SCHEMA_V3, "atomic")?.operation shouldBe "upsert"
            } finally {
                db.close()
            }
        }

    @Test
    fun `switching chains archives prior keys state and queued bytes without permitting existing target replacement`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val dao = db.pallaSyncDao()
                val old = ChainStateEntity(chain, 9, 0, "{}", writeState = "active")
                dao.updateChainState(old)
                val pending =
                    OutboxEntity(chainId = chain, recordId = record(10), deviceSeq = 10, eventJson = "exact old bytes", status = "queued")
                dao.insertOutboxEvent(pending)
                db.protocolDao().applyVerifiedRecord(
                    event(record(1), 1),
                    0,
                    deviceA,
                    PallaSyncInnerRecord(SEARCH_HISTORY_SCHEMA_V3, 1, null, listOf(operation("retained", "upsert", 1))),
                )
                val next = ChainStateEntity("next-chain", 0, 0, "{}", writeState = "active")
                dao.activateChain(next, emptyList())
                dao.getChainState(chain)?.writeState shouldBe "archived"
                dao.getQueuedEvents(chain).single().eventJson shouldBe pending.eventJson
                db.protocolDao().entity(chain, SEARCH_HISTORY_SCHEMA_V3, "retained")?.operation shouldBe "upsert"
                dao.getActiveChainState() shouldBe next
                runCatching { dao.activateChain(old.copy(lamport = 0), emptyList()) }.exceptionOrNull()?.message shouldBe
                    "C_MISSING_DEPENDENCY"
                dao.getChainState(chain)?.lamport shouldBe 9
                dao.getActiveChainState() shouldBe next
            } finally {
                db.close()
            }
        }

    @Test
    fun `equal Lamport delete beats larger UUID upsert and later higher upsert resurrects`() =
        runBlocking<Unit> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = Room.inMemoryDatabaseBuilder(context, PallaSyncDatabase::class.java).build()
            try {
                db.pallaSyncDao().updateChainState(ChainStateEntity(chain, 0, 0, "{}"))
                val dao = db.protocolDao()
                val upsert = operation("query", "upsert", 5)
                val deletion = operation("query", "delete", 5)
                val first = record(1)
                val second = record(2)
                val third = record(3)
                dao.applyVerifiedRecord(
                    event(first, 1),
                    0,
                    deviceB,
                    PallaSyncInnerRecord(SEARCH_HISTORY_SCHEMA_V3, 1, null, listOf(upsert)),
                ) shouldBe
                    true
                dao.applyVerifiedRecord(
                    event(second, 2),
                    0,
                    deviceA,
                    PallaSyncInnerRecord(SEARCH_HISTORY_SCHEMA_V3, 1, null, listOf(deletion)),
                ) shouldBe
                    true
                dao.entity(chain, SEARCH_HISTORY_SCHEMA_V3, "query")?.operation shouldBe "delete"
                dao.historyPage(chain, SEARCH_HISTORY_SCHEMA_V3, "all", null, "", 200).size shouldBe 0
                dao.applyVerifiedRecord(
                    event(first, 1),
                    0,
                    deviceB,
                    PallaSyncInnerRecord(SEARCH_HISTORY_SCHEMA_V3, 1, null, listOf(upsert)),
                ) shouldBe
                    false
                dao.applyVerifiedRecord(
                    event(third, 3),
                    0,
                    deviceB,
                    PallaSyncInnerRecord(SEARCH_HISTORY_SCHEMA_V3, 2, "hash-$first", listOf(operation("query", "upsert", 6))),
                ) shouldBe
                    true
                dao.entity(chain, SEARCH_HISTORY_SCHEMA_V3, "query")?.operation shouldBe "upsert"
                dao
                    .historyPage(chain, SEARCH_HISTORY_SCHEMA_V3, "all", null, "", 200)
                    .single()
                    .winner.lamport shouldBe 6
            } finally {
                db.close()
            }
        }

    @Test
    fun `out of order author gap is resolved and conflicting forward hash rolls back all effects`() =
        runBlocking<Unit> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = Room.inMemoryDatabaseBuilder(context, PallaSyncDatabase::class.java).build()
            try {
                val dao = db.protocolDao()
                db.pallaSyncDao().updateChainState(ChainStateEntity(chain, 0, 0, "{}"))
                dao.applyVerifiedRecord(
                    event(record(9), 2),
                    0,
                    deviceA,
                    PallaSyncInnerRecord(
                        SEARCH_HISTORY_SCHEMA_V3,
                        9,
                        "hash-${record(8)}",
                        listOf(operation("later", "upsert", 9)),
                    ),
                )
                dao.author(chain, 0, deviceA, 9)?.gapBefore shouldBe true
                dao.applyVerifiedRecord(
                    event(record(8), 1),
                    0,
                    deviceA,
                    PallaSyncInnerRecord(SEARCH_HISTORY_SCHEMA_V3, 8, "earlier", listOf(operation("earlier", "upsert", 8))),
                )
                dao.author(chain, 0, deviceA, 9)?.gapBefore shouldBe false
                val result =
                    runCatching {
                        dao.applyVerifiedRecord(
                            event(record(7), 3),
                            0,
                            deviceA,
                            PallaSyncInnerRecord(
                                SEARCH_HISTORY_SCHEMA_V3,
                                7,
                                "before-six",
                                listOf(operation("must-not-apply", "upsert", 100)),
                            ),
                        )
                    }
                result.exceptionOrNull()?.message shouldBe "C_DEVICE_EQUIVOCATION"
                dao.entity(chain, SEARCH_HISTORY_SCHEMA_V3, "must-not-apply") shouldBe null
                dao.event(chain, record(7)) shouldBe null
                db.pallaSyncDao().getChainState(chain)?.lamport shouldBe 9
            } finally {
                db.close()
            }
        }

    @Test
    fun `snapshot author anchor validates suffix predecessor and preserves unresolved gaps`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val dao = db.protocolDao()
                db.pallaSyncDao().updateChainState(ChainStateEntity(chain, 0, 0, "{}"))
                db.snapshotStateDao().insertSnapshotAuthorHead(
                    SnapshotAuthorHeadEntity(chain, 0, deviceA, 5, "snapshot-head-hash", log),
                )
                db.snapshotStateDao().insertSnapshotAuthorGap(SnapshotAuthorGapEntity(chain, 0, deviceA, 1, 2, log))
                db.snapshotStateDao().insertSnapshotAuthorGap(SnapshotAuthorGapEntity(chain, 0, deviceA, 4, 4, log))

                dao.applyVerifiedRecord(
                    event(record(20), 1),
                    0,
                    deviceA,
                    PallaSyncInnerRecord(
                        SEARCH_HISTORY_SCHEMA_V3,
                        6,
                        "snapshot-head-hash",
                        listOf(operation("after-snapshot", "upsert", 6)),
                    ),
                )
                dao.author(chain, 0, deviceA, 6)?.previousHash shouldBe "snapshot-head-hash"
                dao.unresolvedAuthorGapCount(chain) shouldBe 2

                val result =
                    runCatching {
                        dao.applyVerifiedRecord(
                            event(record(21), 2),
                            0,
                            deviceA,
                            PallaSyncInnerRecord(
                                SEARCH_HISTORY_SCHEMA_V3,
                                7,
                                "wrong-predecessor",
                                listOf(operation("must-not-apply", "upsert", 7)),
                            ),
                        )
                    }
                result.exceptionOrNull()?.message shouldBe "C_DEVICE_EQUIVOCATION"
                dao.entity(chain, SEARCH_HISTORY_SCHEMA_V3, "must-not-apply") shouldBe null
                dao.unresolvedAuthorGapCount(chain) shouldBe 2
            } finally {
                db.close()
            }
        }

    @Test
    fun `clear suppresses lower and equal winners but preserves their authoritative registers`() =
        runBlocking<Unit> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = Room.inMemoryDatabaseBuilder(context, PallaSyncDatabase::class.java).build()
            try {
                val dao = db.protocolDao()
                db.pallaSyncDao().updateChainState(ChainStateEntity(chain, 0, 0, "{}"))
                dao.applyVerifiedRecord(
                    event(record(1), 1),
                    0,
                    deviceB,
                    PallaSyncInnerRecord(SEARCH_HISTORY_SCHEMA_V3, 1, null, listOf(operation("old", "upsert", 5))),
                )
                val clear = PallaSyncOperation("*", "clear", 5, 1, buildJsonObject { put("scope", "all") })
                dao.applyVerifiedRecord(
                    event(record(2), 2),
                    0,
                    deviceA,
                    PallaSyncInnerRecord(SEARCH_HISTORY_SCHEMA_V3, 1, null, listOf(clear)),
                )
                dao.historyPage(chain, SEARCH_HISTORY_SCHEMA_V3, "all", null, "", 200).isEmpty() shouldBe true
                dao.entity(chain, SEARCH_HISTORY_SCHEMA_V3, "old")?.operation shouldBe "upsert"
                dao.register(chain, SEARCH_HISTORY_SCHEMA_V3, "all", "clear")?.winner?.opRank shouldBe 1
            } finally {
                db.close()
            }
        }

    @Test
    fun `cold seen membership honors scope clears and subsequent resurrection`() =
        runBlocking<Unit> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = Room.inMemoryDatabaseBuilder(context, PallaSyncDatabase::class.java).build()
            try {
                val dao = db.protocolDao()
                db.pallaSyncDao().updateChainState(ChainStateEntity(chain, 0, 0, "{}"))
                val signed = event(record(1), 1)
                // More than the display page; lookup for a cold item must still succeed.
                for (id in 1..201) {
                    val op = PallaSyncOperation("seen:$id", "upsert", id.toLong(), 1, body = buildJsonObject { put("id", id) })
                    dao.applyOperation(signed, VIEW_HISTORY_SCHEMA_V3, deviceA, id, op)
                }
                dao.historyPage(chain, VIEW_HISTORY_SCHEMA_V3, "seen", null, "", 200).any { it.entityId == "seen:1" } shouldBe false
                dao.liveEntityIds(chain, VIEW_HISTORY_SCHEMA_V3, listOf("seen:1", "seen:999")) shouldBe listOf("seen:1")
                val clear = PallaSyncOperation("*", "clear", 202, 1, context = buildJsonObject { put("scope", "viewed") })
                dao.applyOperation(signed, VIEW_HISTORY_SCHEMA_V3, deviceA, 0, clear)
                dao.liveEntityIds(chain, VIEW_HISTORY_SCHEMA_V3, listOf("seen:1")) shouldBe listOf("seen:1")
                dao.applyOperation(
                    signed,
                    VIEW_HISTORY_SCHEMA_V3,
                    deviceA,
                    0,
                    clear.copy(context = buildJsonObject { put("scope", "seen") }),
                )
                dao.liveEntityIds(chain, VIEW_HISTORY_SCHEMA_V3, listOf("seen:1")) shouldBe emptyList()
                dao.entity(chain, VIEW_HISTORY_SCHEMA_V3, "seen:1")?.operation shouldBe "upsert"
                val restore = PallaSyncOperation("seen:1", "upsert", 203, 1, body = buildJsonObject { put("id", 1) })
                dao.applyOperation(signed, VIEW_HISTORY_SCHEMA_V3, deviceA, 0, restore)
                dao.liveEntityIds(chain, VIEW_HISTORY_SCHEMA_V3, listOf("seen:1")) shouldBe listOf("seen:1")
            } finally {
                db.close()
            }
        }

    private fun record(number: Int) = "019b0000-0000-4000-8000-${number.toString().padStart(12, '0')}"

    private fun event(
        id: String,
        seq: Long,
    ) = SignedEventEntity(chain, id, log, seq, "hash-$id", "{}", null, "verified")

    private fun operation(
        id: String,
        kind: String,
        lamport: Long,
    ) = PallaSyncOperation(
        id,
        kind,
        lamport,
        1,
        body = if (kind == "upsert") buildJsonObject { put("query", id) } else buildJsonObject {},
    )
}
