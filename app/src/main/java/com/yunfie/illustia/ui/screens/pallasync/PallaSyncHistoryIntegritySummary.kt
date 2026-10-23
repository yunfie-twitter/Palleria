package com.yunfie.illustia.ui.screens.pallasync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncClientStatus
import top.yukonga.miuix.kmp.basic.Text
import java.text.DateFormat
import java.util.Date

/** Keep author-sequence evidence distinct from relay-prefix and transparency completeness (§§11.2, 11.6, 17.3). */
@Composable
internal fun PallaSyncHistoryIntegritySummary(status: PallaSyncClientStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.pallasync_progress_author_gaps, status.authorGaps))
        Text(stringResource(R.string.pallasync_progress_source_completeness))
        PallaSyncIndependentCheckpointComparisonSummary(status)
        if (status.transparencyObservationConflict) {
            Text(stringResource(R.string.pallasync_progress_transparency_conflict))
        }
        Text(
            stringResource(
                R.string.pallasync_v3_history_outbox_quarantine,
                status.pendingOutbox,
                status.unresolvedQuarantine,
                status.writeState,
            ),
        )
    }
}

/** Relay advertisements never update this status; only a verified peer-signed checkpoint can do so (§11.5). */
@Composable
internal fun PallaSyncIndependentCheckpointComparisonSummary(status: PallaSyncClientStatus) {
    val comparison = status.lastIndependentCheckpointComparison
    if (comparison == null) {
        Text(stringResource(R.string.pallasync_progress_no_independent_comparison))
        Text(stringResource(R.string.pallasync_progress_independent_comparison_offer))
        return
    }
    val checkedAt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(comparison.checkedAtMs))
    Text(
        stringResource(
            R.string.pallasync_progress_last_independent_comparison,
            checkedAt,
            comparison.generation,
            comparison.peerDeviceId,
            comparison.dataSequence,
            comparison.transparencyIndex,
        ),
    )
    if (System.currentTimeMillis() - comparison.checkedAtMs >= INDEPENDENT_COMPARISON_REMINDER_MS) {
        Text(stringResource(R.string.pallasync_progress_independent_comparison_offer))
    }
}

private const val INDEPENDENT_COMPARISON_REMINDER_MS = 30L * 24 * 60 * 60 * 1000
