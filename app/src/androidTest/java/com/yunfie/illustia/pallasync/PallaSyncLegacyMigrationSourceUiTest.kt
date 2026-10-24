package com.yunfie.illustia.pallasync

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.screens.pallasync.PallaSyncLegacyMigrationInput
import com.yunfie.illustia.ui.screens.pallasync.PallaSyncLegacyMigrationSourceDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(AndroidJUnit4::class)
class PallaSyncLegacyMigrationSourceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sourceCredentialsAreProtectedClearedOnSubmitAndRequireFreezeConfirmation() {
        var show by mutableStateOf(true)
        var busy by mutableStateOf(false)
        var submitted: PallaSyncLegacyMigrationInput? = null
        compose.setContent {
            MigrationDialogHost {
                if (show) {
                    PallaSyncLegacyMigrationSourceDialog(busy, { show = false }) {
                        submitted = it
                        busy = true
                        show = false
                    }
                }
            }
        }
        compose.runOnIdle { assertTrue(secure()) }
        compose.onNodeWithTag("pallasync-legacy-source-url").performTextInput("https://legacy.example")
        compose.onNodeWithTag("pallasync-legacy-source-chain").performTextInput("legacy-chain")
        compose.onNodeWithTag("pallasync-legacy-source-device").performTextInput("legacy-device")
        compose.onNodeWithTag("pallasync-legacy-source-public-key").performTextInput("legacy-public")
        compose.onNodeWithTag("pallasync-legacy-source-seed").performTextInput("legacy-seed")
        compose.onNodeWithTag("pallasync-legacy-source-mnemonic").performTextInput("public test words")
        compose.onNodeWithTag("pallasync-legacy-source-passphrase").performTextInput("exact passphrase")
        compose
            .onNodeWithText(compose.activity.getString(R.string.pallasync_legacy_source_freeze_title))
            .performScrollTo()
            .performClick()
        compose
            .onNodeWithText(compose.activity.getString(R.string.pallasync_legacy_source_inspect))
            .performScrollTo()
            .performClick()
        compose.runOnIdle {
            assertEquals("https://legacy.example", submitted?.sourceUrl)
            assertEquals("legacy-chain", submitted?.sourceChainId)
            assertEquals("legacy-device", submitted?.sourceDeviceId)
            assertEquals("legacy-public", submitted?.sourceDevicePublicKey)
            assertEquals("legacy-seed", submitted?.sourceDeviceSeed)
            assertEquals("public test words", submitted?.mnemonic)
            assertEquals("exact passphrase", submitted?.passphrase)
            assertTrue(submitted?.writersStopped == true)
        }
        compose.onNodeWithTag("pallasync-legacy-source-seed").assertDoesNotExist()
        compose.onNodeWithTag("pallasync-legacy-source-mnemonic").assertDoesNotExist()
        compose.runOnIdle { assertFalse(show) }
        compose.runOnIdle { assertFalse(secure()) }
    }

    @Test
    fun lifecycleStopDismissesAndDropsSensitiveSourceFields() {
        var show by mutableStateOf(true)
        var dismissed = 0
        compose.setContent {
            MigrationDialogHost {
                if (show) {
                    PallaSyncLegacyMigrationSourceDialog(false, {
                        dismissed++
                        show = false
                    }) { error("No migration was requested") }
                }
            }
        }
        compose.onNodeWithTag("pallasync-legacy-source-seed").performTextInput("temporary seed")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle {
            assertFalse(show)
            assertTrue(dismissed >= 1)
            assertFalse(secure())
        }
        compose.onNodeWithTag("pallasync-legacy-source-seed").assertDoesNotExist()
        compose.runOnIdle { assertFalse(secure()) }
    }

    private fun secure(): Boolean = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
}

@Composable
private fun MigrationDialogHost(content: @Composable () -> Unit) {
    MiuixTheme { Scaffold { content() } }
}
