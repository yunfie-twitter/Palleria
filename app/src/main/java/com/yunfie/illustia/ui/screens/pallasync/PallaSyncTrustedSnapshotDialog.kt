package com.yunfie.illustia.ui.screens.pallasync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncTrustedSnapshotCandidate
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/** §11.6: the exact authorized author and snapshot coverage are shown before explicit trust. */
@Composable
internal fun PallaSyncTrustedSnapshotDialog(
    candidates: List<PallaSyncTrustedSnapshotCandidate>,
    busy: Boolean,
    failure: String?,
    onDismiss: () -> Unit,
    onTrustAndInstall: (PallaSyncTrustedSnapshotCandidate) -> Unit,
) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.pallasync_snapshot_bootstrap_title),
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.pallasync_snapshot_bootstrap_warning))
                if (candidates.isEmpty()) Text(stringResource(R.string.pallasync_snapshot_bootstrap_empty))
                candidates.forEach { candidate ->
                    Text(
                        stringResource(
                            R.string.pallasync_snapshot_bootstrap_candidate,
                            candidate.chainId,
                            candidate.logId,
                            candidate.compactionId,
                            candidate.compactionHash,
                            candidate.coveredThroughSeq,
                            candidate.coveredLogHash,
                            candidate.stateSha256,
                            candidate.epochHash,
                            candidate.authorDeviceId,
                            candidate.authorPublicKey,
                            candidate.partCount,
                        ),
                    )
                    TextButton(
                        text = stringResource(R.string.pallasync_snapshot_bootstrap_trust_install),
                        enabled = !busy,
                        onClick = { onTrustAndInstall(candidate) },
                    )
                }
            }
            failure?.let { Text(it) }
            TextButton(text = stringResource(R.string.action_cancel), enabled = !busy, onClick = onDismiss)
        }
    }
}
