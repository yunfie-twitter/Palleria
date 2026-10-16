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
import com.yunfie.illustia.pallasync.PallaSyncBootstrapReview
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/** §11.6: the user reviews the identity and authenticated history cut before bootstrap installation. */
@Composable
internal fun PallaSyncBootstrapReviewDialog(
    review: PallaSyncBootstrapReview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.pallasync_bootstrap_review_title),
        onDismissRequest = onDismiss,
    ) {
        PallaSyncBootstrapReviewContent(review, onConfirm, onDismiss)
    }
}

@Composable
internal fun PallaSyncBootstrapReviewContent(
    review: PallaSyncBootstrapReview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.pallasync_bootstrap_review_origin, review.relayOrigin))
            Text(stringResource(R.string.pallasync_bootstrap_review_chain, review.chainId))
            Text(stringResource(R.string.pallasync_bootstrap_review_admin, review.adminPublicKey))
            Text(stringResource(R.string.pallasync_bootstrap_review_generation, review.generation))
            Text(stringResource(R.string.pallasync_bootstrap_review_log, review.logId))
            Text(stringResource(R.string.pallasync_bootstrap_review_head, review.headSeq, review.headHash))
            Text(stringResource(R.string.pallasync_bootstrap_review_parameters, review.parametersHash))
            Text(stringResource(R.string.pallasync_bootstrap_review_epoch, review.epochHash))
            Text(
                stringResource(
                    R.string.pallasync_bootstrap_review_transparency,
                    review.transparencyId,
                    review.transparencyIndex,
                    review.transparencyHash,
                ),
            )
            review.dataZero?.let { Text(stringResource(R.string.pallasync_bootstrap_review_data_zero, it)) }
            Text(stringResource(R.string.pallasync_bootstrap_review_limit))
        }
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_cancel))
        }
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pallasync_bootstrap_review_confirm))
        }
    }
}
