package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PallaSyncReseedJniTest {
    @Test
    fun `root reseed session prepares a fresh generation identity through JNI`() {
        val fixture = fixture()
        val authority = authority(fixture)
        PallaSyncRootSession
            .beginText(
                MNEMONIC,
                "",
                PallaSyncRootSession.Operation.RESEED,
                fixture
                    .getValue("identity")
                    .jsonObject
                    .getValue("chain_id")
                    .jsonPrimitive.content,
                fixture
                    .getValue("identity")
                    .jsonObject
                    .getValue("admin_public_key")
                    .jsonPrimitive.content,
            ).use { root ->
                root.prepareReseed(authority.toString(), "fresh target device", NOW).use { prepared ->
                    val result = prepared.publicResult
                    result.generation shouldBe 1L
                    val parameters = Json.parseToJsonElement(result.parametersJson).jsonObject
                    parameters.getValue("previous_parameters_hash").jsonPrimitive.content shouldBe
                        PallaSyncCore.objectHash(
                            fixture
                                .getValue("genesis")
                                .jsonObject
                                .getValue("parameters")
                                .toString(),
                        )
                    val epoch = Json.parseToJsonElement(result.epochJson).jsonObject
                    val device = Json.parseToJsonElement(result.deviceRecordJson).jsonObject
                    epoch.getValue("epoch").jsonPrimitive.long shouldBe 1L
                    epoch
                        .getValue("reason")
                        .jsonPrimitive.content shouldBe "reseed"
                    epoch
                        .getValue("members")
                        .jsonArray
                        .single()
                        .jsonPrimitive.content shouldBe result.deviceId
                    device
                        .getValue("device_id")
                        .jsonPrimitive.content shouldBe result.deviceId
                    val envelopeKinds = result.envelopesJson.map(::recipientKind)
                    envelopeKinds shouldBe listOf("device", "recovery")
                    result.epochHash shouldBe PallaSyncCore.objectHash(result.epochJson)
                    prepared.openEpochSession().use { targetEpoch ->
                        targetEpoch.tag("private collection") shouldBe null
                    }
                    val savedKeys = prepared.keySnapshot()
                    resumePallaSyncPreparedReseed(prepared.publicBundleJson(), savedKeys).use { resumed ->
                        resumed.publicResult shouldBe result
                        resumed.openEpochSession().use { targetEpoch ->
                            targetEpoch.tag("private collection") shouldBe null
                        }
                        val seed = resumed.signingSeedCopy()
                        val expectedSeed = Base64.getUrlDecoder().decode(savedKeys.signingKeyBase64Url)
                        try {
                            seed.contentEquals(expectedSeed) shouldBe true
                        } finally {
                            seed.fill(0)
                            expectedSeed.fill(0)
                        }
                    }
                    val alteredEpochSecret =
                        savedKeys.copy(
                            epochSecretBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32)),
                        )
                    shouldThrow<IllegalStateException> {
                        resumePallaSyncPreparedReseed(prepared.publicBundleJson(), alteredEpochSecret).use { resumed ->
                            resumed.openEpochSession().use { }
                        }
                    }
                }
            }
    }

    @Test
    fun `prepared reseed encrypts full state at staged incarnation boundary`() {
        runBlocking {
            val fixture = fixture()
            val authority = authority(fixture)
            val chain =
                fixture
                    .getValue("identity")
                    .jsonObject
                    .getValue("chain_id")
                    .jsonPrimitive.content
            val database =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            PallaSyncCore.setSecretForeground(true)
            val sourceLogId = "44444444-4444-4444-8444-444444444444"
            val stagedLogId = "55555555-5555-4555-8555-555555555555"
            val compactionId = "66666666-6666-4666-8666-666666666666"
            try {
                database.pallaSyncDao().updateChainState(
                    ChainStateEntity(
                        chainId = chain,
                        lamport = 9,
                        keyEpoch = 0,
                        chainVectorJson = "{}",
                        generation = 0,
                        logId = sourceLogId,
                        epochHash = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        verifiedSeq = 0,
                        materializedSeq = 0,
                        verifiedHash = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        indexRevision = 0,
                    ),
                )
                PallaSyncRootSession
                    .beginText(
                        MNEMONIC,
                        "",
                        PallaSyncRootSession.Operation.RESEED,
                        chain,
                        fixture
                            .getValue("identity")
                            .jsonObject
                            .getValue("admin_public_key")
                            .jsonPrimitive.content,
                    ).use { root ->
                        root.prepareReseed(authority.toString(), "fresh target device", NOW).use { prepared ->
                            val uploaded = mutableListOf<String>()
                            val journal = PallaSyncSnapshotPublicationJournal(database, chain, compactionId, prepared.publicResult.epoch)
                            val snapshot =
                                prepared.produceSnapshot(database, stagedLogId, compactionId, journal) { _, record, _ ->
                                    uploaded += record
                                }
                            snapshot.header
                                .getValue("generation")
                                .jsonPrimitive.long shouldBe 1L
                            snapshot.header
                                .getValue("log_id")
                                .jsonPrimitive.content shouldBe stagedLogId
                            snapshot.header
                                .getValue("covered_through_seq")
                                .jsonPrimitive.long shouldBe 0L
                            snapshot.header
                                .getValue("lamport_max")
                                .jsonPrimitive.long shouldBe 9L
                            snapshot.parts.size shouldBe 1
                            uploaded.size shouldBe 1
                            val commitBody =
                                root.createReseedCommitBody(
                                    prepared,
                                    fixture
                                        .getValue("genesis")
                                        .jsonObject
                                        .getValue("parameters")
                                        .toString(),
                                    fixture
                                        .getValue("genesis")
                                        .jsonObject
                                        .getValue("epoch")
                                        .toString(),
                                    snapshot,
                                    NOW,
                                )
                            val body = Json.parseToJsonElement(commitBody).jsonObject
                            body.keys shouldBe setOf("parameters", "epoch", "envelopes", "compaction", "admin_operation", "device_records")
                            body
                                .getValue("compaction")
                                .jsonObject
                                .getValue("log_id")
                                .jsonPrimitive.content shouldBe stagedLogId
                            body
                                .getValue("admin_operation")
                                .jsonObject
                                .getValue("action")
                                .jsonPrimitive.content shouldBe "reseed"
                        }
                    }
            } finally {
                PallaSyncCore.setSecretForeground(false)
                database.close()
            }
        }
    }

    @Test
    fun `non reseed root operation cannot prepare a reseed bundle`() {
        val fixture = fixture()
        val authority = authority(fixture)
        shouldThrow<IllegalStateException> {
            PallaSyncRootSession
                .beginText(
                    MNEMONIC,
                    "",
                    PallaSyncRootSession.Operation.DELETE,
                    fixture
                        .getValue("identity")
                        .jsonObject
                        .getValue("chain_id")
                        .jsonPrimitive.content,
                    fixture
                        .getValue("identity")
                        .jsonObject
                        .getValue("admin_public_key")
                        .jsonPrimitive.content,
                ).use { it.prepareReseed(authority.toString(), "fresh target device", NOW) }
        }
    }

    private fun fixture(): JsonObject =
        Json
            .parseToJsonElement(
                javaClass
                    .getResourceAsStream("/pallasync/python-genesis-acceptance.json")!!
                    .bufferedReader()
                    .use { it.readText() },
            ).jsonObject

    private fun authority(f: JsonObject): JsonObject {
        var authority =
            Json
                .parseToJsonElement(
                    PallaSyncCore.beginAuthorityHistory(
                        f.getValue("parameters").toString().toByteArray(),
                        f.getValue("identity").toString(),
                        f.getValue("origin").jsonPrimitive.content,
                        f.getValue("relay_public_key").jsonPrimitive.content,
                    ),
                ).jsonObject
        val pin = authority.getValue("pin").toString()
        val enroll = f.getValue("enrollment").jsonObject
        val receipts =
            (
                f
                    .getValue("created")
                    .jsonObject
                    .getValue("upload_receipts")
                    .jsonArray +
                    f
                        .getValue("record_upload")
                        .jsonObject
                        .getValue("upload_receipts")
                        .jsonArray +
                    enroll
                        .getValue("accepted")
                        .jsonObject
                        .getValue("upload_receipts")
                        .jsonArray
            ).associateBy {
                it.jsonObject
                    .getValue("relay_seq")
                    .jsonPrimitive.long
            }
        (f.getValue("log_pages").jsonArray + enroll.getValue("log_pages").jsonArray).forEach { pageValue ->
            val page =
                Json
                    .parseToJsonElement(
                        PallaSyncCore.validateLogPage(
                            pageValue.toString().toByteArray(),
                            pin,
                            0,
                            authority.getValue("sequence").jsonPrimitive.long,
                            200,
                        ),
                    ).jsonObject
            page.getValue("items").jsonArray.forEach { entry ->
                val receipt =
                    receipts.getValue(
                        entry.jsonObject
                            .getValue("relay_seq")
                            .jsonPrimitive.long,
                    )
                val checked =
                    PallaSyncCore.validateReceiptResponse(
                        buildJsonObject {
                            put("entry", entry)
                            put("receipt", receipt)
                        }.toString().toByteArray(),
                        entry.toString(),
                        pin,
                    )
                authority =
                    Json
                        .parseToJsonElement(
                            PallaSyncCore.advanceAuthorityHistory(authority.toString(), entry.toString(), checked),
                        ).jsonObject
            }
        }
        return authority
    }

    private fun recipientKind(envelope: String): String {
        val parsed = Json.parseToJsonElement(envelope)
        val objectValue = parsed.jsonObject
        return objectValue.getValue("recipient_kind").jsonPrimitive.content
    }

    private companion object {
        const val MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon abandon art"
        const val NOW = 1_800_000_000_000L
    }
}
