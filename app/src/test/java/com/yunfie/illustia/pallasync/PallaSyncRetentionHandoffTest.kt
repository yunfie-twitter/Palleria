package com.yunfie.illustia.pallasync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** §13.5: an unproven handoff never unlocks immediate expiry or key deletion. */
@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = Application::class)
class PallaSyncRetentionHandoffTest {
    @Test
    fun `missing current snapshot proof blocks immediate eligibility`() =
        runBlocking<Unit> {
            val db = database()
            try {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val handoff = PallaSyncRetentionHandoff(PallaSyncLocalStore(context, db)) { _, _ -> false }

                shouldThrow<IllegalStateException> {
                    handoff.eligibleEpochs(CHAIN, authority("immediate"), nowMs = 500)
                }.message shouldBe "C_MISSING_DEPENDENCY"
            } finally {
                db.close()
            }
        }

    @Test
    fun `missing current snapshot proof postpones windowed expiry`() =
        runBlocking<Unit> {
            val db = database()
            try {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val handoff = PallaSyncRetentionHandoff(PallaSyncLocalStore(context, db)) { _, _ -> false }

                handoff.eligibleEpochs(CHAIN, authority("windowed"), nowMs = 500) shouldBe listOf(0L, 1L)
                handoff.expireAfterHandoff(CHAIN, authority("windowed"), nowMs = 500) shouldBe emptyList()
            } finally {
                db.close()
            }
        }

    private fun database() =
        Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                PallaSyncDatabase::class.java,
            ).build()

    private fun authority(retention: String) =
        Json
            .parseToJsonElement(
                """{"generation":0,"pin":{"log_id":"$LOG"},"parameters":{"policy":{"epoch_retention":"$retention","epoch_window_ms":20}},"epochs":{"0":{"record":{"epoch":0,"generation":0},"committed_at_ms":0},"1":{"record":{"epoch":1,"generation":0},"committed_at_ms":100}}}""",
            ).jsonObject

    private companion object {
        const val CHAIN = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
        const val LOG = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    }
}
