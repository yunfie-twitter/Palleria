package com.yunfie.illustia.ui.screens.pallasync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncRecoveryManagement
import com.yunfie.illustia.pallasync.PallaSyncRecoverySetStore
import com.yunfie.illustia.pallasync.PallaSyncRootSession
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.Section
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

private data class RecoverySafetyAction(
    val entry: PallaSyncRecoverySetStore.Entry,
    val shareId: String? = null,
    val eraseOwn: Boolean = false,
)

/** §§18.7–18.9: independent set capacities, incomplete preparation and explicit local safety actions. */
@Composable
internal fun PallaSyncRecoveryControls(manager: PalleriaSyncManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val management = remember(manager) { PallaSyncRecoveryManagement(context.applicationContext, manager) }
    var entries by remember { mutableStateOf<List<PallaSyncRecoverySetStore.Entry>?>(null) }
    var action by remember { mutableStateOf<RecoverySafetyAction?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var revision by remember { mutableStateOf(0) }
    var heldSetId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf<PallaSyncRecoverySetStore.Entry?>(null) }
    var resumingReplacement by remember { mutableStateOf<PallaSyncRecoverySetStore.Entry?>(null) }
    var receiving by remember { mutableStateOf(false) }
    var releasing by remember { mutableStateOf(false) }
    var collecting by remember { mutableStateOf<Pair<PallaSyncRecoverySetStore.Entry, PallaSyncRootSession.Operation>?>(null) }
    LaunchedEffect(management, revision) {
        try {
            entries = management.inventory()
            heldSetId = management.heldSetId()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalStateException) {
            entries = null
            failed = true
        }
    }
    Section(stringResource(R.string.pallasync_recovery_title)) {
        ElevatedPanel(contentPadding = PaddingValues(16.dp)) {
            Column {
                Text(stringResource(R.string.pallasync_recovery_independent_warning))
                Text(stringResource(R.string.pallasync_recovery_freshness_warning))
                TextButton(text = stringResource(R.string.pallasync_recovery_create), enabled = !busy, onClick = { creating = true })
                TextButton(text = stringResource(R.string.pallasync_recovery_receive), enabled = !busy, onClick = { receiving = true })
                TextButton(text = stringResource(R.string.pallasync_recovery_release), enabled = !busy, onClick = { releasing = true })
                if (failed) Text(stringResource(R.string.pallasync_recovery_action_failed))
                val current = entries
                when {
                    current == null -> {
                        Text(stringResource(R.string.pallasync_recovery_unknown))
                    }

                    current.isEmpty() -> {
                        Text(stringResource(R.string.pallasync_recovery_not_provisioned))
                    }

                    else -> {
                        current.forEach { entry ->
                            PallaSyncRecoverySetCapacity(entry, busy, { action = RecoverySafetyAction(entry, it) }, {
                                action =
                                    RecoverySafetyAction(entry)
                            })
                            if (entry.replacementPending && entry.state == "active" && entry.conflictingStatuses == null) {
                                TextButton(
                                    text = stringResource(R.string.pallasync_recovery_resume_replacement),
                                    enabled = !busy,
                                    onClick = { resumingReplacement = entry },
                                )
                            }
                            PallaSyncRecoveryStatusActions(management, manager, entry, busy) { revision++ }
                            if (canReplaceRecoverySet(entry)) {
                                TextButton(
                                    text = stringResource(R.string.pallasync_recovery_replace),
                                    enabled = !busy,
                                    onClick = { replacing = entry },
                                )
                            }
                            val canCollect = !busy && entry.state == "active" && !entry.compromised && entry.conflictingStatuses == null
                            TextButton(
                                stringResource(R.string.pallasync_recovery_collect_disable),
                                enabled = canCollect,
                                onClick = { collecting = entry to PallaSyncRootSession.Operation.RECOVERY_DISABLE },
                            )
                            TextButton(
                                stringResource(R.string.pallasync_recovery_collect_rotate),
                                enabled = canCollect,
                                onClick = { collecting = entry to PallaSyncRootSession.Operation.ROTATE },
                            )
                            if (entry.id == heldSetId) {
                                TextButton(
                                    text = stringResource(R.string.pallasync_recovery_erase_own),
                                    enabled = !busy,
                                    onClick = { action = RecoverySafetyAction(entry, eraseOwn = true) },
                                )
                            }
                        }
                    }
                }
                TextButton(text = stringResource(R.string.pallasync_recovery_refresh), enabled = !busy, onClick = {
                    failed = false
                    revision++
                })
            }
        }
    }
    if (creating) {
        PallaSyncRecoveryCreationDialog(manager, onDismiss = {
            creating = false
            revision++
        })
    }
    replacing?.let { previous ->
        PallaSyncRecoveryCreationDialog(manager, previous = previous, onDismiss = {
            replacing = null
            revision++
        })
    }
    resumingReplacement?.let { entry ->
        PallaSyncRecoveryReplacementResumeDialog(manager, management, entry) {
            resumingReplacement = null
            revision++
        }
    }
    if (receiving) {
        PallaSyncRecoveryReceiveDialog(manager, onDismiss = {
            receiving = false
            revision++
        })
    }
    if (releasing) {
        PallaSyncRecoveryReleaseDialog(manager, onDismiss = {
            releasing = false
            revision++
        })
    }
    collecting?.let { (entry, operation) ->
        PallaSyncRecoveryCollectDialog(manager, entry, operation) {
            collecting = null
            revision++
        }
    }
    action?.let { selected ->
        MiuixConfirmDialog(
            show = true,
            title =
                stringResource(
                    if (selected.eraseOwn) {
                        R.string.pallasync_recovery_erase_own
                    } else if (selected.shareId == null) {
                        R.string.pallasync_recovery_leak
                    } else {
                        R.string.pallasync_recovery_lost
                    },
                ),
            summary =
                stringResource(
                    if (selected.eraseOwn) {
                        R.string.pallasync_recovery_erase_warning
                    } else if (selected.shareId ==
                        null
                    ) {
                        R.string.pallasync_recovery_leak_warning
                    } else {
                        R.string.pallasync_recovery_loss_warning
                    },
                ),
            confirmText = stringResource(R.string.action_confirm),
            destructive = true,
            onDismiss = { action = null },
            onConfirm = {
                action = null
                failed = false
                busy = true
                scope.launch {
                    try {
                        if (selected.eraseOwn) {
                            management.eraseOwn(selected.entry, true)
                        } else if (selected.shareId == null) {
                            management.markCompromised(selected.entry)
                        } else {
                            management.markLost(selected.entry, selected.shareId)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IllegalStateException) {
                        failed = true
                    } finally {
                        busy = false
                        revision++
                    }
                }
            },
        )
    }
}

@Composable
internal fun PallaSyncRecoverySetCapacity(
    entry: PallaSyncRecoverySetStore.Entry,
    busy: Boolean,
    onLost: (String) -> Unit,
    onLeak: () -> Unit,
) {
    Text(stringResource(R.string.pallasync_recovery_set_id, entry.id))
    Text(stringResource(R.string.pallasync_recovery_capacity, entry.usable, entry.threshold, entry.total))
    Text(stringResource(recoveryStateMessage(entry)))
    if (entry.state == "active") {
        Text(
            stringResource(
                if (entry.usable <
                    entry.threshold
                ) {
                    R.string.pallasync_recovery_unavailable
                } else if (entry.usable == entry.threshold) {
                    R.string.pallasync_recovery_next_loss
                } else {
                    R.string.pallasync_recovery_margin
                },
            ),
        )
    }
    entry.holders.forEachIndexed { index, holder ->
        Text(
            stringResource(
                if (holder.lost) {
                    R.string.pallasync_recovery_holder_lost
                } else if (holder.available) {
                    R.string.pallasync_recovery_holder_readback
                } else {
                    R.string.pallasync_recovery_holder_unconfirmed
                },
                index + 1,
            ),
        )
        TextButton(
            text = stringResource(R.string.pallasync_recovery_mark_lost, index + 1),
            enabled = !busy && !holder.lost,
            onClick = { onLost(holder.shareId) },
        )
    }
    TextButton(
        text = stringResource(R.string.pallasync_recovery_leak),
        enabled = !busy && !entry.compromised,
        onClick = onLeak,
    )
}

private fun recoveryStateMessage(entry: PallaSyncRecoverySetStore.Entry): Int =
    when {
        entry.conflictingStatuses != null -> R.string.pallasync_recovery_quarantined
        entry.compromised || entry.state == "compromised" -> R.string.pallasync_recovery_compromised
        entry.replacementPending -> R.string.pallasync_recovery_incomplete
        entry.state == "retired" -> R.string.pallasync_recovery_retired
        entry.interrupted || entry.state == "prepared" -> R.string.pallasync_recovery_incomplete
        else -> R.string.pallasync_recovery_active
    }

private fun canReplaceRecoverySet(entry: PallaSyncRecoverySetStore.Entry): Boolean =
    (entry.state == "active" || entry.compromised) && !entry.replacementPending &&
        entry.conflictingStatuses == null && entry.forkEvidence.isEmpty()
