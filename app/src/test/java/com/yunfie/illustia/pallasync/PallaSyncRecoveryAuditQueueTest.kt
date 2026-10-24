package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Public fixtures and host JNI; does not certify OS authentication, physical media or actual process death. */
@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PallaSyncRecoveryAuditQueueTest {
    private fun fixture(name: String): PallaSyncRecoverySetStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = PallaSyncRecoverySetStore(prefs)
        val packageJson =
            checkNotNull(javaClass.getResourceAsStream("/pallasync/python-recovery-c8-package.json"))
                .use { it.readBytes().toString(Charsets.UTF_8) }
        store.observeHolder(PallaSyncRecoverySlot.Entry(packageJson, "public-test", "public-test", "public-test"))
        return store
    }

    @Test
    fun everyPermittedOccurrenceHasOnlyAllowlistedMetadataAndSurvivesUnavailableWriter() =
        runBlocking<Unit> {
            val name = "recovery-audit-public-queue-unit"
            val store = fixture(name)
            val entry = store.all().single()
            val chain =
                entry.manifest
                    .getValue("chain_id")
                    .toString()
                    .trim('"')
            recoveryAuditEvents.forEach { event -> store.auditQueue.record(entry, event, "succeeded") shouldBe true }
            val before = store.auditQueue.pending(chain)
            before.size shouldBe recoveryAuditEvents.size
            before.map { it.event.entityId }.toSet().size shouldBe before.size
            val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(name, Context.MODE_PRIVATE)
            val reopened = PallaSyncRecoveryAuditQueue(prefs)
            reopened.drain(chain) { false }
            reopened.pending(chain) shouldBe before
            reopened.pending("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA") shouldBe emptyList()
            before.forEach {
                val text = Json.encodeToString(PallaSyncPendingEvent.serializer(), it.event)
                entry.holders.forEach { holder ->
                    text.contains(holder.instanceId) shouldBe false
                    text.contains(holder.shareId) shouldBe false
                }
                text.contains("encrypted_blob") shouldBe false
                text.contains("encrypted_share") shouldBe false
                it.event.body.jsonObject
                    .getValue("subject_id")
                    .toString() shouldBe "null"
                it.event.body.jsonObject
                    .getValue("object_hash")
                    .toString() shouldBe "null"
            }
            reopened.drain(chain) { true }
            reopened.pending(chain) shouldBe emptyList()
        }

    @Test
    fun malformedMetadataAndChangedOccurrenceCannotEnterOrReplaceTheQueue() {
        val store = fixture("recovery-audit-invalid-queue-unit")
        val entry = store.all().single()
        val chain =
            entry.manifest
                .getValue("chain_id")
                .toString()
                .trim('"')
        val event = recoveryAudit(entry, "recovery_ceremony_started", "started")
        store.auditQueue.enqueue(PallaSyncRecoveryAuditQueue.Pending(chain, event))
        val changed = event.copy(body = JsonObject(event.body.jsonObject + ("result" to JsonPrimitive("failed"))))
        shouldThrow<IllegalStateException> { store.auditQueue.enqueue(PallaSyncRecoveryAuditQueue.Pending(chain, changed)) }
            .message shouldBe "C_RECORD_ID_CONFLICT"
        val secretBearing = event.copy(body = JsonObject(event.body.jsonObject + ("credential" to JsonPrimitive("public fixture only"))))
        shouldThrow<IllegalStateException> { store.auditQueue.enqueue(PallaSyncRecoveryAuditQueue.Pending(chain, secretBearing)) }
            .message shouldBe "C_SCHEMA_VIOLATION"
        shouldThrow<IllegalStateException> { recoveryAudit(entry, "recovery_ceremony_started", "started", "device") }
        store.auditQueue
            .pending(chain)
            .single()
            .event shouldBe event
    }

    @Test
    fun cleanupFailureStillClosesEveryLaterSecretOwner() {
        val closed = mutableListOf<Int>()
        val first = IllegalStateException("first cleanup")
        val last = IllegalStateException("last cleanup")
        val failure =
            shouldThrow<IllegalStateException> {
                closeRecoveryResources(
                    listOf(
                        AutoCloseable {
                            closed.add(1)
                            throw first
                        },
                        AutoCloseable { closed.add(2) },
                        AutoCloseable {
                            closed.add(3)
                            throw last
                        },
                    ),
                )
            }
        closed shouldBe listOf(1, 2, 3)
        failure shouldBe first
        failure.suppressed.toList() shouldBe listOf(last)
    }
}
