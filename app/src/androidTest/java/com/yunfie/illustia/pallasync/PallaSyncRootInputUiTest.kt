package com.yunfie.illustia.pallasync

import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
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
import com.yunfie.illustia.ui.screens.pallasync.PallaSyncMnemonicDialog
import com.yunfie.illustia.ui.screens.pallasync.PallaSyncRootInputDialog
import com.yunfie.illustia.ui.screens.pallasync.PallaSyncSecureInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Real Android window, lifecycle and Compose input; never captures secret screenshots. */
@RunWith(AndroidJUnit4::class)
class PallaSyncRootInputUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun wrappedContextUsesSecureWindowAndSubmissionClearsTheInput() {
        var busy by mutableStateOf(false)
        var show by mutableStateOf(true)
        var submitted: Pair<String, String>? = null
        val words = (List(23) { "abandon" } + "art").joinToString(" ")
        val passphrase = "  é spaced passphrase  "
        compose.setContent {
            CompositionLocalProvider(LocalContext provides ContextThemeWrapper(compose.activity, android.R.style.Theme_Material_Light)) {
                RootInputHost {
                    if (show) {
                        PallaSyncRootInputDialog("Root input test", "Public fixture only", busy, { show = false }) { mnemonic, pass ->
                            submitted = mnemonic to pass
                            busy = true
                        }
                    }
                }
            }
        }
        compose.runOnIdle { assertTrue(secure()) }
        compose.runOnIdle {
            assertTrue(WindowInspector.getGlobalWindowViews().any(::autofillExcluded))
            assertTrue(
                WindowInspector.getGlobalWindowViews().any { view ->
                    val params = view.layoutParams as? WindowManager.LayoutParams
                    params?.type == WindowManager.LayoutParams.TYPE_APPLICATION &&
                        params.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
                },
            )
        }
        compose.onNodeWithTag("pallasync-root-mnemonic").performTextInput(words)
        compose.onNodeWithTag("pallasync-root-passphrase").performTextInput(passphrase)
        compose.onNodeWithText(compose.activity.getString(R.string.action_confirm)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(words to passphrase, submitted) }
        compose.onNodeWithTag("pallasync-root-mnemonic").assertDoesNotExist()
        compose.onNodeWithTag("pallasync-root-passphrase").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.action_cancel)).performScrollTo().performClick()
        compose.runOnIdle { assertFalse(show) }
        compose.runOnIdle { assertFalse(secure()) }
        compose.runOnIdle {
            busy = false
            show = true
        }
        assertEmptyInput("pallasync-root-mnemonic")
        assertEmptyInput("pallasync-root-passphrase")
    }

    @Test
    fun stopDismissesAndReopeningDoesNotRestoreTypedInput() {
        var show by mutableStateOf(true)
        var dismissals = 0
        compose.setContent {
            RootInputHost {
                if (show) {
                    PallaSyncRootInputDialog("Root input test", "Public fixture only", false, {
                        dismissals++
                        show = false
                    }) { _, _ -> error("No root operation was requested") }
                }
            }
        }
        compose.onNodeWithTag("pallasync-root-mnemonic").performTextInput("public temporary input")
        compose.onNodeWithTag("pallasync-root-passphrase").performTextInput("public temporary passphrase")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle {
            assertFalse(show)
            assertTrue(dismissals >= 1)
            assertFalse(secure())
            show = true
        }
        assertEmptyInput("pallasync-root-mnemonic")
        assertEmptyInput("pallasync-root-passphrase")
    }

    @Test
    fun cancellationPreservesAnAlreadySecureActivityWindow() {
        var show by mutableStateOf(true)
        compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        compose.setContent {
            RootInputHost {
                if (show) {
                    PallaSyncRootInputDialog("Root input test", "Public fixture only", false, { show = false }) { _, _ ->
                        error("No root operation was requested")
                    }
                }
            }
        }
        compose.onNodeWithTag("pallasync-root-mnemonic").performTextInput("public temporary input")
        compose.onNodeWithText(compose.activity.getString(R.string.action_cancel)).performScrollTo().performClick()
        compose.runOnIdle {
            assertFalse(show)
            assertTrue(secure())
        }
        compose.activityRule.scenario.onActivity { it.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    @Test
    fun generatedMnemonicClearsBeforeTheProtectedDisplayIsDisposed() {
        var show by mutableStateOf(true)
        val words = (List(23) { "abandon" } + "art").joinToString(" ")
        compose.setContent {
            CompositionLocalProvider(LocalContext provides ContextThemeWrapper(compose.activity, android.R.style.Theme_Material_Light)) {
                RootInputHost {
                    if (show) PallaSyncMnemonicDialog(words, { show = false }, { error("No genesis was requested") })
                }
            }
        }
        compose.onNodeWithText(words).assertIsDisplayed()
        compose.runOnIdle { assertTrue(secure()) }
        compose.onNodeWithText(compose.activity.getString(R.string.action_cancel)).performScrollTo().performClick()
        compose.onNodeWithText(words).assertDoesNotExist()
        compose.runOnIdle { assertFalse(secure()) }
    }

    @Test
    fun generatedMnemonicPassphraseIsExactAndClearedOnSubmission() {
        var show by mutableStateOf(true)
        var busy by mutableStateOf(false)
        var submitted: String? = null
        val words = (List(23) { "abandon" } + "art").joinToString(" ")
        val passphrase = "  é exact genesis input  "
        compose.setContent {
            RootInputHost {
                if (show) {
                    PallaSyncMnemonicDialog(words, { show = false }, {
                        submitted = it
                        busy = true
                    }, busy)
                }
            }
        }
        compose.onNodeWithTag("pallasync-genesis-passphrase").performScrollTo().performTextInput(passphrase)
        compose.onNodeWithText(compose.activity.getString(R.string.pallasync_v3_mnemonic_saved)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(passphrase, submitted) }
        compose.onNodeWithText(words).assertDoesNotExist()
        compose.onNodeWithTag("pallasync-genesis-passphrase").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.action_cancel)).performScrollTo().performClick()
        compose.runOnIdle {
            show = true
            busy = false
        }
        assertEmptyInput("pallasync-genesis-passphrase")
        compose.onNodeWithText(compose.activity.getString(R.string.action_cancel)).performScrollTo().performClick()
        compose.runOnIdle { assertFalse(secure()) }
    }

    @Test
    fun closingAnOlderInputLeaseCannotUnprotectANewerInput() {
        compose.activityRule.scenario.onActivity { activity ->
            val first = PallaSyncSecureInput.protect(activity)
            val second = PallaSyncSecureInput.protect(activity)
            first.close()
            assertTrue(secure())
            first.close()
            assertTrue(secure())
            second.close()
            assertFalse(secure())
        }
    }

    private fun assertEmptyInput(tag: String) {
        compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    private fun secure(): Boolean = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    private fun autofillExcluded(view: View): Boolean =
        view.importantForAutofill == View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS ||
            (view is ViewGroup && (0 until view.childCount).any { autofillExcluded(view.getChildAt(it)) })
}

@Composable
private fun RootInputHost(content: @Composable () -> Unit) {
    MiuixTheme { Scaffold { content() } }
}
