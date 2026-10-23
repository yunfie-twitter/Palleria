package com.yunfie.illustia.ui.screens.pallasync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncClientStatus
import top.yukonga.miuix.kmp.basic.Text

/** Keep author-sequence evidence distinct from relay-prefix and transparency completeness (§§11.2, 11.6, 17.3). */
@Composable
internal fun PallaSyncHistoryIntegritySummary(status: PallaSyncClientStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.pallasync_progress_author_gaps, status.authorGaps))
        Text(stringResource(R.string.pallasync_progress_source_completeness))
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
