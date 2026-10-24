package com.yunfie.illustia.ui.screens.pallasync

import android.app.Application
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PallaSyncMnemonicDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `recovery mnemonic is shown in protected modal with explicit saved action`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val phrase = "alpha bravo charlie delta"
        var savedPassphrase: String? = null

        compose.setContent {
            MiuixTheme {
                PallaSyncMnemonicDialog(
                    phrase = phrase,
                    onDismiss = {},
                    onSaved = { savedPassphrase = it },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.pallasync_v3_mnemonic)).assertIsDisplayed()
        compose.onNodeWithText(phrase).assertIsDisplayed()
        compose
            .onNodeWithText(context.getString(R.string.pallasync_v3_mnemonic_saved))
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals("", savedPassphrase)
    }
}
