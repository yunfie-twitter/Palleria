package com.yunfie.illustia.ui.screens.pallasync

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.R
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PallaSyncLocalStorageRecoveryTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `storage failure stays visible and a later explicit retry reports applied count`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var attempts = 0
        compose.setContent {
            MiuixTheme {
                PallaSyncLocalStorageRecovery(2) {
                    attempts++
                    if (attempts == 1) error("C_LOCAL_STORAGE_FULL")
                    2
                }
            }
        }
        compose.runOnIdle { attempts shouldBe 0 }
        val action = context.getString(R.string.pallasync_storage_recovery_retry)
        compose.onNodeWithText(action).performClick()
        compose.onNodeWithText(context.getString(R.string.pallasync_storage_recovery_failed)).assertIsDisplayed()
        compose.onNodeWithText(action).performClick()
        compose.onNodeWithText(context.getString(R.string.pallasync_pending_evidence_retry_result, 2)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pallasync_storage_recovery_failed)).assertDoesNotExist()
        compose.runOnIdle { attempts shouldBe 2 }
    }

    @Test
    fun `no storage dispositions means no retry action`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent { MiuixTheme { PallaSyncLocalStorageRecovery(0) { error("Unexpected retry") } } }
        compose.onNodeWithText(context.getString(R.string.pallasync_storage_recovery_retry)).assertDoesNotExist()
    }
}
