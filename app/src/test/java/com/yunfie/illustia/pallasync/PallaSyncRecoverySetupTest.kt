package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

private val setupTestPhrase = (List(23) { "abandon" } + "art").joinToString(" ")

/** Real JNI crypto/stage orchestration with simulated stored media; no physical-independence/OS claim. */
@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PallaSyncRecoverySetupTest {
    private fun store(name: String): PallaSyncRecoverySetStore {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return PallaSyncRecoverySetStore(prefs)
    }

    private fun holders(): String =
        JsonArray(
            List(3) {
                buildJsonObject {
                    put("holder_kind", "offline")
                    put("holder_device_id", JsonNull)
                    put("holder_instance_id", UUID.randomUUID().toString())
                }
            },
        ).toString()

    @Test
    fun dealerReadbacksThresholdActivationAndLossCountsRetainOnlyPublicEvidence() {
        PallaSyncCore.setSecretForeground(true)
        val store = store("recovery-setup-flow-unit")
        val root = PallaSyncRootSession.beginText(setupTestPhrase, "", PallaSyncRootSession.Operation.RECOVERY_CREATE)
        val handle = root.handle
        val packages = mutableListOf<String>()
        PallaSyncRecoverySetup.create(root, store, 0, 2, holders(), 1).use { setup ->
            val manifest = Json.parseToJsonElement(setup.manifestJson).jsonObject
            val admin = setup.evidence.admin
            repeat(3) { index -> packages.add(distributeOffline(setup, index)) }
            setup.evidence.usable shouldBe 3
            setup.evidence.ordinaryRecoveryAvailable shouldBe false
            setup.beginThresholdTest(true).use { collection ->
                repeat(2) { index -> collect(setup, collection, packages[index], index) }
                collection.finish(true).use { it.thresholdTestPassed shouldBe true }
            }
            setup.activate(2)
            setup.evidence.ordinaryRecoveryAvailable shouldBe true
        }
        shouldThrow<IllegalStateException> { PallaSyncCore.rootIdentity(handle) }.message shouldBe "C_RECOVERY_AUTH_REQUIRED"
        val selected = checkNotNull(store.selected())
        selected.state shouldBe "active"
        selected.usable shouldBe 3
        val once = store.lost(selected, selected.holders[0].shareId)
        once.usable shouldBe 2
        once.ordinaryRecoveryAvailable shouldBe true
        val twice = store.lost(once, once.holders[1].shareId)
        twice.usable shouldBe 1
        twice.ordinaryRecoveryAvailable shouldBe false
        val compromised = store.compromised(twice)
        compromised.compromised shouldBe true
        compromised.usable shouldBe 1
        assertNoSecrets("recovery-setup-flow-unit")
    }

    @Test
    fun cancelledPreparationCannotResumeSecretStateAndDoesNotClaimRecoveryAvailability() {
        PallaSyncCore.setSecretForeground(true)
        val store = store("recovery-setup-cancel-unit")
        val root = PallaSyncRootSession.beginText(setupTestPhrase, "", PallaSyncRootSession.Operation.RECOVERY_CREATE)
        val setup = PallaSyncRecoverySetup.create(root, store, 0, 2, holders(), 1)
        setup.close()
        checkNotNull(store.selected()).interrupted shouldBe true
        checkNotNull(store.selected()).ordinaryRecoveryAvailable shouldBe false
        shouldThrow<IllegalStateException> { setup.beginTransfer(1) }.message shouldBe "C_RECOVERY_SESSION_EXPIRED"
        shouldThrow<IllegalStateException> { PallaSyncCore.rootIdentity(root.handle) }.message shouldBe "C_RECOVERY_AUTH_REQUIRED"
    }

    private fun distributeOffline(
        setup: PallaSyncRecoverySetup,
        index: Int,
    ): String {
        val transfer = setup.beginTransfer(index + 1)
        val session =
            Json
                .parseToJsonElement(transfer.pair.descriptorJson)
                .jsonObject
                .getValue("session_id")
                .jsonPrimitive.content
        return PallaSyncCore
            .beginRecoveryTransfer(
                setup.manifestJson,
                setup.evidence.admin,
                setup.ceremonyId,
                session,
                "responder",
                "reader",
                "create",
            ).use { reader ->
                val sas = setup.bind(transfer, reader.descriptorJson)
                PallaSyncCore.bindRecoveryTransfer(reader.handle, transfer.pair.descriptorJson) shouldBe sas
                setup.confirm(transfer, sas, true)
                PallaSyncCore.confirmRecoveryTransfer(reader.handle, sas, true)
                val distribution = setup.distribute(transfer)
                setup.distribute(transfer) shouldBe distribution
                val artifact = wrapOffline(setup, reader, transfer, distribution, index)
                persistOffline(setup, reader, transfer, artifact, index)
            }
    }

    private fun wrapOffline(
        setup: PallaSyncRecoverySetup,
        reader: PallaSyncRecoveryCandidate,
        transfer: PallaSyncRecoverySetup.Transfer,
        distribution: String,
        index: Int,
    ): String {
        val instance =
            setup.evidence.manifest
                .getValue(
                    "holders",
                ).jsonArray[index]
                .jsonObject
                .getValue("holder_instance_id")
                .jsonPrimitive.content
        val proposal =
            PallaSyncCore.receiveRecoveryHolder(
                reader.handle,
                distribution,
                instance,
                null,
                "passphrase-wrapped",
                true,
                false,
                false,
                1,
            )
        val signed = setup.receive(transfer, proposal)
        PallaSyncCore.receiveRecoveryHolder(reader.handle, signed, instance, null, "passphrase-wrapped", true, false, false, 1)
        val credential = strictRootUtf8("independent test credential $index")
        val artifact = PallaSyncCore.recoveryHolderPackage(reader.handle, setup.evidence.statusesJson, credential)
        credential.all { it == 0.toByte() } shouldBe true
        return artifact
    }

    private fun persistOffline(
        setup: PallaSyncRecoverySetup,
        reader: PallaSyncRecoveryCandidate,
        transfer: PallaSyncRecoverySetup.Transfer,
        artifact: String,
        index: Int,
    ): String {
        val output = ByteArrayOutputStream()
        PallaSyncRecoveryOfflineMedia.write(output, artifact, setup.evidence.admin, setup.evidence.head)
        val readback =
            PallaSyncRecoveryOfflineMedia.read(
                ByteArrayInputStream(output.toByteArray()),
                setup.evidence.admin,
                setup.evidence.head,
            )
        val key = PallaSyncCore.unlockOfflineRecoveryKey(readback, strictRootUtf8("independent test credential $index"))
        val packet = PallaSyncCore.recoveryHolderPersisted(reader.handle, readback, key)
        key.all { it == 0.toByte() } shouldBe true
        val acknowledged = setup.receive(transfer, packet)
        setup.receive(transfer, packet) shouldBe acknowledged
        return readback
    }

    private fun assertNoSecrets(name: String) {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(name, Context.MODE_PRIVATE)
        val serialized = prefs.all.values.joinToString("\n")
        listOf("encrypted_blob", "encrypted_share", "offline_key_envelope", "mnemonic", "passphrase", "encrypted_key").forEach { field ->
            serialized.contains("\"$field\"") shouldBe false
        }
    }

    private fun collect(
        setup: PallaSyncRecoverySetup,
        collection: PallaSyncRecoveryCollection,
        artifact: String,
        index: Int,
    ) {
        val sessionId = UUID.randomUUID().toString()
        val admin = setup.evidence.admin
        PallaSyncCore
            .beginRecoveryTransfer(
                setup.manifestJson,
                admin,
                collection.ceremonyId,
                sessionId,
                "initiator",
                "reader",
                "create",
            ).use { reader ->
                PallaSyncCore
                    .beginRecoveryTransfer(
                        setup.manifestJson,
                        admin,
                        collection.ceremonyId,
                        sessionId,
                        "responder",
                        "collector",
                        "create",
                    ).use { receiver ->
                        val sas = PallaSyncCore.bindRecoveryTransfer(reader.handle, receiver.descriptorJson)
                        PallaSyncCore.bindRecoveryTransfer(receiver.handle, reader.descriptorJson) shouldBe sas
                        PallaSyncCore.confirmRecoveryTransfer(reader.handle, sas, true)
                        PallaSyncCore.confirmRecoveryTransfer(receiver.handle, sas, true)
                        val key = PallaSyncCore.unlockOfflineRecoveryKey(artifact, strictRootUtf8("independent test credential $index"))
                        val packet = PallaSyncCore.releaseRecoveryShare(reader.handle, artifact, key, true, true, false, false)
                        key.all { it == 0.toByte() } shouldBe true
                        collection.receive(receiver, packet)
                    }
            }
    }
}
