package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.EntityRegisterEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PallaSyncSnapshotProducerTest {
    @Test
    @Suppress("LongMethod")
    fun `restarts reuse only exact signature verified encrypted parts`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            PallaSyncCore.setSecretForeground(true)
            val phrase = (List(23) { "abandon" } + "art").joinToString(" ")
            val root = PallaSyncCore.beginRootSession(phrase.toByteArray(), byteArrayOf(), "genesis", null, null)
            try {
                PallaSyncCore.createGenesisBundle(root, "snapshot resume", "os-keystore", "https://relay.example.com").use { prepared ->
                    val keys = prepared.snapshot()
                    val chainId = checkNotNull(keys.chainId)
                    val deviceId = checkNotNull(keys.deviceId)
                    db.pallaSyncDao().updateChainState(
                        ChainStateEntity(
                            chainId = chainId,
                            lamport = 0,
                            keyEpoch = keys.epoch,
                            chainVectorJson = "{}",
                            generation = 0,
                            logId = LOG_ID,
                            epochHash = HASH,
                            verifiedSeq = 0,
                            materializedSeq = 0,
                            verifiedHash = HASH,
                            indexRevision = 0,
                        ),
                    )
                    val context =
                        buildJsonObject {
                            put("chain_id", chainId)
                            put("generation", 0)
                            put("epoch", keys.epoch)
                            put("epoch_commitment", prepared.publicResult.epochCommitment)
                            put("metadata_profile", "private")
                        }
                    val secret =
                        java.util.Base64
                            .getUrlDecoder()
                            .decode(keys.epochSecretBase64Url)
                    PallaSyncEpochSession.begin(context, secret).use { epoch ->
                        val journal = PallaSyncSnapshotPublicationJournal(db, chainId, COMPACTION_ID, keys.epoch)
                        val firstUpload = mutableListOf<Pair<String, String>>()
                        val first =
                            PallaSyncSnapshotProducer(db).produce(
                                chainId = chainId,
                                compactionId = COMPACTION_ID,
                                deviceId = deviceId,
                                epochSession = epoch,
                                signingSeed = {
                                    java.util.Base64
                                        .getUrlDecoder()
                                        .decode(keys.signingKeyBase64Url)
                                },
                                devicePublicKeyBase64Url = keys.publicKeyBase64Url,
                                journal = journal,
                                publishPart = { id, signedRecord, _ -> firstUpload += id to signedRecord },
                            )
                        first.parts.size shouldBe 1
                        firstUpload.size shouldBe 1

                        val resumedUpload = mutableListOf<Pair<String, String>>()
                        val resumed =
                            PallaSyncSnapshotProducer(db).produce(
                                chainId = chainId,
                                compactionId = COMPACTION_ID,
                                deviceId = deviceId,
                                epochSession = epoch,
                                signingSeed = { error("resuming an existing part must not create a new signature") },
                                devicePublicKeyBase64Url = keys.publicKeyBase64Url,
                                journal = journal,
                                publishPart = { id, signedRecord, _ -> resumedUpload += id to signedRecord },
                            )
                        resumed.stateCommitment shouldBe first.stateCommitment
                        resumed.parts shouldBe first.parts
                        resumedUpload shouldBe firstUpload
                    }
                }
            } finally {
                PallaSyncCore.endRootSession(root)
                PallaSyncCore.setSecretForeground(false)
                db.close()
            }
        }

    @Test
    @Suppress("LongMethod")
    fun `plans state commitment then creates ordered encrypted snapshot parts`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val chain = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
                val logId = "44444444-4444-4444-8444-444444444444"
                db.pallaSyncDao().updateChainState(
                    ChainStateEntity(
                        chainId = chain,
                        lamport = 0,
                        keyEpoch = 0,
                        chainVectorJson = "{}",
                        generation = 0,
                        logId = logId,
                        epochHash = HASH,
                        verifiedSeq = 0,
                        materializedSeq = 0,
                        verifiedHash = HASH,
                        indexRevision = 0,
                    ),
                )
                val secret = ByteArray(32) { (0xa0 + it).toByte() }
                val context =
                    buildJsonObject {
                        put("chain_id", chain)
                        put("generation", 0)
                        put("epoch", 0)
                        put("epoch_commitment", "3fT41zGvEMHJhoQzcQO_4BC_hPtkbiz6Ea7K-rmG9JM")
                        put("metadata_profile", "queryable")
                    }
                PallaSyncEpochSession.begin(context, secret).use { epoch ->
                    val published = mutableListOf<Triple<String, String, String>>()
                    val snapshot =
                        PallaSyncSnapshotProducer(db, pageSize = 1).produce(
                            chainId = chain,
                            compactionId = COMPACTION_ID,
                            deviceId = DEVICE_ID,
                            epochSession = epoch,
                            signingSeed = { ByteArray(32) { 9 } },
                            publishPart = { id, record, hash -> published += Triple(id, record, hash) },
                        )
                    snapshot.header["chain_id"]!!.jsonPrimitive.content shouldBe chain
                    snapshot.parts.size shouldBe 1
                    published.size shouldBe 1
                    published.single().first shouldBe snapshot.parts.single().recordId
                    PallaSyncCore.objectHash(published.single().second) shouldBe snapshot.parts.single().recordHash
                    snapshot.stateCommitment.length shouldBe 43
                }
            } finally {
                db.close()
            }
        }

    @Test
    @Suppress("LongMethod")
    fun `partitions large logical history without flattening it into one frame`() =
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
                        lamport = ENTITY_COUNT.toLong(),
                        keyEpoch = 0,
                        chainVectorJson = "{}",
                        generation = 0,
                        logId = "44444444-4444-4444-8444-444444444444",
                        epochHash = HASH,
                        verifiedSeq = 0,
                        materializedSeq = 0,
                        verifiedHash = HASH,
                        indexRevision = 1,
                    ),
                )
                val longQuery = "q".repeat(QUERY_BYTES)
                val protocol = db.protocolDao()
                repeat(ENTITY_COUNT) { index ->
                    val recordId = UUID.randomUUID().toString()
                    val query = "$index:$longQuery"
                    val key = PallaSyncConflictResolver.key(index + 1L, "upsert", DEVICE_ID, recordId, 0)
                    protocol.putEntity(
                        EntityRegisterEntity(
                            chain,
                            "palleria.search_history/3",
                            query,
                            key,
                            "upsert",
                            "all",
                            """{"query":"$query"}""",
                            "{}",
                            null,
                        ),
                    )
                }
                val context =
                    buildJsonObject {
                        put("chain_id", chain)
                        put("generation", 0)
                        put("epoch", 0)
                        put("epoch_commitment", "3fT41zGvEMHJhoQzcQO_4BC_hPtkbiz6Ea7K-rmG9JM")
                        put("metadata_profile", "queryable")
                    }
                PallaSyncEpochSession.begin(context, ByteArray(32) { (0xa0 + it).toByte() }).use { epoch ->
                    val uploaded = mutableListOf<PallaSyncSnapshotPartReference>()
                    val snapshot =
                        PallaSyncSnapshotProducer(db, pageSize = 100).produce(
                            chain,
                            COMPACTION_ID,
                            DEVICE_ID,
                            epoch,
                            signingSeed = { ByteArray(32) { 9 } },
                            publishPart = { id, record, hash ->
                                uploaded += PallaSyncSnapshotPartReference(uploaded.size, id, hash)
                                PallaSyncCore.objectHash(record) shouldBe hash
                            },
                        )
                    snapshot.parts.size shouldBe uploaded.size
                    assertTrue(snapshot.parts.size > 1)
                    snapshot.parts.map { it.partIndex } shouldBe snapshot.parts.indices.toList()
                }
            } finally {
                db.close()
            }
        }

    private companion object {
        const val HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val LOG_ID = "44444444-4444-4444-8444-444444444444"
        const val COMPACTION_ID = "55555555-5555-4555-8555-555555555555"
        const val DEVICE_ID = "22222222-2222-4222-8222-222222222222"
        const val ENTITY_COUNT = 1_200
        const val QUERY_BYTES = 1_024
    }
}
