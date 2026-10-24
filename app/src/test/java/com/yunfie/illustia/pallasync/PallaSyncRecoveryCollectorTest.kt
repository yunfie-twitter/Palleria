package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

/** Actual JNI ownership/stages with RAM-only simulated independent media, not complete three-party IT. */
@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PallaSyncRecoveryCollectorTest {
    private val credential = "public test-only offline credential"

    private fun chain(entry: PallaSyncRecoverySetStore.Entry) =
        PallaSyncRecoveryChainContext(
            entry.manifest
                .getValue("chain_id")
                .jsonPrimitive.content,
            entry.admin,
            1,
            UUID.randomUUID().toString(),
        )

    private fun withActiveSet(
        name: String,
        action: (PallaSyncRecoverySetStore, PallaSyncRecoverySetStore.Entry, List<String>) -> Unit,
    ) {
        PallaSyncCore.setSecretForeground(true)
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = PallaSyncRecoverySetStore(prefs)
        val phrase = (List(23) { "abandon" } + "art").joinToString(" ")
        val identity = PallaSyncRootSession.beginText(phrase, "", PallaSyncRootSession.Operation.RECOVERY_DISABLE).use { it.identity }
        val owner = PallaSyncRecoverySetupOwner(store)
        owner.prepare(
            PallaSyncRecoveryChainContext(identity.chainId, identity.adminPublicKey, 0, UUID.randomUUID().toString()),
            PallaSyncRecoveryConfiguration(
                2,
                List(3) { PallaSyncRecoveryConfiguration.Holder(UUID.randomUUID().toString(), null) },
                true,
                false,
            ),
            phrase,
            "",
            1,
        )
        PallaSyncRecoveryDealerWorkflow(owner).use { dealer ->
            val packages = List(3) { index -> distribute(dealer, index) }
            dealer.beginThresholdTest(true)
            repeat(2) { index ->
                val descriptor = dealer.beginCollectionPair(true)
                PallaSyncRecoveryReleaseOwner().use { reader ->
                    reader.begin(chain(dealer.evidence), packages[index], descriptor, true, dealer.evidence, false, false)
                    dealer.bind(checkNotNull(reader.descriptor)) shouldBe reader.visual
                    reader.confirm(true)
                    dealer.confirm(true) shouldBe null
                    dealer.receive(reader.releaseOffline(strictRootUtf8(credential), true)) shouldBe null
                }
            }
            dealer.finishThresholdTest(true)
            dealer.activate(System.currentTimeMillis())
            val active = dealer.evidence
            val updated =
                packages.map { original ->
                    JsonObject(
                        Json.parseToJsonElement(original).jsonObject + ("status_chain" to Json.parseToJsonElement(active.statusesJson)),
                    ).toString()
                }
            action(store, active, updated)
        }
    }

    private fun distribute(
        dealer: PallaSyncRecoveryDealerWorkflow,
        index: Int,
    ): String {
        val descriptor = dealer.beginDistribution(index + 1)
        return PallaSyncRecoveryRecipient
            .begin(
                chain(dealer.evidence).copy(generation = 0),
                dealer.evidence.manifestJson,
                dealer.evidence.statusesJson,
                descriptor,
                dealer.evidence.holders[index].instanceId,
            ).use { receiver ->
                dealer.bind(receiver.descriptor) shouldBe receiver.visual
                receiver.confirm(true)
                val proposal = checkNotNull(receiver.receive(checkNotNull(dealer.confirm(true)), 1))
                receiver.receive(checkNotNull(dealer.receive(proposal)), 1) shouldBe null
                val packageJson = receiver.prepareOfflinePackage(strictRootUtf8(credential))
                dealer.receive(receiver.acknowledgeOfflineReadback(packageJson, strictRootUtf8(credential))) shouldBe null
                packageJson
            }
    }

    private fun collect(
        collector: PallaSyncRecoveryCollectorOwner,
        artifact: String,
    ): String {
        val descriptor = collector.beginPair(true)
        return PallaSyncRecoveryReleaseOwner().use { reader ->
            reader.begin(chain(collector.entry), artifact, descriptor, true, collector.entry, false, false)
            collector.bind(checkNotNull(reader.descriptor)) shouldBe reader.visual
            reader.confirm(true)
            collector.confirm(true)
            val packet = reader.releaseOffline(strictRootUtf8(credential), true)
            collector.receive(packet)
            packet
        }
    }

    @Test
    fun thresholdRecoversOnlyTheNamedNativeRootAndSignsRetirementWithoutSecretJson() =
        withActiveSet("recovery-collector-disable-unit") { store, entry, packages ->
            PallaSyncRecoveryCollectorOwner(entry, PallaSyncRootSession.Operation.RECOVERY_DISABLE).use { collector ->
                collector.begin(chain(entry), true)
                val first = collect(collector, packages[0])
                collector.distinctShares shouldBe 1
                collector.receive(first) shouldBe 1
                collect(collector, packages[1])
                collector.distinctShares shouldBe 2
                collector.receive(first) shouldBe 1
                collector.distinctShares shouldBe 2
                collector.finish(true)
                collector.stage shouldBe PallaSyncRecoveryCollectorOwner.Stage.RECOVERED
                val completion = collector.recovered()
                completion.thresholdTestPassed shouldBe false
                val root = completion.takeRoot(PallaSyncRootSession.Operation.RECOVERY_DISABLE, null) { System.currentTimeMillis() }
                root.use {
                    it.identity.chainId shouldBe chain(entry).chainId
                    it.identity.adminPublicKey shouldBe entry.admin
                    val retired = signRecoveryRetirement(it, store, entry)
                    retired.state shouldBe "retired"
                    PallaSyncCore.validateRecoverySetState(retired.manifestJson, retired.statusesJson, entry.admin, entry.head) shouldBe
                        retired.statusesJson
                }
                shouldThrow<IllegalStateException> { root.identity }
                store.read(entry.id)!!.state shouldBe "retired"
            }
        }

    @Test
    fun insufficientThresholdEndsTransientCollectionAndKeepsEveryProtectedArtifact() =
        withActiveSet("recovery-collector-threshold-unit") { store, entry, packages ->
            PallaSyncRecoveryCollectorOwner(entry, PallaSyncRootSession.Operation.ROTATE).use { collector ->
                collector.begin(chain(entry), true)
                collect(collector, packages[0])
                shouldThrow<IllegalStateException> { collector.finish(true) }.message shouldBe "C_RECOVERY_THRESHOLD_NOT_MET"
                collector.stage shouldBe PallaSyncRecoveryCollectorOwner.Stage.CLOSED
                shouldThrow<IllegalStateException> { collector.recovered() }.message shouldBe "C_RECOVERY_AUTH_REQUIRED"
                store.read(entry.id) shouldBe entry
                packages.forEach { PallaSyncCore.validateRecoveryPackage(it, entry.admin, entry.head).isNotEmpty() shouldBe true }
            }
        }

    @Test
    fun cancellationAfterCollectionRefusesResumptionAndPreservesPublicSet() =
        withActiveSet("recovery-collector-cancel-unit") { store, entry, packages ->
            val collector = PallaSyncRecoveryCollectorOwner(entry, PallaSyncRootSession.Operation.ROTATE)
            collector.begin(chain(entry), true)
            collect(collector, packages[0])
            collector.close()
            collector.stage shouldBe PallaSyncRecoveryCollectorOwner.Stage.CLOSED
            shouldThrow<IllegalStateException> { collector.finish(true) }.message shouldBe "C_RECOVERY_SESSION_EXPIRED"
            store.read(entry.id) shouldBe entry
        }

    @Test
    fun changedBytesAtAnAcceptedTransferOrSequenceAbortEvenAfterThePairEnded() =
        withActiveSet("recovery-collector-replay-conflict-unit") { store, entry, packages ->
            PallaSyncRecoveryCollectorOwner(entry, PallaSyncRootSession.Operation.ROTATE).use { collector ->
                collector.begin(chain(entry), true)
                val original = collect(collector, packages[0])
                collector.receive(original) shouldBe 1
                shouldThrow<IllegalStateException> { collector.receive(original + " ") }.message shouldBe "C_RECOVERY_SHARE_INVALID"
                collector.stage shouldBe PallaSyncRecoveryCollectorOwner.Stage.CLOSED
                store.read(entry.id) shouldBe entry
            }
        }
}
