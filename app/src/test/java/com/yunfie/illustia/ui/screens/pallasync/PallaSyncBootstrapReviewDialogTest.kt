package com.yunfie.illustia.ui.screens.pallasync

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncBootstrapReview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PallaSyncBootstrapReviewDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `dialog gives trust guidance first and keeps technical identity available in details`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            MiuixTheme {
                PallaSyncBootstrapReviewContent(
                    review = sampleReview,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        compose
            .onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_summary, "https://relay.example", 17))
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_trust_guidance)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_chain, "chain-1")).assertDoesNotExist()
        compose
            .onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_show_details))
            .assertIsDisplayed()
            .performClick()
        compose
            .onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_chain, "chain-1"))
            .performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_admin, "admin-key"))
            .performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_head, 17, "history-hash"))
            .performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_parameters, "parameters-hash"))
            .performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_epoch, "epoch-hash"))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_limit)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.action_cancel)).assertIsDisplayed().assertHasClickAction()
        compose
            .onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_confirm))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    private companion object {
        val sampleReview =
            PallaSyncBootstrapReview(
                relayOrigin = "https://relay.example",
                chainId = "chain-1",
                adminPublicKey = "admin-key",
                logId = "log-1",
                generation = 2,
                headSeq = 17,
                headHash = "history-hash",
                parametersHash = "parameters-hash",
                epochHash = "epoch-hash",
                transparencyId = "transparency-1",
                transparencyIndex = 31,
                transparencyHash = "transparency-hash",
                dataZero = "data-zero",
            )
    }
}
