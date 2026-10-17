package com.yunfie.illustia.ui.screens.pallasync

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
    fun `dialog shows verified source identity and checkpoint before affirmative action`() {
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
            .onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_origin, "https://relay.example"))
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_chain, "chain-1")).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_admin, "admin-key")).assertIsDisplayed()
        compose
            .onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_head, 17, "history-hash"))
            .assertIsDisplayed()
        compose
            .onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_parameters, "parameters-hash"))
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_epoch, "epoch-hash")).assertIsDisplayed()
        compose
            .onNodeWithText(context.getString(R.string.pallasync_bootstrap_review_limit))
            .performScrollTo()
            .assertIsDisplayed()
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
