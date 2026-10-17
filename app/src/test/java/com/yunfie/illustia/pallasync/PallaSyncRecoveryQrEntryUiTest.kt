package com.yunfie.illustia.pallasync

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.screens.pallasync.PallaSyncRecoveryQrEntryButton
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** §18.6: QR scan entry is visible and enabled before any recovery session starts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PallaSyncRecoveryQrEntryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun receiverCanStartQrScanAndSeeScannedStateBeforeStartingAnySecretSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var scanned by mutableStateOf(false)
        var requestedScan = false
        compose.setContent {
            MiuixTheme {
                Column {
                    PallaSyncRecoveryQrEntryButton(scanned, enabled = true) { requestedScan = true }
                }
            }
        }

        compose.onNodeWithText(context.getString(R.string.pallasync_recovery_scan_descriptor)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(requestedScan) }
        compose.runOnIdle { scanned = true }
        compose.onNodeWithText(context.getString(R.string.pallasync_recovery_descriptor_scanned)).assertIsDisplayed()
    }
}
