package com.yunfie.illustia.pallasync

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PallaSyncEpochJournalTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("epoch-journal-test-${UUID.randomUUID()}", Context.MODE_PRIVATE)
    private val chain = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"

    private fun candidate() =
        PallaSyncEpochJournal.Candidate(
            "https://relay.example.com",
            "11111111-1111-4111-8111-111111111111",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            PallaSyncPreparedEpoch.PublicResult(
                chain,
                0,
                1,
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                JsonObject(emptyMap()),
            ),
        )

    @Test
    fun `restart keeps exact proposal and unknown dispatch outcome until readback and ack`() {
        val first = PallaSyncEpochJournal(prefs)
        val candidate = candidate()
        val secret = ByteArray(32) { 7 }
        first.stage(candidate, secret)
        secret.all { it == 0.toByte() } shouldBe true
        val resumed = PallaSyncEpochJournal(prefs)
        resumed.pending(chain) shouldBe candidate
        val readback = resumed.normalSecret(candidate)
        try {
            readback.toList() shouldBe List(32) { 7.toByte() }
        } finally {
            readback.fill(0)
        }
        val submitted = resumed.submitted(candidate)
        shouldThrow<IllegalStateException> { resumed.cancelBeforeDispatch(submitted) }.message shouldBe "C_ACCEPTANCE_UNPROVEN"
        shouldThrow<IllegalStateException> { resumed.acknowledged(submitted) }.message shouldBe "C_ACCEPTANCE_UNPROVEN"
        val delivered = PallaSyncEpochJournal(prefs).delivered(submitted)
        resumed.finish(resumed.acknowledged(delivered))
        resumed.pending(chain) shouldBe null
        prefs.all.isEmpty() shouldBe true
    }

    @Test
    fun `verified conflicting winner releases unpublished secret but absent winner retains it`() {
        val journal = PallaSyncEpochJournal(prefs)
        val candidate = candidate()
        journal.stage(candidate, ByteArray(32) { 9 })
        val submitted = journal.submitted(candidate)
        val absent =
            buildJsonObject {
                put("pin", buildJsonObject { put("chain_id", chain) })
                put("epochs", JsonObject(emptyMap()))
            }
        shouldThrow<IllegalStateException> { journal.discardCasLoser(submitted, absent) }.message shouldBe "C_MISSING_DEPENDENCY"
        journal.pending(chain) shouldBe submitted
        val fixture =
            Json.parseToJsonElement(
                javaClass
                    .getResourceAsStream(
                        "/pallasync/python-epoch-acceptance.json",
                    )!!
                    .bufferedReader()
                    .use {
                        it.readText()
                    },
            )
        val epochs = fixture as JsonObject
        val winner = ((epochs.getValue("epoch_checks") as JsonObject).getValue("manual_body") as JsonObject).getValue("epoch")
        val authority =
            buildJsonObject {
                put("pin", buildJsonObject { put("chain_id", chain) })
                put("epochs", buildJsonObject { put("1", buildJsonObject { put("record", winner) }) })
            }
        journal.discardCasLoser(submitted, authority)
        prefs.all.isEmpty() shouldBe true
    }

    @Test
    fun `failed OS commit wipes input and leaves no pending proposal`() {
        val failed =
            object : SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor {
                    val editor = prefs.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun putString(
                            key: String?,
                            value: String?,
                        ): SharedPreferences.Editor {
                            editor.putString(key, value)
                            return this
                        }

                        override fun commit(): Boolean = false
                    }
                }
            }
        val secret = ByteArray(32) { 6 }
        shouldThrow<IllegalStateException> { PallaSyncEpochJournal(failed).stage(candidate(), secret) }.message shouldBe
            "C_LOCAL_STORAGE_FULL"
        secret.all { it == 0.toByte() } shouldBe true
        prefs.all.isEmpty() shouldBe true
    }
}
