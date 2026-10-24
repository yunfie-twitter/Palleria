package com.yunfie.illustia.ui.screens.pallasync

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yunfie.illustia.R
import top.yukonga.miuix.kmp.basic.TextButton

/** §18.6: public descriptor QR entry is available before any recovery secret session starts. */
@Composable
internal fun PallaSyncRecoveryQrEntryButton(
    scanned: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        stringResource(if (scanned) R.string.pallasync_recovery_descriptor_scanned else R.string.pallasync_recovery_scan_descriptor),
        enabled = enabled,
        onClick = onClick,
    )
}
