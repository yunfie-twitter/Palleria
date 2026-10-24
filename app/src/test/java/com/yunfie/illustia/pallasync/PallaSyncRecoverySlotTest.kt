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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Single-slot durability and signed status fork handling. OS wrapping/authentication are tested on Android separately. */
@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PallaSyncRecoverySlotTest {
    private fun source(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/pallasync/$name")).use {
            it.readBytes().toString(Charsets.UTF_8)
        }

    private fun slot(name: String): PallaSyncRecoverySlot {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs
            .edit()
            .clear()
            .putString("physical-holder-instance", "88888888-8888-4888-8888-888888888888")
            .commit()
        return PallaSyncRecoverySlot(prefs)
    }

    private fun entry() =
        PallaSyncRecoverySlot.Entry(source("python-recovery-c8-package.json"), "public-test-wrapper", "test-iv", "test-cipher")

    @Test
    fun singleHolderRefusesSecondSlotAndStaleEraseAcrossReopen() {
        val first = slot("recovery-slot-one-unit")
        val candidate = entry()
        val accepted = first.replace(null, candidate, candidate.admin, false)
        shouldThrow<IllegalStateException> { first.replace(accepted, candidate.copy(keyAlias = "second-test"), candidate.admin, false) }
            .message shouldBe "C_RECOVERY_SHARE_ALREADY_USED"
        first.read() shouldBe accepted
        val prefs =
            ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(
                "recovery-slot-one-unit",
                Context.MODE_PRIVATE,
            )
        val reopened = PallaSyncRecoverySlot(prefs)
        reopened.read() shouldBe accepted
        val compromised = reopened.markCompromised(accepted)
        shouldThrow<IllegalStateException> { first.erase(accepted, true) }.message shouldBe "C_RECOVERY_SHARE_INVALID"
        val replacement =
            reopened.replace(
                compromised,
                compromised.copy(keyAlias = "replacement-test", compromised = false),
                compromised.admin,
                true,
            )
        replacement.compromised shouldBe true
        shouldThrow<IllegalStateException> { reopened.erase(replacement, false) }.message shouldBe "C_RECOVERY_AUTH_REQUIRED"
        reopened.erase(replacement, true)
        reopened.read() shouldBe null
        reopened.instanceId shouldBe "88888888-8888-4888-8888-888888888888"
        val evidence = checkNotNull(reopened.publicEvidence(replacement.setId))
        evidence.compromised shouldBe true
        evidence.recordHash shouldBe PallaSyncCore.objectHash(replacement.record.toString())
        reopened.pendingKeyErasure() shouldBe setOf(accepted.keyAlias, replacement.keyAlias)
        reopened.keyErased(accepted.keyAlias)
        reopened.keyErased(replacement.keyAlias)
        val reimported = reopened.replace(null, candidate, candidate.admin, false)
        reimported.compromised shouldBe true
        reopened.erase(reimported, true)
        PallaSyncRecoverySlot(prefs).pendingKeyErasure() shouldBe setOf(reimported.keyAlias)
        shouldThrow<IllegalStateException> { reopened.replace(null, candidate, candidate.admin, false) }
            .message shouldBe "C_RECOVERY_WRAP_KEY_UNAVAILABLE"
    }

    @Test
    fun validConflictingStatusPreservesPackageAndPublicForkEvidence() {
        val holder = slot("recovery-slot-fork-unit")
        val candidate = entry()
        val accepted = holder.replace(null, candidate, candidate.admin, false)
        val packageObject = accepted.packageObject
        val prepared = packageObject.getValue("status_chain").jsonArray.first()
        val fork = Json.parseToJsonElement(source("python-recovery-c8-retired-status.json")).jsonObject
        val competing = JsonObject(packageObject + ("status_chain" to JsonArray(listOf(prepared, fork))))
        shouldThrow<IllegalStateException> { holder.updateStatus(accepted, competing.toString()) }.message shouldBe
            "C_RECOVERY_SHARE_INVALID"
        val retained = checkNotNull(holder.read())
        retained.packageJson shouldBe accepted.packageJson
        retained.keyAlias shouldBe accepted.keyAlias
        retained.conflictingStatusChain shouldBe competing.getValue("status_chain").toString()
        retained.toString() shouldBe "PallaSync protected recovery holder"
    }

    @Test
    fun unsignedStatusCannotReplaceOrQuarantineKnownGoodArtifact() {
        val holder = slot("recovery-slot-invalid-unit")
        val candidate = entry()
        val accepted = holder.replace(null, candidate, candidate.admin, false)
        val unsigned = accepted.packageJson.replace("\"state\":\"active\"", "\"state\":\"compromised\"")
        shouldThrow<IllegalStateException> { holder.updateStatus(accepted, unsigned) }
        holder.read() shouldBe accepted
    }

    @Test
    fun everySignedForkSurvivesEraseAndReimportWithoutRestoringOrdinaryRecovery() {
        val name = "recovery-slot-multiple-forks-unit"
        val holder = slot(name)
        val candidate = entry()
        val accepted = holder.replace(null, candidate, candidate.admin, false)
        val forks = recoveryStatusForks(accepted.packageJson)
        (forks + forks.first()).forEach { statuses ->
            val current = checkNotNull(holder.read())
            val competing = JsonObject(current.packageObject + ("status_chain" to Json.parseToJsonElement(statuses)))
            shouldThrow<IllegalStateException> { holder.updateStatus(current, competing.toString()) }
                .message shouldBe "C_RECOVERY_SHARE_INVALID"
        }
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(name, Context.MODE_PRIVATE)
        val reopened = PallaSyncRecoverySlot(prefs)
        val retained = checkNotNull(reopened.read())
        retained.packageJson shouldBe accepted.packageJson
        retained.forkEvidence shouldBe forks
        reopened.erase(retained, true)
        checkNotNull(reopened.publicEvidence(retained.setId)).forkEvidence shouldBe forks
        reopened.keyErased(retained.keyAlias)
        val reimported = reopened.replace(null, candidate, candidate.admin, false)
        reimported.forkEvidence shouldBe forks
        checkNotNull(reopened.publicEvidence(retained.setId)).forkEvidence shouldBe forks
    }

    @Test
    fun compromisedOwnPieceCanOnlyEnterExplicitNewRootEmergencyCollection() {
        PallaSyncCore.setSecretForeground(true)
        val packageJson = entry().packageJson
        val packageObject = Json.parseToJsonElement(packageJson).jsonObject
        val manifest = packageObject.getValue("manifest").toString()
        val statuses = packageObject.getValue("status_chain").toString()
        val admin = entry().admin
        val holder = "88888888-8888-4888-8888-888888888888"
        val ordinary =
            PallaSyncCore.beginRecoveryCollection(
                manifest,
                statuses,
                admin,
                null,
                java.util.UUID
                    .randomUUID()
                    .toString(),
                "enrollment",
                0,
                true,
                false,
                false,
            )
        val key = ByteArray(32) { (it + 0x60).toByte() } // C.8's public fixed test RSWK only.
        shouldThrow<IllegalStateException> {
            PallaSyncCore.collectOwnRecoveryShare(ordinary, packageJson, key, holder, true, true, true)
        }.message shouldBe "C_RECOVERY_SHARE_COMPROMISED"
        key.all { it == 0.toByte() } shouldBe true
        shouldThrow<IllegalStateException> { PallaSyncCore.finishRecoveryCollection(ordinary, true) }
            .message shouldBe "C_RECOVERY_SESSION_EXPIRED"
        val emergency =
            PallaSyncCore.beginRecoveryCollection(
                manifest,
                statuses,
                admin,
                null,
                java.util.UUID
                    .randomUUID()
                    .toString(),
                "new_root_migration",
                0,
                true,
                true,
                true,
            )
        try {
            val unlocked = ByteArray(32) { (it + 0x60).toByte() }
            val result =
                Json
                    .parseToJsonElement(
                        PallaSyncCore.collectOwnRecoveryShare(emergency, packageJson, unlocked, holder, true, true, true),
                    ).jsonObject
            result.getValue("distinct_shares").toString() shouldBe "1"
            unlocked.all { it == 0.toByte() } shouldBe true
            shouldThrow<IllegalStateException> { PallaSyncCore.finishRecoveryCollection(emergency, true) }
                .message shouldBe "C_RECOVERY_THRESHOLD_NOT_MET"
        } finally {
            PallaSyncCore.endRecoveryCollection(emergency)
        }
    }
}
