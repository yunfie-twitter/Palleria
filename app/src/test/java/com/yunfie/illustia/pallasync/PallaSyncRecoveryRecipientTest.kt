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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Real native stages with simulated local media; no physical independence/OS/SAF claim. */
@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PallaSyncRecoveryRecipientTest {
    private val phrase = (List(23) { "abandon" } + "art").joinToString(" ")

    private fun dealer(name: String): PallaSyncRecoveryDealerWorkflow {
        PallaSyncCore.setSecretForeground(true)
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val identity = PallaSyncRootSession.beginText(phrase, "", PallaSyncRootSession.Operation.RECOVERY_DISABLE).use { it.identity }
        val owner = PallaSyncRecoverySetupOwner(PallaSyncRecoverySetStore(prefs))
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
        return PallaSyncRecoveryDealerWorkflow(owner)
    }

    private fun chain(flow: PallaSyncRecoveryDealerWorkflow): PallaSyncRecoveryChainContext =
        PallaSyncRecoveryChainContext(
            flow.evidence.manifest
                .getValue("chain_id")
                .jsonPrimitive.content,
            flow.evidence.admin,
            0,
            UUID.randomUUID().toString(),
        )

    private fun recipient(
        flow: PallaSyncRecoveryDealerWorkflow,
        descriptor: String,
        index: Int = 0,
    ): PallaSyncRecoveryRecipient =
        PallaSyncRecoveryRecipient.begin(
            chain(flow),
            flow.evidence.manifestJson,
            flow.evidence.statusesJson,
            descriptor,
            flow.evidence.holders[index].instanceId,
        )

    @Test
    fun offlineReadbackUsesSeparateCredentialAndExactAckSurvivesEndingSecretPair() {
        dealer("recovery-recipient-readback-unit").use { flow ->
            val descriptor = flow.beginDistribution(1)
            recipient(flow, descriptor).use { receiver ->
                flow.bind(receiver.descriptor) shouldBe receiver.visual
                receiver.confirm(true)
                val distribution = checkNotNull(flow.confirm(true))
                val proposal = checkNotNull(receiver.receive(distribution, 1))
                receiver.receive(distribution, 1) shouldBe proposal
                val signed = checkNotNull(flow.receive(proposal))
                receiver.receive(signed, 1) shouldBe null
                receiver.receive(signed, 1) shouldBe null
                receiver.stage shouldBe PallaSyncRecoveryRecipient.Stage.READY_TO_PERSIST
                val credential = strictRootUtf8("distinct test-only offline credential")
                val artifact = receiver.prepareOfflinePackage(credential)
                credential.all { it == 0.toByte() } shouldBe true
                val output = ByteArrayOutputStream()
                PallaSyncRecoveryOfflineMedia.write(output, artifact, flow.evidence.admin, flow.evidence.head)
                val readback =
                    PallaSyncRecoveryOfflineMedia.read(
                        ByteArrayInputStream(output.toByteArray()),
                        flow.evidence.admin,
                        flow.evidence.head,
                    )
                val readCredential = strictRootUtf8("distinct test-only offline credential")
                val packet = receiver.acknowledgeOfflineReadback(readback, readCredential)
                readCredential.all { it == 0.toByte() } shouldBe true
                receiver.stage shouldBe PallaSyncRecoveryRecipient.Stage.PERSISTED
                receiver.persistedAcknowledgement() shouldBe packet
                receiver.persistedAcknowledgement() shouldBe packet
                receiver.receive(distribution, 1) shouldBe proposal
                receiver.receive(signed, 1) shouldBe null
                flow.receive(packet) shouldBe null
                flow.evidence.usable shouldBe 1
                flow.evidence.state shouldBe "prepared"
                flow.evidence.ordinaryRecoveryAvailable shouldBe false
            }
        }
    }

    @Test
    fun unconfirmedRecipientAndWrongPinnedChainRefuseBeforeShareAcceptance() {
        dealer("recovery-recipient-consent-unit").use { flow ->
            val descriptor = flow.beginDistribution(1)
            shouldThrow<IllegalStateException> {
                PallaSyncRecoveryRecipient.begin(
                    chain(flow).copy(chainId = "different"),
                    flow.evidence.manifestJson,
                    flow.evidence.statusesJson,
                    descriptor,
                    flow.evidence.holders
                        .first()
                        .instanceId,
                )
            }.message shouldBe "C_RECOVERY_SET_MISMATCH"
            recipient(flow, descriptor).use { receiver ->
                flow.bind(receiver.descriptor) shouldBe receiver.visual
                shouldThrow<IllegalStateException> { receiver.confirm(false) }.message shouldBe "C_RECOVERY_AUTH_REQUIRED"
                receiver.stage shouldBe PallaSyncRecoveryRecipient.Stage.CLOSED
            }
            flow.evidence.usable shouldBe 0
        }
    }

    @Test
    fun changedBytesAtASignedShareSequenceAbortAfterTheStageAdvanced() {
        dealer("recovery-recipient-replay-conflict-unit").use { flow ->
            val descriptor = flow.beginDistribution(1)
            recipient(flow, descriptor).use { receiver ->
                flow.bind(receiver.descriptor) shouldBe receiver.visual
                receiver.confirm(true)
                val proposal = checkNotNull(receiver.receive(checkNotNull(flow.confirm(true)), 1))
                val signed = checkNotNull(flow.receive(proposal))
                receiver.receive(signed, 1) shouldBe null
                receiver.receive(signed, 1) shouldBe null
                shouldThrow<IllegalStateException> { receiver.receive(signed + " ", 1) }.message shouldBe "C_RECOVERY_SHARE_INVALID"
                receiver.stage shouldBe PallaSyncRecoveryRecipient.Stage.CLOSED
                flow.evidence.usable shouldBe 0
            }
        }
    }

    @Test
    fun activeSetIsNotAProposalForFreshDistribution() {
        val artifact =
            checkNotNull(javaClass.getResourceAsStream("/pallasync/python-recovery-c8-package.json"))
                .use { Json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject }
        val manifest = artifact.getValue("manifest").jsonObject
        val pinned =
            PallaSyncRecoveryChainContext(
                manifest.getValue("chain_id").jsonPrimitive.content,
                manifest.getValue("admin_public_key").jsonPrimitive.content,
                0,
                UUID.randomUUID().toString(),
            )
        shouldThrow<IllegalStateException> {
            PallaSyncRecoveryRecipient.begin(
                pinned,
                manifest.toString(),
                artifact.getValue("status_chain").toString(),
                "{}",
                manifest
                    .getValue("holders")
                    .jsonArray
                    .first()
                    .jsonObject
                    .getValue("holder_instance_id")
                    .jsonPrimitive.content,
            )
        }.message shouldBe "C_RECOVERY_ROTATION_INCOMPLETE"
    }

    @Test
    fun receivingOwnerRetainsCancellationOwnershipAndRejectsWrongPinnedGeneration() {
        dealer("recovery-recipient-owner-unit").use { flow ->
            val descriptor = flow.beginDistribution(1)
            val instance =
                flow.evidence.holders
                    .first()
                    .instanceId
            PallaSyncRecoveryRecipientOwner().use { owner ->
                shouldThrow<IllegalStateException> {
                    owner.begin(
                        chain(flow).copy(generation = 1),
                        flow.evidence.manifestJson,
                        flow.evidence.statusesJson,
                        descriptor,
                        instance,
                    )
                }.message shouldBe "C_RECOVERY_SET_MISMATCH"
                owner.begin(chain(flow), flow.evidence.manifestJson, flow.evidence.statusesJson, descriptor, instance)
                val candidate = owner.current()
                owner.protection shouldBe "passphrase-wrapped"
                candidate.stage shouldBe PallaSyncRecoveryRecipient.Stage.SAS
                owner.close()
                candidate.stage shouldBe PallaSyncRecoveryRecipient.Stage.CLOSED
                shouldThrow<IllegalStateException> { owner.current() }.message shouldBe "C_RECOVERY_SESSION_EXPIRED"
                shouldThrow<IllegalStateException> {
                    owner.begin(chain(flow), flow.evidence.manifestJson, flow.evidence.statusesJson, descriptor, instance)
                }.message shouldBe "C_RECOVERY_SESSION_EXPIRED"
            }
            flow.evidence.usable shouldBe 0
        }
    }

    private fun packages(flow: PallaSyncRecoveryDealerWorkflow): List<String> =
        List(3) { index ->
            val descriptor = flow.beginDistribution(index + 1)
            recipient(flow, descriptor, index).use { receiver ->
                flow.bind(receiver.descriptor) shouldBe receiver.visual
                receiver.confirm(true)
                val proposal = checkNotNull(receiver.receive(checkNotNull(flow.confirm(true)), 1))
                receiver.receive(checkNotNull(flow.receive(proposal)), 1) shouldBe null
                val artifact = receiver.prepareOfflinePackage(strictRootUtf8("test-only distinct offline credential"))
                val ack = receiver.acknowledgeOfflineReadback(artifact, strictRootUtf8("test-only distinct offline credential"))
                flow.receive(ack) shouldBe null
                artifact
            }
        }

    @Test
    fun independentReaderReleasesOnePieceOnlyAfterLiveConsentAndRetainsExactEncryptedRetry() {
        dealer("recovery-reader-release-unit").use { flow ->
            val artifacts = packages(flow)
            flow.beginThresholdTest(true)
            val peer = flow.beginCollectionPair(true)
            PallaSyncRecoveryReleaseOwner().use { reader ->
                reader.begin(chain(flow).copy(generation = 1), artifacts.first(), peer, true, flow.evidence, false, false)
                flow.bind(checkNotNull(reader.descriptor)) shouldBe reader.visual
                reader.confirm(true)
                flow.confirm(true) shouldBe null
                val credential = strictRootUtf8("test-only distinct offline credential")
                val original = reader.releaseOffline(credential, true)
                credential.all { it == 0.toByte() } shouldBe true
                reader.stage shouldBe PallaSyncRecoveryReleaseOwner.Stage.RELEASED
                reader.originalPacket() shouldBe original
                reader.originalPacket() shouldBe original
                flow.receive(original) shouldBe null
                flow.distinctShares shouldBe 1
                flow.thresholdTestPassed shouldBe false
            }
        }
    }

    @Test
    fun readerRiskConsentAndBadUnlockNeverEmitAPiece() {
        dealer("recovery-reader-refusal-unit").use { flow ->
            val artifacts = packages(flow)
            flow.beginThresholdTest(true)
            val peer = flow.beginCollectionPair(true)
            PallaSyncRecoveryReleaseOwner().use { reader ->
                shouldThrow<IllegalStateException> {
                    reader.begin(chain(flow), artifacts.first(), peer, true, flow.evidence, true, false)
                }.message shouldBe "C_RECOVERY_AUTH_REQUIRED"
                reader.stage shouldBe PallaSyncRecoveryReleaseOwner.Stage.CLOSED
            }
            PallaSyncRecoveryReleaseOwner().use { reader ->
                reader.begin(chain(flow), artifacts.first(), peer, true, flow.evidence, true, true)
                flow.bind(checkNotNull(reader.descriptor)) shouldBe reader.visual
                reader.confirm(true)
                flow.confirm(true) shouldBe null
                val credential = strictRootUtf8("wrong test-only credential")
                shouldThrow<IllegalStateException> { reader.releaseOffline(credential, true) }
                credential.all { it == 0.toByte() } shouldBe true
                reader.stage shouldBe PallaSyncRecoveryReleaseOwner.Stage.CLOSED
                shouldThrow<IllegalStateException> { reader.originalPacket() }.message shouldBe "C_RECOVERY_AUTH_REQUIRED"
                flow.distinctShares shouldBe 0
            }
        }
    }
}
