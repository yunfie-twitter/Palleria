package com.yunfie.illustia.pallasync

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yunfie.illustia.ui.screens.pallasync.PallaSyncRecoveryDealerControls
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID

/** Verifies the Compose lifecycle observer closes the live native dealer on a stop event. */
@RunWith(AndroidJUnit4::class)
class PallaSyncRecoveryDealerLifecycleAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<PallaSyncRecoveryTestActivity>()

    private class CeremonyLifecycleOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }

    private fun prepareOwner(name: String): PallaSyncRecoverySetupOwner {
        PallaSyncCore.setSecretForeground(true)
        val prefs = compose.activity.getSharedPreferences(name, 0)
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
        return owner
    }

    @Test
    fun stopEventAbandonsNonterminalDealerSession() {
        val owner = prepareOwner("pallasync-dealer-lifecycle-ui-test")
        val lifecycleOwner = CeremonyLifecycleOwner()
        compose.runOnUiThread {
            lifecycleOwner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                MiuixTheme {
                    PallaSyncRecoveryDealerControls(owner, android.net.Uri.EMPTY, android.net.Uri.EMPTY) {}
                }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { lifecycleOwner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
        compose.waitForIdle()

        try {
            owner.usePrepared { it.userInteraction() }
            throw AssertionError("dealer session remained open after ON_STOP")
        } catch (failure: IllegalStateException) {
            assertEquals("C_RECOVERY_SESSION_EXPIRED", failure.message)
        } finally {
            owner.close()
        }
    }

    @Test
    fun nativeAlignedIdleTimeoutClosesTheWholeCeremony() {
        compose.mainClock.autoAdvance = false
        val owner = prepareOwner("pallasync-dealer-timeout-ui-test")
        compose.setContent {
            MiuixTheme {
                PallaSyncRecoveryDealerControls(owner, android.net.Uri.EMPTY, android.net.Uri.EMPTY) {}
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(299_000)
        compose.runOnIdle { owner.usePrepared { it.userInteraction() } }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()

        try {
            owner.usePrepared { it.userInteraction() }
            throw AssertionError("idle dealer session remained open past its five-minute lease")
        } catch (failure: IllegalStateException) {
            assertEquals("C_RECOVERY_SESSION_EXPIRED", failure.message)
        } finally {
            owner.close()
            compose.mainClock.autoAdvance = true
        }
    }
}
