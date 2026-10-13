package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PallaSyncRecoveryInventoryTest {
    private fun holder(): PallaSyncRecoverySlot.Entry {
        val packageJson =
            checkNotNull(javaClass.getResourceAsStream("/pallasync/python-recovery-c8-package.json"))
                .use { it.readBytes().toString(Charsets.UTF_8) }
        return PallaSyncRecoverySlot.Entry(packageJson, "public-test-only", "public-test-only", "public-test-only")
    }

    private fun store(name: String): PallaSyncRecoverySetStore {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return PallaSyncRecoverySetStore(prefs)
    }

    @Test
    fun storedSignedActivePackageDoesNotProveCurrentCredentialAvailabilityOrResetLocalFlags() {
        val source = holder()
        val store = store("recovery-inventory-unit")
        val preparedPackage =
            JsonObject(
                source.packageObject + (
                    "status_chain" to
                        JsonArray(
                            source.packageObject
                                .getValue("status_chain")
                                .jsonArray
                                .take(1),
                        )
                ),
            )
        store.observeHolder(source.copy(packageJson = preparedPackage.toString())).state shouldBe "prepared"
        val observed = store.observeHolder(source)
        observed.state shouldBe "active"
        observed.usable shouldBe 0
        observed.ordinaryRecoveryAvailable shouldBe false
        val compromised = store.compromised(observed)
        val repeated = store.observeHolder(source)
        repeated shouldBe compromised
        store.all() shouldBe listOf(compromised)
        shouldThrow<IllegalStateException> { store.lost(observed, observed.holders[0].shareId) }
            .message shouldBe "C_RECOVERY_SHARE_INVALID"
    }

    @Test
    fun holderForkSuspendsAlreadyKnownInventoryAndSurvivesReobservation() {
        val source = holder()
        val store = store("recovery-inventory-holder-fork-unit")
        val known = store.observeHolder(source)
        val fork =
            checkNotNull(javaClass.getResourceAsStream("/pallasync/python-recovery-c8-retired-status.json"))
                .use { Json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject }
        val alternate = JsonArray(listOf(known.statuses.first(), fork)).toString()
        val marked = store.observeHolder(source.copy(conflictingStatusChain = alternate))
        marked.conflictingStatuses shouldBe alternate
        marked.statusesJson shouldBe known.statusesJson
        marked.ordinaryRecoveryAvailable shouldBe false
        store.observeHolder(source) shouldBe marked
        store.all() shouldBe listOf(marked)
        shouldThrow<IllegalStateException> { store.observeHolder(source.copy(conflictingStatusChain = "[{}]")) }
            .message shouldBe "C_RECOVERY_SHARE_INVALID"
        store.read(known.id) shouldBe marked
    }

    @Test
    fun verifiedLocalReplacementRemovesOnlyItsOldAvailabilityWithoutErasingSignedEvidence() {
        val source = holder()
        val store = store("recovery-inventory-replacement-unit")
        val prepared =
            JsonObject(
                source.packageObject + (
                    "status_chain" to
                        JsonArray(
                            source.packageObject
                                .getValue("status_chain")
                                .jsonArray
                                .take(1),
                        )
                ),
            )
        val initial = store.observeHolder(source.copy(packageJson = prepared.toString()))
        val recordHash = checkNotNull(PallaSyncCore.objectHash(source.record.toString()))
        val shareId =
            source.record
                .getValue("recovery_share_id")
                .jsonPrimitive.content
        val instanceId =
            source.record
                .getValue("holder_instance_id")
                .jsonPrimitive.content
        val saved = store.readback(initial, shareId, recordHash)
        val active = store.updateStatus(saved, source.packageObject.getValue("status_chain").toString(), false)
        active.usable shouldBe 1
        val evidence =
            PallaSyncRecoverySlot.PublicEvidence(
                active.manifestJson,
                active.statusesJson,
                recordHash,
                source.record
                    .getValue("recovery_blob_hash")
                    .jsonPrimitive.content,
                active.head,
                false,
                null,
            )
        store.observeReplacedHolder(instanceId, active.id, evidence) shouldBe active
        shouldThrow<IllegalStateException> {
            store.observeReplacedHolder(instanceId, null, evidence.copy(recordHash = "wrong"))
        }.message shouldBe "C_RECOVERY_SHARE_INVALID"
        store.read(active.id) shouldBe active
        val replaced = checkNotNull(store.observeReplacedHolder(instanceId, null, evidence))
        replaced.usable shouldBe 0
        replaced.holders.single { it.instanceId == instanceId }.lost shouldBe true
        replaced.manifestJson shouldBe active.manifestJson
        replaced.statusesJson shouldBe active.statusesJson
        replaced.head shouldBe active.head
        replaced.holders.single { it.instanceId == instanceId }.recordHash shouldBe recordHash
        store.observeReplacedHolder(instanceId, null, evidence) shouldBe replaced
        val prefs =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getSharedPreferences("recovery-inventory-replacement-unit", Context.MODE_PRIVATE)
        PallaSyncRecoverySetStore(prefs).read(active.id) shouldBe replaced
    }

    @Test
    fun leakAuditUsesOnlyTheExactMetadataAllowlistAndFreshOccurrenceIds() {
        val entry = store("recovery-inventory-audit-unit").observeHolder(holder())
        val first = recoverySafetyAudit(entry, null)
        val second = recoverySafetyAudit(entry, null)
        (first.entityId == second.entityId) shouldBe false
        first.context.toString() shouldBe "{}"
        first.schema shouldBe "pallasync.audit/3"
        val body = first.body.jsonObject
        body.keys shouldBe setOf("event", "subject_id", "object_hash", "result", "ext")
        val recovery =
            body
                .getValue("ext")
                .jsonObject
                .getValue("org.palleria.recovery")
                .jsonObject
        recovery.keys shouldBe
            setOf("recovery_set_id", "recovery_secret_fingerprint", "threshold", "total_shares", "holder_kind", "holder_counts")
        recovery.getValue("holder_counts").toString() shouldBe "{\"device\":2,\"offline\":1}"
        val parsed = Json.parseToJsonElement(first.body.toString()).jsonObject
        parsed.getValue("subject_id").toString() shouldBe "null"
        parsed.getValue("object_hash").toString() shouldBe "null"
        val share = entry.holders.first().shareId
        val loss = recoverySafetyAudit(entry, share).body.jsonObject
        loss.getValue("event").toString() shouldBe "\"recovery_share_lost\""
        loss
            .getValue("ext")
            .jsonObject
            .getValue("org.palleria.recovery")
            .jsonObject
            .getValue("holder_kind")
            .toString() shouldBe
            "\"device\""
        loss.toString().contains(share) shouldBe false
        shouldThrow<IllegalStateException> { recoverySafetyAudit(entry, "not-a-member") }.message shouldBe "C_RECOVERY_SET_MISMATCH"
    }
}
