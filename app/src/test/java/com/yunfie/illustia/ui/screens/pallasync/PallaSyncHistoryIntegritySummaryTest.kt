package com.yunfie.illustia.ui.screens.pallasync

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncClientStatus
import com.yunfie.illustia.pallasync.PallaSyncIndependentCheckpointComparison
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PallaSyncHistoryIntegritySummaryTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `missing independent checkpoint prompts local comparison`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val status = status().copy(lastIndependentCheckpointComparison = null)

        compose.setContent {
            MiuixTheme {
                PallaSyncHistoryIntegritySummary(status)
            }
        }

        compose.onNodeWithText(context.getString(R.string.pallasync_progress_no_independent_comparison)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pallasync_progress_independent_comparison_offer)).assertIsDisplayed()
    }

    @Test
    fun `comparison older than thirty days prompts another local comparison`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val status =
            status().copy(
                lastIndependentCheckpointComparison =
                    PallaSyncIndependentCheckpointComparison(
                        checkedAtMs = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000,
                        generation = 7,
                        peerDeviceId = "peer-device",
                        dataSequence = 10,
                        transparencyIndex = 24,
                    ),
            )

        compose.setContent {
            MiuixTheme {
                PallaSyncHistoryIntegritySummary(status)
            }
        }

        compose.onNodeWithText(context.getString(R.string.pallasync_progress_independent_comparison_offer)).assertIsDisplayed()
    }

    @Test
    fun `author gap relay completeness and transparency conflict are separate`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val status =
            status().copy(
                lastIndependentCheckpointComparison =
                    PallaSyncIndependentCheckpointComparison(
                        checkedAtMs = System.currentTimeMillis(),
                        generation = 7,
                        peerDeviceId = "peer-device",
                        dataSequence = 10,
                        transparencyIndex = 24,
                    ),
            )

        compose.setContent {
            MiuixTheme {
                PallaSyncHistoryIntegritySummary(status)
            }
        }

        compose.onNodeWithText(context.getString(R.string.pallasync_progress_author_gaps, 2)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pallasync_progress_source_completeness)).assertIsDisplayed()
        val comparison = checkNotNull(status.lastIndependentCheckpointComparison)
        val comparisonText =
            context.getString(
                R.string.pallasync_progress_last_independent_comparison,
                java.text.DateFormat
                    .getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                    .format(java.util.Date(comparison.checkedAtMs)),
                comparison.generation,
                comparison.peerDeviceId,
                comparison.dataSequence,
                comparison.transparencyIndex,
            )
        compose.onNodeWithText(comparisonText).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.pallasync_progress_transparency_conflict)).assertIsDisplayed()
        compose
            .onNodeWithText(context.getString(R.string.pallasync_v3_history_outbox_quarantine, 3, 1, "paused_integrity"))
            .assertIsDisplayed()
    }

    private fun status() =
        PallaSyncClientStatus(
            downloadedSeq = 12,
            verifiedSeq = 10,
            materializedSeq = 9,
            verifiedTransparencyIndex = 20,
            observedTransparencyTargetIndex = 24,
            transparencyObservationConflict = true,
            pendingOutbox = 3,
            authorGaps = 2,
            unresolvedQuarantine = 1,
            writeState = "paused_integrity",
            initialPullCompleted = false,
            searchCount = 0,
            searchCeiling = 100,
            viewedCount = 0,
            viewedCeiling = 100,
            seenCount = 0,
            seenCeiling = 100,
        )
}
