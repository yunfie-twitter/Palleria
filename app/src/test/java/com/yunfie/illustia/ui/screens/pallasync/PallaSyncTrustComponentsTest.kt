package com.yunfie.illustia.ui.screens.pallasync

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
@Config(application = Application::class, sdk = [34])
class PallaSyncTrustComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `step indicator renders one marker per trust decision`() {
        compose.setContent { MiuixTheme { TrustStepIndicator(current = TrustStep.SOURCE) } }

        compose.onNodeWithTag("pallasync-trust-steps").assertIsDisplayed()
        compose.onNodeWithText("1").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
    }

    /**
     * The fingerprint must stay comparable: grouping is presentation only, so every character of
     * the original key has to survive. This is the whole point of asking the user to compare it.
     */
    @Test
    fun `fingerprint grouping is lossless`() {
        val key = "RWQf6LRCGA9i53mlYecO4IzT51TGPpvWucNSCh1CBM0QTaLn73Y7GFO3"
        assertEquals(key, fingerprintLines(key).joinToString("").replace(" ", ""))
    }

    @Test
    fun `fingerprint is rendered in grouped lines`() {
        val key = "RWQf6LRCGA9i53mlYecO4IzT51TGPpvWucNSCh1CBM0QTaLn73Y7GFO3"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            MiuixTheme { TrustFingerprint(value = key, label = context.getString(R.string.pallasync_trust_fingerprint_label)) }
        }

        compose.onNodeWithTag("pallasync-trust-fingerprint").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pallasync_trust_fingerprint_label)).assertIsDisplayed()
        // The first rendered line is the first four characters followed by a group separator.
        compose.onNodeWithText(key.take(4), substring = true).assertIsDisplayed()
    }

    @Test
    fun `trust note shows the limit text`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            MiuixTheme {
                Column { TrustNote(context.getString(R.string.pallasync_v3_source_completeness_limit)) }
            }
        }

        compose
            .onNodeWithText(context.getString(R.string.pallasync_v3_source_completeness_limit))
            .assertIsDisplayed()
    }
}
