package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Android JNI/protected public state. Reader peers and package readback are simulated in RAM,
 * so this verifies stages/ABI/cleanup, not physical independence, SAF or the full human ceremony.
 */
@RunWith(AndroidJUnit4::class)
class PallaSyncRecoveryDealerAndroidTest {
    @Test
    fun readbackThresholdAndSuccessKeepPublicStatusAfterSecretOwnerCloses() =
        withOwnedStore("pallasync-dealer-success-test") { store ->
            val owner = prepare(store)
            val flow = PallaSyncRecoveryDealerWorkflow(owner)
            flow.use {
                val packages = List(3) { index -> distribute(flow, index) }
                assertEquals(3, flow.evidence.usable)
                assertEquals("prepared", flow.evidence.state)
                flow.beginThresholdTest(true)
                repeat(2) { index -> collect(flow, packages[index], index) }
                assertEquals(2, flow.distinctShares)
                flow.finishThresholdTest(true)
                flow.activate(System.currentTimeMillis())
                assertEquals(PallaSyncRecoveryDealerWorkflow.Stage.ACTIVE, flow.stage)
                assertEquals("active", flow.evidence.state)
                expectCode("C_RECOVERY_SESSION_EXPIRED") { owner.usePrepared { it.userInteraction() } }
                assertEquals(flow.evidence, PallaSyncRecoverySetStore(storePrefs()).selected())
            }
            assertEquals("active", checkNotNull(store.selected()).state)
        }

    @Test
    fun unconfirmedScreenEndsThePairAndRetainsIncompleteEvidence() =
        withOwnedStore("pallasync-dealer-unconfirmed-test") { store ->
            val flow = PallaSyncRecoveryDealerWorkflow(prepare(store))
            flow.use {
                val descriptor = flow.beginDistribution(1)
                peer(flow, descriptor).use { reader ->
                    val sas = flow.bind(reader.descriptorJson)
                    assertEquals(sas, PallaSyncCore.bindRecoveryTransfer(reader.handle, descriptor))
                    expectCode("C_RECOVERY_AUTH_REQUIRED") { flow.confirm(false) }
                    assertEquals(PallaSyncRecoveryDealerWorkflow.Stage.CLOSED, flow.stage)
                    assertNull(flow.descriptor)
                }
            }
            assertTrue(checkNotNull(store.selected()).interrupted)
            assertEquals(0, checkNotNull(store.selected()).usable)
        }

    @Test
    fun collectedRootSignsRetirementAndEndsWithoutPrivateJsonOrSecretResumption() =
        withOwnedStore("pallasync-collected-root-retirement-test") { store ->
            val flow = PallaSyncRecoveryDealerWorkflow(prepare(store))
            flow.use {
                val packages = List(3) { index -> distribute(flow, index) }
                flow.beginThresholdTest(true)
                repeat(2) { index -> collect(flow, packages[index], index) }
                flow.finishThresholdTest(true)
                flow.activate(System.currentTimeMillis())
                val active = flow.evidence
                val chain =
                    PallaSyncRecoveryChainContext(
                        active.manifest
                            .getValue("chain_id")
                            .jsonPrimitive.content,
                        active.admin,
                        1,
                        UUID.randomUUID().toString(),
                    )
                PallaSyncRecoveryCollectorOwner(active, PallaSyncRootSession.Operation.RECOVERY_DISABLE).use { collector ->
                    collector.begin(chain, true)
                    repeat(2) { index ->
                        val artifact =
                            JsonObject(
                                Json.parseToJsonElement(packages[index]).jsonObject +
                                    ("status_chain" to Json.parseToJsonElement(active.statusesJson)),
                            ).toString()
                        val descriptor = collector.beginPair(true)
                        PallaSyncRecoveryReleaseOwner().use { reader ->
                            reader.begin(chain, artifact, descriptor, true, active, false, false)
                            assertEquals(reader.visual, collector.bind(checkNotNull(reader.descriptor)))
                            reader.confirm(true)
                            collector.confirm(true)
                            val credential = strictRootUtf8("test credential $index")
                            val original = reader.releaseOffline(credential, true)
                            assertTrue(credential.all { it == 0.toByte() })
                            assertEquals(index + 1, collector.receive(original))
                            assertEquals(index + 1, collector.receive(original))
                        }
                    }
                    collector.finish(true)
                    val completion = collector.recovered()
                    assertTrue(!completion.thresholdTestPassed)
                    val root =
                        completion.takeRoot(PallaSyncRootSession.Operation.RECOVERY_DISABLE, null) {
                            System.currentTimeMillis()
                        }
                    root.use {
                        assertEquals(chain.chainId, it.identity.chainId)
                        assertEquals(chain.adminPublicKey, it.identity.adminPublicKey)
                        assertEquals("retired", signRecoveryRetirement(it, store, active).state)
                    }
                    assertTrue(runCatching { root.identity }.exceptionOrNull() is IllegalStateException)
                    assertEquals("retired", checkNotNull(store.read(active.id)).state)
                }
            }
        }

    private var preferences: android.content.SharedPreferences? = null

    private fun storePrefs() = checkNotNull(preferences)

    private fun withOwnedStore(
        name: String,
        action: (PallaSyncRecoverySetStore) -> Unit,
    ) {
        ActivityScenario.launch(PallaSyncRecoveryTestActivity::class.java).use {
            PallaSyncCore.setSecretForeground(true)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val prefs =
                EncryptedSharedPreferences.create(
                    context,
                    name,
                    PallaSyncPlatformKeys.master(context),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            assertTrue("Only empty test-owned preferences may be used", prefs.all.isEmpty())
            preferences = prefs
            try {
                action(PallaSyncRecoverySetStore(prefs))
            } finally {
                preferences = null
                assertTrue(prefs.edit().clear().commit())
                PallaSyncCore.setSecretForeground(false)
            }
        }
    }

    private fun prepare(store: PallaSyncRecoverySetStore): PallaSyncRecoverySetupOwner {
        val phrase = (List(23) { "abandon" } + "art").joinToString(" ")
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
            System.currentTimeMillis(),
        )
        return owner
    }

    private fun peer(
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
            "create",
        )
    }

    private fun distribute(
        flow: PallaSyncRecoveryDealerWorkflow,
        index: Int,
    ): String {
        val descriptor = flow.beginDistribution(index + 1)
        return peer(flow, descriptor).use { reader ->
            val sas = flow.bind(reader.descriptorJson)
            assertEquals(sas, PallaSyncCore.bindRecoveryTransfer(reader.handle, descriptor))
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
            assertEquals(signed, flow.receive(proposal))
            PallaSyncCore.receiveRecoveryHolder(reader.handle, signed, instance, null, "passphrase-wrapped", true, false, false, 1)
            val artifact =
                PallaSyncCore.recoveryHolderPackage(
                    reader.handle,
                    flow.evidence.statusesJson,
                    strictRootUtf8("test credential $index"),
                )
            val key = PallaSyncCore.unlockOfflineRecoveryKey(artifact, strictRootUtf8("test credential $index"))
            val ack = PallaSyncCore.recoveryHolderPersisted(reader.handle, artifact, key)
            assertTrue(key.all { it == 0.toByte() })
            assertNull(flow.receive(ack))
            artifact
        }
    }

    private fun collect(
        flow: PallaSyncRecoveryDealerWorkflow,
        artifact: String,
        index: Int,
    ) {
        val descriptor = flow.beginCollectionPair(true)
        PallaSyncRecoveryReleaseOwner().use { reader ->
            val manifest = flow.evidence.manifest
            val chain =
                PallaSyncRecoveryChainContext(
                    manifest.getValue("chain_id").jsonPrimitive.content,
                    flow.evidence.admin,
                    1,
                    UUID.randomUUID().toString(),
                )
            reader.begin(chain, artifact, descriptor, true, flow.evidence, false, false)
            val sas = flow.bind(checkNotNull(reader.descriptor))
            assertEquals(sas, reader.visual)
            reader.confirm(true)
            assertNull(flow.confirm(true))
            val credential = strictRootUtf8("test credential $index")
            val packet = reader.releaseOffline(credential, true)
            assertTrue(credential.all { it == 0.toByte() })
            assertEquals(PallaSyncRecoveryReleaseOwner.Stage.RELEASED, reader.stage)
            assertEquals(packet, reader.originalPacket())
            assertNull(flow.receive(packet))
        }
    }

    private fun expectCode(
        code: String,
        action: () -> Unit,
    ) {
        val failure = runCatching(action).exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(code, failure?.message)
    }
}
