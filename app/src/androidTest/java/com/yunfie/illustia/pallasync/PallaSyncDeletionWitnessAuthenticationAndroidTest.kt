package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/** Task-owned emulator coverage for the one-time Keystore gate bound to a deletion receipt. */
@RunWith(AndroidJUnit4::class)
class PallaSyncDeletionWitnessAuthenticationAndroidTest {
    @Test
    fun authenticatedReceiptRunsWitnessOperationAndDeletesTemporaryKey() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val before = temporaryAliases()
            ActivityScenario.launch(PallaSyncRecoveryTestActivity::class.java).use { scenario ->
                lateinit var activity: PallaSyncRecoveryTestActivity
                scenario.onActivity { activity = it }
                var operationRan = false
                val result =
                    withDevicePin {
                        authenticateDeletionWitness(
                            activity,
                            context,
                            RECEIPT_HASH,
                            "Witness authentication test",
                            "Disposable emulator only",
                        ) {
                            operationRan = true
                            "witness-created"
                        }
                    }
                assertEquals("witness-created", result)
                assertTrue(operationRan)
            }
            assertEquals(before, temporaryAliases())
        }

    @Test
    fun cancelledReceiptAuthenticationSkipsOperationAndDeletesTemporaryKey() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val before = temporaryAliases()
            ActivityScenario.launch(PallaSyncRecoveryTestActivity::class.java).use { scenario ->
                lateinit var activity: PallaSyncRecoveryTestActivity
                scenario.onActivity { activity = it }
                var operationRan = false
                coroutineScope {
                    val attempt =
                        async(Dispatchers.Default) {
                            runCatching {
                                authenticateDeletionWitness(
                                    activity,
                                    context,
                                    RECEIPT_HASH,
                                    "Witness authentication test",
                                    "Disposable emulator only",
                                ) {
                                    operationRan = true
                                }
                            }
                        }
                    val cancelPrompt =
                        launch(Dispatchers.IO) {
                            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                            val credentialPad = By.res("cred_pin_pad")
                            withTimeout(AUTH_TIMEOUT) {
                                while (!device.hasObject(credentialPad)) delay(POLL_INTERVAL_MS)
                            }
                            device.pressBack()
                        }
                    try {
                        assertTrue(attempt.await().isFailure)
                        cancelPrompt.join()
                    } finally {
                        cancelPrompt.cancelAndJoin()
                    }
                }
                assertFalse(operationRan)
            }
            assertEquals(before, temporaryAliases())
        }

    private suspend fun <T> withDevicePin(operation: suspend () -> T): T =
        coroutineScope {
            val baseline = PallaSyncRecoveryAuthEvents.successfulPrompts
            val completion = async(Dispatchers.Default) { operation() }
            val auth =
                launch(Dispatchers.IO) {
                    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    val credentialPad = By.res("cred_pin_pad")
                    withTimeout(AUTH_TIMEOUT) {
                        while (!device.hasObject(credentialPad)) delay(POLL_INTERVAL_MS)
                    }
                    device.executeShellCommand("input text $AUTH_PIN")
                    device.pressEnter()
                    withTimeout(AUTH_TIMEOUT) {
                        while (PallaSyncRecoveryAuthEvents.successfulPrompts <= baseline) delay(POLL_INTERVAL_MS)
                    }
                }
            try {
                withTimeout(AUTH_TIMEOUT) { completion.await().also { auth.join() } }
            } finally {
                auth.cancelAndJoin()
            }
        }

    private fun temporaryAliases(): Set<String> =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .aliases()
            .toList()
            .filter { it.startsWith(TEMP_ALIAS_PREFIX) }
            .toSet()

    private companion object {
        const val AUTH_PIN = "246810" // Test-only credential on the disposable AVD.
        const val AUTH_TIMEOUT = 60_000L
        const val POLL_INTERVAL_MS = 50L
        const val RECEIPT_HASH = "b64u-exact-signed-receipt-hash"
        const val TEMP_ALIAS_PREFIX = "pallasync.recovery.wrap."
    }
}
