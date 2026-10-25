package com.yunfie.illustia.ui.screens.pallasync

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The dialog body is tested through its own composables (as the other PallaSync dialog tests do).
 * The window footer is outside the scrolling body, so behaviour is asserted where it lives: the
 * numbered word list and the backup check that unlocks the commit action.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PallaSyncMnemonicDialogTest {
    @get:Rule val compose = createComposeRule()

    private val words = "alpha bravo charlie delta echo foxtrot golf hotel india juliet".split(' ')

    /** Words must be individually numbered, not one undifferentiated paragraph. */
    @Test
    fun `recovery mnemonic is shown as numbered words`() {
        compose.setContent { MiuixTheme { MnemonicGrid(words = words) } }

        compose.onNodeWithTag("pallasync-mnemonic-grid").assertIsDisplayed()
        compose.onNodeWithText("alpha").assertIsDisplayed()
        compose.onNodeWithText("juliet").assertIsDisplayed()
        // One chip per word, so no word is dropped or merged into a paragraph.
        compose.onNodeWithTag("pallasync-mnemonic-grid").onChildren().assertCountEquals(words.size)
    }

    /**
     * The backup step must gate the commit action: one accidental tap is exactly the failure mode
     * this dialog exists to prevent.
     */
    @Test
    fun `backup check completes only for the exact positions`() {
        var confirmed = false
        compose.setContent {
            MiuixTheme {
                MnemonicBackupCheck(words = words, confirmed = false, onConfirmed = { confirmed = true })
            }
        }

        // Positions 3 and 7 are checked (zero-based 2 and 6).
        compose.onNodeWithTag("pallasync-mnemonic-pick-0").performClick()
        compose.onNodeWithTag("pallasync-mnemonic-wrong").assertExists()
        assertEquals(false, confirmed)

        compose.onNodeWithTag("pallasync-mnemonic-pick-2").performClick()
        assertEquals(false, confirmed)

        compose.onNodeWithTag("pallasync-mnemonic-pick-6").performClick()
        assertEquals(true, confirmed)
        compose.onNodeWithTag("pallasync-mnemonic-wrong").assertDoesNotExist()
    }

    /** The protected shell must separate the commit action from a busy status. */
    @Test
    fun `commit action is disabled while busy`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            MiuixTheme {
                PallaSyncProtectedDialog(
                    title = context.getString(R.string.pallasync_v3_mnemonic),
                    onDismissRequest = {},
                    actions = {
                        PallaSyncPrimaryAction(
                            text = context.getString(R.string.pallasync_v3_mnemonic_saved),
                            enabled = false,
                            onClick = {},
                        )
                    },
                ) {
                    Column { PallaSyncWorkingState(text = context.getString(R.string.pallasync_v3_root_working)) }
                }
            }
        }

        compose.onNodeWithText(context.getString(R.string.pallasync_v3_mnemonic_saved)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.pallasync_v3_root_working)).assertIsDisplayed()
    }
}
