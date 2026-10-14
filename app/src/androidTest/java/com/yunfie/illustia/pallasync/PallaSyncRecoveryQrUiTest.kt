package com.yunfie.illustia.pallasync

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.screens.pallasync.PallaSyncRecoveryReceiveDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** §18.6: the receiver must accept the dealer's signed session descriptor over the displayed QR. */
@RunWith(AndroidJUnit4::class)
class PallaSyncRecoveryQrUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun receiverOffersQrDescriptorScanBeforeStartingAnySecretSession() {
        val manager = PalleriaSyncManager(context = compose.activity)
        compose.setContent {
            MiuixTheme {
                PallaSyncRecoveryReceiveDialog(manager) {}
            }
        }

        compose
            .onNodeWithText(compose.activity.getString(R.string.pallasync_recovery_scan_descriptor))
            .performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText(compose.activity.getString(R.string.pallasync_recovery_receive_begin))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
