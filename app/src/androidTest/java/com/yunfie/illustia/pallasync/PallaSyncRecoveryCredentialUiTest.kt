package com.yunfie.illustia.pallasync

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.screens.pallasync.PallaSyncRecoveryCredentialDialog
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Actual Compose input/window/lifecycle, not an offline medium or cryptographic readback test. */
@RunWith(AndroidJUnit4::class)
class PallaSyncRecoveryCredentialUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun submissionClearsExactMutableCredentialAndCancellationDoesNotRestoreIt() {
        var show by mutableStateOf(true)
        var submitted: ByteArray? = null
        compose.setContent {
            MiuixTheme {
                Scaffold {
                    if (show) PallaSyncRecoveryCredentialDialog({ show = false }) { submitted = it }
                }
            }
        }
        compose.runOnIdle {
            assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        val publicFixture = "  é test-only credential  "
        compose.onNodeWithTag("pallasync-offline-credential").performScrollTo().performTextInput(publicFixture)
        compose.onNodeWithText(compose.activity.getString(R.string.action_confirm)).performScrollTo().performClick()
        compose.runOnIdle {
            assertArrayEquals(strictRootUtf8(publicFixture), submitted)
            submitted!!.fill(0)
            submitted = null
        }
        compose
            .onNodeWithTag("pallasync-offline-credential")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText(compose.activity.getString(R.string.action_cancel)).performScrollTo().performClick()
        compose.runOnIdle { assertFalse(show) }
    }

    @Test
    fun backgroundingDismissesAndReopeningKeepsCredentialEmpty() {
        var show by mutableStateOf(true)
        compose.setContent {
            MiuixTheme {
                Scaffold {
                    if (show) PallaSyncRecoveryCredentialDialog({ show = false }) { error("No issuance requested") }
                }
            }
        }
        compose.onNodeWithTag("pallasync-offline-credential").performScrollTo().performTextInput("public temporary credential")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle {
            assertFalse(show)
            show = true
        }
        compose
            .onNodeWithTag("pallasync-offline-credential")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }
}
