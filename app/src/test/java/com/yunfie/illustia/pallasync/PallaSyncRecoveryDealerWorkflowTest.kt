package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

/** Actual JNI dealer/reader/collector stages; RAM packages simulate readback, not OS/physical media. */
@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PallaSyncRecoveryDealerWorkflowTest {
    private val phrase = (List(23) { "abandon" } + "art").joinToString(" ")

    private fun create(name: String): Pair<PallaSyncRecoveryDealerWorkflow, PallaSyncRecoverySetStore> {
        PallaSyncCore.setSecretForeground(true)
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = PallaSyncRecoverySetStore(prefs)
        val identity = PallaSyncRootSession.beginText(phrase, "", PallaSyncRootSession.Operation.RECOVERY_DISABLE).use { it.identity }
        val configuration =
            PallaSyncRecoveryConfiguration(
                2,
                List(3) { PallaSyncRecoveryConfiguration.Holder(UUID.randomUUID().toString(), null) },
                true,
                false,
            )
        val owner = PallaSyncRecoverySetupOwner(store)
        owner.prepare(
            PallaSyncRecoveryChainContext(identity.chainId, identity.adminPublicKey, 0, UUID.randomUUID().toString()),
            configuration,
            phrase,
            "",
            1,
        )
        return PallaSyncRecoveryDealerWorkflow(owner) to store
    }

    @Test
    fun allReadbacksAndSeparateConfirmedThresholdTestPrecedeActivation() {
        val (flow, store) = create("dealer-workflow-complete-unit")
        flow.use {
            val packages = List(3) { index -> distribute(flow, index) }
            flow.evidence.usable shouldBe 3
            flow.evidence.state shouldBe "prepared"
            flow.beginThresholdTest(true)
            repeat(2) { index -> collect(flow, packages[index], index) }
            flow.distinctShares shouldBe 2
            flow.finishThresholdTest(true)
            flow.thresholdTestPassed shouldBe true
            Json
                .parseToJsonElement(flow.activate(2))
                .jsonArray
                .last()
                .jsonObject
                .getValue("state")
                .jsonPrimitive.content shouldBe "active"
            flow.stage shouldBe PallaSyncRecoveryDealerWorkflow.Stage.ACTIVE
        }
        checkNotNull(store.selected()).interrupted shouldBe false
        checkNotNull(store.selected()).state shouldBe "active"
    }

    @Test
    fun unconfirmedSasAbortsWithoutDistributionAndCannotReviveSameSession() {
        val (flow, store) = create("dealer-workflow-unconfirmed-unit")
        flow.use {
            val descriptor = flow.beginDistribution(1)
            responder(flow, descriptor).use { reader ->
                val sas = flow.bind(reader.descriptorJson)
                PallaSyncCore.bindRecoveryTransfer(reader.handle, descriptor) shouldBe sas
                shouldThrow<IllegalStateException> { flow.confirm(false) }.message shouldBe "C_RECOVERY_AUTH_REQUIRED"
                flow.stage shouldBe PallaSyncRecoveryDealerWorkflow.Stage.CLOSED
                flow.descriptor shouldBe null
                shouldThrow<IllegalStateException> { flow.confirm(true) }.message shouldBe "C_RECOVERY_SESSION_EXPIRED"
            }
        }
        checkNotNull(store.selected()).usable shouldBe 0
        checkNotNull(store.selected()).interrupted shouldBe true
    }

    @Test
    fun replacementActivatesOnlyAfterReadbackAndThresholdThenRetiresThePinnedPreviousSet() {
        val (initialFlow, store) = create("dealer-workflow-replacement-unit")
        val initialPackages = List(3) { index -> distribute(initialFlow, index) }
        initialFlow.beginThresholdTest(true)
        repeat(2) { index -> collect(initialFlow, initialPackages[index], index) }
        initialFlow.finishThresholdTest(true)
        initialFlow.activate(2)
        val previous = checkNotNull(store.read(initialFlow.evidence.id))
        previous.state shouldBe "active"

        val context = ApplicationProvider.getApplicationContext<Context>()
        val identity = PallaSyncRootSession.beginText(phrase, "", PallaSyncRootSession.Operation.RECOVERY_ROTATE).use { it.identity }
        val configuration =
            PallaSyncRecoveryConfiguration(
                2,
                List(3) { PallaSyncRecoveryConfiguration.Holder(UUID.randomUUID().toString(), null) },
                true,
                false,
            )
        val owner = PallaSyncRecoverySetupOwner(store, previous)
        owner.prepare(
            PallaSyncRecoveryChainContext(identity.chainId, identity.adminPublicKey, 0, UUID.randomUUID().toString()),
            configuration,
            phrase,
            "",
            3,
        )
        val replacement = PallaSyncRecoveryDealerWorkflow(owner)
        val replacementPackages = List(3) { index -> distribute(replacement, index) }
        replacement.beginThresholdTest(true)
        repeat(2) { index -> collect(replacement, replacementPackages[index], index) }
        replacement.finishThresholdTest(true)
        val replacementId = replacement.evidence.id
        owner.usePrepared { it.activate(4) }

        val activatedButUnfinished = checkNotNull(store.read(replacementId))
        activatedButUnfinished.state shouldBe "active"
        activatedButUnfinished.replacementPending shouldBe true
        checkNotNull(store.read(previous.id)).state shouldBe "active"
        replacement.close()
        owner.close()

        val resumed =
            PallaSyncRootSession.beginText(phrase, "", PallaSyncRootSession.Operation.RECOVERY_ROTATE).use { root ->
                store.finishReplacement(activatedButUnfinished, root, 5)
            }

        val retired = checkNotNull(store.read(previous.id))
        retired.state shouldBe "retired"
        retired.statuses
            .last()
            .jsonObject
            .getValue("replacement_set_id")
            .jsonPrimitive.content shouldBe replacementId
        resumed.state shouldBe "active"
        resumed.replacementPending shouldBe false
        retired.usable shouldBe 3
        resumed.usable shouldBe 3
        initialFlow.close()
    }

    private fun responder(
        flow: PallaSyncRecoveryDealerWorkflow,
        descriptor: String,
    ): PallaSyncRecoveryCandidate {
        val parsed = Json.parseToJsonElement(descriptor).jsonObject
        return PallaSyncCore.beginRecoveryTransfer(
            flow.evidence.manifestJson,
            flow.evidence.admin,
            parsed.getValue("ceremony_id").jsonPrimitive.content,
            parsed.getValue("session_id").jsonPrimitive.content,
            "responder",
            "reader",
            flow.purpose,
        )
    }

    private fun distribute(
        flow: PallaSyncRecoveryDealerWorkflow,
        index: Int,
    ): String {
        val descriptor = flow.beginDistribution(index + 1)
        return responder(flow, descriptor).use { reader ->
            val sas = flow.bind(reader.descriptorJson)
            PallaSyncCore.bindRecoveryTransfer(reader.handle, descriptor) shouldBe sas
            PallaSyncCore.confirmRecoveryTransfer(reader.handle, sas, true)
            val packet = checkNotNull(flow.confirm(true))
            val instance = flow.evidence.holders[index].instanceId
            val proposal =
                PallaSyncCore.receiveRecoveryHolder(
                    reader.handle,
                    packet,
                    instance,
                    null,
                    "passphrase-wrapped",
                    true,
                    false,
                    false,
                    1,
                )
            val signed = checkNotNull(flow.receive(proposal))
            flow.receive(proposal) shouldBe signed
            PallaSyncCore.receiveRecoveryHolder(reader.handle, signed, instance, null, "passphrase-wrapped", true, false, false, 1)
            val artifact =
                PallaSyncCore.recoveryHolderPackage(
                    reader.handle,
                    flow.evidence.statusesJson,
                    strictRootUtf8("test credential $index"),
                )
            val key = PallaSyncCore.unlockOfflineRecoveryKey(artifact, strictRootUtf8("test credential $index"))
            val ack = PallaSyncCore.recoveryHolderPersisted(reader.handle, artifact, key)
            key.all { it == 0.toByte() } shouldBe true
            flow.receive(ack) shouldBe null
            flow.stage shouldBe PallaSyncRecoveryDealerWorkflow.Stage.READY
            artifact
        }
    }

    private fun collect(
        flow: PallaSyncRecoveryDealerWorkflow,
        artifact: String,
        index: Int,
    ) {
        val descriptor = flow.beginCollectionPair(true)
        responder(flow, descriptor).use { reader ->
            val sas = flow.bind(reader.descriptorJson)
            PallaSyncCore.bindRecoveryTransfer(reader.handle, descriptor) shouldBe sas
            PallaSyncCore.confirmRecoveryTransfer(reader.handle, sas, true)
            flow.confirm(true) shouldBe null
            val key = PallaSyncCore.unlockOfflineRecoveryKey(artifact, strictRootUtf8("test credential $index"))
            val packet = PallaSyncCore.releaseRecoveryShare(reader.handle, artifact, key, true, true, false, false)
            key.all { it == 0.toByte() } shouldBe true
            flow.receive(packet) shouldBe null
        }
    }
}
