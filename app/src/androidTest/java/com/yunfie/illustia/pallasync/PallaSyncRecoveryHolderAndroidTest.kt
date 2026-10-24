package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

private const val AUTH_TIMEOUT = 60_000L
private const val AUTH_PIN = "246810" // Disposable task-owned emulator credential only.
private const val DEVICE_COUNT = 3
private val androidRecoveryPhrase = (List(23) { "abandon" } + "art").joinToString(" ")

/** Actual Keystore + authenticated cipher + protected prefs + JNI. No screenshots or production keys. */
@RunWith(AndroidJUnit4::class)
class PallaSyncRecoveryHolderAndroidTest {
    @Test
    fun protectedSlotNeedsFreshOsAuthenticationForReadbackAndRelease() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val slot = PallaSyncRecoverySlot(context)
            assertNull("Only the empty disposable holder is authorized for this test", slot.read())
            val protector = PallaSyncRecoveryKeyProtector(context)
            assertTrue("Set the test-only emulator PIN before this case", protector.available())
            val holder = PallaSyncRecoveryHolder(slot, protector)
            var ownedAlias: String? = null
            try {
                ActivityScenario.launch(PallaSyncRecoveryTestActivity::class.java).use { scenario ->
                    lateinit var activity: FragmentActivity
                    scenario.onActivity { activity = it }
                    PallaSyncCore.setSecretForeground(true)
                    PallaSyncRootSession.beginText(androidRecoveryPhrase, "", PallaSyncRootSession.Operation.RECOVERY_CREATE).use { root ->
                        holder.prepare(UUID.randomUUID().toString(), false).use { admission ->
                            ownedAlias = admission.key.alias
                            val set = createSet(root, slot, admission.deviceId)
                            distribute(root, set, holder, admission, activity)
                            val reopened = PallaSyncRecoverySlot(context)
                            assertEquals(slot.read(), reopened.read())
                            assertEquals(slot.instanceId, reopened.instanceId)
                            assertTrue(runCatching { holder.prepare(UUID.randomUUID().toString(), false) }.isFailure)
                            val collection =
                                PallaSyncCore.beginRecoveryCollection(
                                    set.getValue("manifest").toString(),
                                    set.getValue("status_chain").toString(),
                                    root.identity.adminPublicKey,
                                    null,
                                    UUID.randomUUID().toString(),
                                    root.operation.wire,
                                    root.handle,
                                    true,
                                    false,
                                    false,
                                )
                            try {
                                val collected =
                                    Json
                                        .parseToJsonElement(
                                            withPins(1) { holder.collectOwn(collection, activity, true) },
                                        ).jsonObject
                                assertEquals("1", collected.getValue("distinct_shares").jsonPrimitive.content)
                            } finally {
                                PallaSyncCore.endRecoveryCollection(collection)
                            }
                            slot.markCompromised(checkNotNull(slot.read()))
                            assertTrue(checkNotNull(reopened.read()).compromised)
                        }
                    }
                }
            } finally {
                if (slot.read()?.keyAlias == ownedAlias && ownedAlias != null) holder.erase(true)
            }
            assertNull(slot.read())
        }

    @Test
    fun osCredentialPromptPreservesTheRegisteredForegroundOperation() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val protector = PallaSyncRecoveryKeyProtector(context)
            val prepared = protector.prepare()
            try {
                ActivityScenario.launch(PallaSyncRecoveryTestActivity::class.java).use { scenario ->
                    lateinit var activity: FragmentActivity
                    scenario.onActivity {
                        activity = it
                        PallaSyncSecretLifecycle.install()
                    }
                    PallaSyncRootSession
                        .beginText(
                            (List(23) { "abandon" } + "art").joinToString(" "),
                            "",
                            PallaSyncRootSession.Operation.RECOVERY_CREATE,
                        ).use { root ->
                            val identity = root.identity
                            val cipher = withPins(1) { authenticateRecoveryCipher(activity, protector.encrypt(prepared.alias)) }
                            val input = ByteArray(32) { 1 }
                            try {
                                assertEquals(48, cipher.doFinal(input).size)
                                assertEquals(identity, root.identity)
                            } finally {
                                input.fill(0)
                            }
                        }
                }
            } finally {
                protector.delete(prepared.alias)
            }
        }

    @Test
    fun cancelledCryptoAuthenticationDoesNotProduceAnUnlock() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val protector = PallaSyncRecoveryKeyProtector(context)
            assertTrue(protector.available())
            val prepared = protector.prepare()
            try {
                ActivityScenario.launch(PallaSyncRecoveryTestActivity::class.java).use { scenario ->
                    lateinit var activity: FragmentActivity
                    scenario.onActivity { activity = it }
                    val input = ByteArray(32) { 1 } // Public test input, never a user's RSWK.
                    try {
                        assertTrue(runCatching { protector.encrypt(prepared.alias).doFinal(input) }.isFailure)
                        val operation =
                            async(Dispatchers.Default) {
                                authenticateRecoveryCipher(activity, protector.encrypt(prepared.alias))
                            }
                        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                        credentialInput(device)
                        operation.cancelAndJoin()
                        assertTrue(operation.isCancelled)
                        assertTrue(runCatching { protector.encrypt(prepared.alias).doFinal(input) }.isFailure)
                    } finally {
                        input.fill(0)
                    }
                }
            } finally {
                protector.delete(prepared.alias)
            }
        }

    private fun createSet(
        root: PallaSyncRootSession,
        slot: PallaSyncRecoverySlot,
        deviceId: String,
    ): JsonObject {
        val selected =
            JsonArray(
                List(DEVICE_COUNT) { index ->
                    buildJsonObject {
                        put("holder_kind", if (index == 0) "device" else "offline")
                        put("holder_device_id", if (index == 0) JsonPrimitive(deviceId) else JsonNull)
                        put("holder_instance_id", if (index == 0) slot.instanceId else UUID.randomUUID().toString())
                    }
                },
            )
        return Json
            .parseToJsonElement(
                PallaSyncCore.prepareRecoverySet(
                    root.handle,
                    root.operation.wire,
                    0,
                    2,
                    selected.toString(),
                    System.currentTimeMillis(),
                ),
            ).jsonObject
    }

    @Test
    fun typedRecipientPersistsAfterFreshOsReadbackAndRetriesOnlyOriginalAck() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val slot = PallaSyncRecoverySlot(context)
            assertNull("Only an empty disposable holder is authorized", slot.read())
            val holder = PallaSyncRecoveryHolder(slot, PallaSyncRecoveryKeyProtector(context))
            var ownedAlias: String? = null
            try {
                ActivityScenario.launch(PallaSyncRecoveryTestActivity::class.java).use { scenario ->
                    lateinit var activity: FragmentActivity
                    scenario.onActivity { activity = it }
                    PallaSyncCore.setSecretForeground(true)
                    PallaSyncRootSession.beginText(androidRecoveryPhrase, "", PallaSyncRootSession.Operation.RECOVERY_CREATE).use { root ->
                        holder.prepare(UUID.randomUUID().toString(), false).use { admission ->
                            ownedAlias = admission.key.alias
                            val set = createSet(root, slot, admission.deviceId)
                            val chain =
                                PallaSyncRecoveryChainContext(root.identity.chainId, root.identity.adminPublicKey, 0, admission.deviceId)
                            val manifest = set.getValue("manifest").toString()
                            PallaSyncCore
                                .beginRecoveryTransfer(
                                    manifest,
                                    chain.adminPublicKey,
                                    UUID.randomUUID().toString(),
                                    UUID.randomUUID().toString(),
                                    "initiator",
                                    "dealer",
                                    "create",
                                ).use { dealer ->
                                    PallaSyncRecoveryRecipient
                                        .begin(
                                            chain,
                                            manifest,
                                            set.getValue("status_chain").toString(),
                                            dealer.descriptorJson,
                                            slot.instanceId,
                                            holder,
                                            admission,
                                        ).use { receiver ->
                                            assertEquals(
                                                receiver.visual,
                                                PallaSyncCore.bindRecoveryTransfer(dealer.handle, receiver.descriptor),
                                            )
                                            PallaSyncCore.confirmRecoveryTransfer(dealer.handle, receiver.visual, true)
                                            receiver.confirm(true)
                                            val packet =
                                                PallaSyncCore.distributeRecoveryShare(
                                                    root.handle,
                                                    root.operation.wire,
                                                    dealer.handle,
                                                    1,
                                                )
                                            val proposal = checkNotNull(receiver.receive(packet, System.currentTimeMillis()))
                                            val signed =
                                                PallaSyncCore.receiveRecoveryDealer(
                                                    root.handle,
                                                    root.operation.wire,
                                                    dealer.handle,
                                                    proposal,
                                                )
                                            assertNull(receiver.receive(signed, System.currentTimeMillis()))
                                            val ack = withPins(2) { receiver.persistDevice(activity) }
                                            assertEquals(PallaSyncRecoveryRecipient.Stage.PERSISTED, receiver.stage)
                                            assertEquals(ack, receiver.persistedAcknowledgement())
                                            assertEquals(ack, receiver.persistedAcknowledgement())
                                            assertNull(receiver.receive(signed, System.currentTimeMillis()))
                                            val verified =
                                                Json
                                                    .parseToJsonElement(
                                                        PallaSyncCore.receiveRecoveryDealer(
                                                            root.handle,
                                                            root.operation.wire,
                                                            dealer.handle,
                                                            ack,
                                                        ),
                                                    ).jsonObject
                                            assertEquals("true", verified.getValue("verified").jsonPrimitive.content)
                                            assertEquals(slot.read(), PallaSyncRecoverySlot(context).read())
                                        }
                                }
                        }
                    }
                }
            } finally {
                if (ownedAlias != null && slot.read()?.keyAlias == ownedAlias) holder.erase(true)
            }
        }

    private suspend fun distribute(
        root: PallaSyncRootSession,
        set: JsonObject,
        service: PallaSyncRecoveryHolder,
        admission: PallaSyncRecoveryHolder.Admission,
        activity: FragmentActivity,
    ) {
        val manifest = set.getValue("manifest").toString()
        val ceremony = UUID.randomUUID().toString()
        val session = UUID.randomUUID().toString()
        val admin = root.identity.adminPublicKey
        PallaSyncCore.beginRecoveryTransfer(manifest, admin, ceremony, session, "initiator", "dealer", "create").use { dealer ->
            PallaSyncCore.beginRecoveryTransfer(manifest, admin, ceremony, session, "responder", "holder", "create").use { receiver ->
                confirmTestPair(dealer, receiver)
                val packet = PallaSyncCore.distributeRecoveryShare(root.handle, root.operation.wire, dealer.handle, 1)
                val proposal = service.receive(admission, receiver, packet, System.currentTimeMillis())
                val signed = PallaSyncCore.receiveRecoveryDealer(root.handle, root.operation.wire, dealer.handle, proposal)
                service.receive(admission, receiver, signed, System.currentTimeMillis())
                val ack = withPins(2) { service.persist(admission, receiver, set.getValue("status_chain").toString(), admin, activity) }
                // A lost local acknowledgement retries exact encrypted bytes without rewriting or another key release.
                val repeated =
                    withTimeout(5_000L) { service.persist(admission, receiver, set.getValue("status_chain").toString(), admin, activity) }
                assertEquals(ack, repeated)
                val result =
                    Json
                        .parseToJsonElement(
                            PallaSyncCore.receiveRecoveryDealer(
                                root.handle,
                                root.operation.wire,
                                dealer.handle,
                                ack,
                            ),
                        ).jsonObject
                assertEquals("true", result.getValue("verified").jsonPrimitive.content)
            }
        }
    }

    private fun confirmTestPair(
        first: PallaSyncRecoveryCandidate,
        second: PallaSyncRecoveryCandidate,
    ) {
        val sas = PallaSyncCore.bindRecoveryTransfer(first.handle, second.descriptorJson)
        assertEquals(sas, PallaSyncCore.bindRecoveryTransfer(second.handle, first.descriptorJson))
        // Explicit test confirmations do not replace the product's two human SAS screens.
        PallaSyncCore.confirmRecoveryTransfer(first.handle, sas, true)
        PallaSyncCore.confirmRecoveryTransfer(second.handle, sas, true)
    }

    private fun credentialInput(device: UiDevice): BySelector {
        // The OS may first show the biometric sheet's explicit credential fallback.
        val credentialFallbacks =
            listOf(
                By.res("com.android.systemui", "button_use_credential"),
                By.text(
                    java.util.regex.Pattern
                        .compile("(?i)use pin|use screen lock|use credential"),
                ),
            )
        for (fallback in credentialFallbacks) {
            if (device.wait(Until.hasObject(fallback), 5_000)) {
                device.findObject(fallback).click()
                break
            }
        }
        val selectors =
            listOf(
                By.res("cred_pin_pad"), // Observed Android 17 SystemUI Compose credential pad.
                By.clazz("android.widget.EditText"),
                By.res("com.android.systemui", "pinEntry"),
                By.res("com.android.systemui", "lockPassword"),
                By.res("com.android.settings", "password_entry"),
            )
        for (selector in selectors) {
            if (device.wait(Until.hasObject(selector), 5_000)) return selector
        }
        // Only classes/resource IDs are diagnostic; no text, PIN, package/blob or screenshots are recorded.
        val output = java.io.ByteArrayOutputStream()
        device.dumpWindowHierarchy(output)
        val structure =
            Regex("""(?:class|resource-id)="[^"]*"""")
                .findAll(output.toString(Charsets.UTF_8.name()))
                .take(80)
                .joinToString("\n") { it.value }
        android.util.Log.i("PallaSyncRecoveryTest", structure)
        error("Credential UI selectors did not match; inspect test-only class/resource diagnostics")
    }

    private suspend fun <T> withPins(
        count: Int,
        operation: suspend () -> T,
    ): T =
        coroutineScope {
            val baseline = PallaSyncRecoveryAuthEvents.successfulPrompts
            val completion = async(Dispatchers.Default) { operation() }
            val auth =
                launch(Dispatchers.IO) {
                    repeat(count) { index ->
                        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                        credentialInput(device)
                        device.waitForIdle()
                        device.executeShellCommand("input text $AUTH_PIN")
                        device.pressEnter()
                        withTimeout(AUTH_TIMEOUT) {
                            while (PallaSyncRecoveryAuthEvents.successfulPrompts <= baseline + index) {
                                kotlinx.coroutines.delay(50)
                            }
                        }
                    }
                }
            try {
                withTimeout(AUTH_TIMEOUT * count) { completion.await().also { auth.join() } }
            } finally {
                auth.cancelAndJoin()
            }
        }
}
