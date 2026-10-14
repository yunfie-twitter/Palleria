package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.SnapshotPublicationEntity
import com.yunfie.illustia.pallasync.data.SnapshotPublicationPartEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PallaSyncSnapshotPublicationDaoTest {
    @Test
    @Suppress("LongMethod")
    fun `snapshot receipts persist only after transparency inclusion succeeds`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val journal = PallaSyncSnapshotPublicationJournal(db, CHAIN, COMPACTION, epoch = 4)
                val header =
                    buildJsonObject {
                        put("kind", "header")
                        put("chain_id", CHAIN)
                        put("generation", 2)
                        put("log_id", LOG)
                        put("covered_through_seq", 91)
                        put("covered_log_hash", HASH)
                        put("epoch_hash", OTHER_HASH)
                        put("lamport_max", 7)
                    }
                journal.begin(header, 1)
                val record =
                    buildJsonObject {
                        put("record_id", RECORD)
                        put("chain_id", CHAIN)
                        put("ciphertext", "opaque")
                    }
                val recordHash = checkNotNull(PallaSyncCore.objectHash(record.toString()))
                journal.retain(
                    SnapshotPublicationPartEntity(
                        chainId = CHAIN,
                        compactionId = COMPACTION,
                        partIndex = 0,
                        recordId = RECORD,
                        recordHash = recordHash,
                        signedJson = record.toString(),
                    ),
                )
                val receipt =
                    buildJsonObject {
                        put("chain_id", CHAIN)
                        put("log_id", LOG)
                        put("object_hash", recordHash)
                        put("relay_seq", JsonNull)
                        put("entry_hash", JsonNull)
                        put("receipt_id", "55555555-5555-4555-8555-555555555555")
                    }
                val response =
                    buildJsonObject {
                        put("accepted", JsonArray(listOf(JsonPrimitive(RECORD))))
                        put("rejected", JsonArray(emptyList()))
                        put("upload_receipts", JsonArray(listOf(receipt)))
                    }
                runCatching {
                    journal.verifyAndRetainUploadReceipts(listOf(record), response) {
                        error("C_TRANSPARENCY_INVALID")
                    }
                }.exceptionOrNull()?.message shouldBe "C_TRANSPARENCY_INVALID"
                journal.prepared(0)?.uploadReceiptJson shouldBe null

                var verified = 0
                journal.verifyAndRetainUploadReceipts(listOf(record), response) { verified++ }
                verified shouldBe 1
                journal.prepared(0)?.uploadReceiptJson shouldBe receipt.toString()
            } finally {
                db.close()
            }
        }

    @Test
    fun `producer journal binds resume state to the complete canonical header`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val journal = PallaSyncSnapshotPublicationJournal(db, CHAIN, COMPACTION, epoch = 4)
                val header =
                    buildJsonObject {
                        put("kind", "header")
                        put("chain_id", CHAIN)
                        put("generation", 2)
                        put("log_id", LOG)
                        put("covered_through_seq", 91)
                        put("covered_log_hash", HASH)
                        put("epoch_hash", OTHER_HASH)
                        put("lamport_max", 7)
                    }
                journal.begin(header, 1)
                journal.prepared(0) shouldBe null
                val part =
                    SnapshotPublicationPartEntity(
                        chainId = CHAIN,
                        compactionId = COMPACTION,
                        partIndex = 0,
                        recordId = RECORD,
                        recordHash = HASH,
                        signedJson = "{\"immutable\":true}",
                    )
                journal.retain(part)
                journal.prepared(0) shouldBe part.copy(status = "prepared")
                journal.begin(header, 1)
                runCatching {
                    journal.begin(
                        kotlinx.serialization.json.JsonObject(
                            header +
                                (
                                    "covered_log_hash" to
                                        kotlinx.serialization.json.JsonPrimitive("CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC")
                                ),
                        ),
                        1,
                    )
                }.exceptionOrNull()?.message shouldBe "C_SNAPSHOT_INVALID"
            } finally {
                db.close()
            }
        }

    @Test
    fun `publication journal preserves exact parts and receipts across retry boundaries`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val dao = db.snapshotPublicationDao()
                val publication =
                    SnapshotPublicationEntity(
                        chainId = CHAIN,
                        compactionId = COMPACTION,
                        generation = 2,
                        epoch = 4,
                        coveredThroughSeq = 91,
                        logId = LOG,
                        headerJson = "{\"kind\":\"header\"}",
                        partCount = 1,
                    )
                dao.begin(publication)
                dao.begin(publication)
                dao.publication(CHAIN, COMPACTION) shouldBe publication
                dao.resumablePublications(CHAIN) shouldBe listOf(publication)

                val part =
                    SnapshotPublicationPartEntity(
                        chainId = CHAIN,
                        compactionId = COMPACTION,
                        partIndex = 0,
                        recordId = RECORD,
                        recordHash = HASH,
                        signedJson = "{\"immutable\":true}",
                    )
                dao.retainPrepared(part)
                dao.retainPrepared(part)
                runCatching {
                    dao.retainPrepared(part.copy(recordHash = OTHER_HASH))
                }.exceptionOrNull()?.message shouldBe "C_RECORD_ID_CONFLICT"

                runCatching {
                    dao.finalizeCompaction(CHAIN, COMPACTION, OTHER_HASH, "{\"compaction\":true}")
                }.exceptionOrNull()?.message shouldBe "C_ACCEPTANCE_UNPROVEN"

                dao.retainReceipt(part, "{\"signed_receipt\":1}")
                dao.retainReceipt(part, "{\"signed_receipt\":1}")
                runCatching {
                    dao.retainReceipt(part, "{\"signed_receipt\":2}")
                }.exceptionOrNull()?.message shouldBe "C_SNAPSHOT_INVALID"

                val commitment = HASH
                val compaction = "{\"exact\":\"signed compaction bytes\"}"
                dao.finalizeCompaction(CHAIN, COMPACTION, commitment, compaction)
                dao.finalizeCompaction(CHAIN, COMPACTION, commitment, compaction)
                dao.publication(CHAIN, COMPACTION)?.status shouldBe "compaction_ready"
                dao.parts(CHAIN, COMPACTION).single().uploadReceiptJson shouldBe "{\"signed_receipt\":1}"
                dao.markPublished(CHAIN, COMPACTION, compaction) shouldBe 1
                dao.markPublished(CHAIN, COMPACTION, compaction) shouldBe 0
                dao.publication(CHAIN, COMPACTION)?.status shouldBe "published"
                dao.resumablePublications(CHAIN) shouldBe emptyList()
            } finally {
                db.close()
            }
        }

    private companion object {
        const val CHAIN = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
        const val COMPACTION = "33333333-3333-4333-8333-333333333333"
        const val RECORD = "11111111-1111-4111-8111-111111111111"
        const val LOG = "44444444-4444-4444-8444-444444444444"
        const val HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val OTHER_HASH = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
    }
}
