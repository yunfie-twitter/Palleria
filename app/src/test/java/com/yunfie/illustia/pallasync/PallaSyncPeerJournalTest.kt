package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Public journal transitions, including restart; cryptography is exercised separately by actual JNI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PallaSyncPeerJournalTest {
    @Test
    fun `an authority head change invalidates an in-flight enrollment offer`() {
        val prefs =
            ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(
                "peer-journal-unit-authority-change",
                Context.MODE_PRIVATE,
            )
        prefs.edit().clear().commit()
        val journal = PallaSyncPeerJournal(prefs)
        val transcript = journal.create(buildJsonObject { put("request_id", "authority-change") }, "head-before")

        journal.requireCurrentAuthority(transcript, "head-before")
        val failure = runCatching { journal.requireCurrentAuthority(transcript, "head-after") }.exceptionOrNull()
        failure?.message shouldBe "C_MISSING_DEPENDENCY"
    }

    @Test
    fun `a consumed offer or stale update cannot bind a second subject`() {
        val prefs =
            ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(
                "peer-journal-unit-consumed",
                Context.MODE_PRIVATE,
            )
        prefs.edit().clear().commit()
        val journal = PallaSyncPeerJournal(prefs)
        val offer = buildJsonObject { put("request_id", "public-fixture") }
        val created = journal.create(offer, "authority-fixture")
        val offered = journal.update(created, created.copy(stage = PallaSyncPeerJournal.Stage.OFFERED))
        val consumed = journal.update(offered, offered.copy(stage = PallaSyncPeerJournal.Stage.CONSUMED))
        val reopened = PallaSyncPeerJournal(prefs)
        reopened.read(created.requestId) shouldBe consumed
        runCatching { reopened.create(offer, "authority-fixture") }.isFailure shouldBe true
        runCatching { reopened.update(offered, offered.copy(stage = PallaSyncPeerJournal.Stage.BOUND)) }.isFailure shouldBe true
        runCatching { reopened.update(consumed, consumed.copy(stage = PallaSyncPeerJournal.Stage.OFFERED)) }.isFailure shouldBe true
    }

    @Test
    fun `an uncertain submitted approval retains exact evidence and cannot be canceled or replaced`() {
        val prefs =
            ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(
                "peer-journal-unit-submitted",
                Context.MODE_PRIVATE,
            )
        prefs.edit().clear().commit()
        val journal = PallaSyncPeerJournal(prefs)
        var current = journal.create(buildJsonObject { put("request_id", "public-fixture") }, "authority-fixture")
        current = journal.update(current, current.copy(stage = PallaSyncPeerJournal.Stage.OFFERED))
        current =
            journal.update(
                current,
                current.copy(stage = PallaSyncPeerJournal.Stage.BOUND, request = buildJsonObject { put("subject", "fixture") }),
            )
        current =
            journal.update(
                current,
                current.copy(
                    stage = PallaSyncPeerJournal.Stage.TRANSFERRED,
                    certificate = buildJsonObject { put("certificate", "fixture") },
                    envelope = buildJsonObject { put("envelope", "fixture") },
                    envelopes = listOf(buildJsonObject { put("envelope", "fixture") }),
                ),
            )
        current = journal.update(current, current.copy(stage = PallaSyncPeerJournal.Stage.SUBMITTED, approvalBody = "exact public fixture"))
        val reopened = PallaSyncPeerJournal(prefs)
        reopened.read(current.requestId) shouldBe current
        runCatching { reopened.update(current, current.copy(approvalBody = "replacement")) }.isFailure shouldBe true
        runCatching { reopened.update(current, current.copy(stage = PallaSyncPeerJournal.Stage.CONSUMED)) }.isFailure shouldBe true
        val accepted =
            reopened.update(
                current,
                current.copy(
                    stage = PallaSyncPeerJournal.Stage.ACCEPTED,
                    acceptance =
                        buildJsonObject {
                            put("receipt", "fixture")
                        },
                ),
            )
        accepted.approvalBody shouldBe current.approvalBody
        runCatching { reopened.update(accepted, accepted.copy(stage = PallaSyncPeerJournal.Stage.OFFERED)) }.isFailure shouldBe true
    }
}
