package com.yunfie.illustia.ui.screens.pallasync

import com.yunfie.illustia.R

internal enum class RecoveryReceiveFile(
    val label: Int,
    val output: Boolean = false,
) {
    MANIFEST(R.string.pallasync_recovery_receive_manifest),
    STATUSES(R.string.pallasync_recovery_receive_statuses),
    PEER(R.string.pallasync_recovery_receive_descriptor),
    INCOMING(R.string.pallasync_recovery_choose_incoming),
    OUTGOING(R.string.pallasync_recovery_choose_outgoing, true),
    PACKAGE(R.string.pallasync_recovery_receive_medium, true),
}
