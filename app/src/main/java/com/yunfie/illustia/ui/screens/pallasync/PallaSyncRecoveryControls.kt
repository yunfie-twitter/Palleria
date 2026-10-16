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
import kotlinx.serialization.json.jsonPrimitive
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

private data class RecoverySafetyAction(
    val entry: PallaSyncRecoverySetStore.Entry,
    val shareId: String? = null,
    val eraseOwn: Boolean = false,
)

/** §§18.7–18.9: independent set capacities, incomplete preparation and explicit local safety actions. */
@Composable
internal fun PallaSyncRecoveryControls(
    manager: PalleriaSyncManager,
    onManageDevices: () -> Unit,
) {
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
    var migrating by remember { mutableStateOf<PallaSyncRecoverySetStore.Entry?>(null) }
    var migrationResult by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var migrationReceiptProgress by remember { mutableStateOf<Triple<String, Long, Long>?>(null) }
    var activeTargetChain by remember { mutableStateOf<String?>(null) }
    var serverOrigin by remember { mutableStateOf("") }
    var createNewRootFor by remember { mutableStateOf<PallaSyncRecoverySetStore.Entry?>(null) }
    var genesisPhrase by remember { mutableStateOf<String?>(null) }
    var showCreateNewRootConfirm by remember { mutableStateOf(false) }
    var showNewRootTrustDialog by remember { mutableStateOf(false) }
    LaunchedEffect(management, revision) {
        try {
            entries = management.inventory()
            heldSetId = management.heldSetId()
            activeTargetChain = manager.activeNewRootMigrationTarget()
            serverOrigin = manager.getServerUrl()
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
                            val sourceChain =
                                entry
                                    .manifest
                                    .getValue("chain_id")
                                    .jsonPrimitive
                                    .content
                            PallaSyncRecoverySetCapacity(entry, busy, { action = RecoverySafetyAction(entry, it) }, {
                                action =
                                    RecoverySafetyAction(entry)
                            })
                            if (entry.compromised || entry.state == "compromised") {
                                TextButton(
                                    text = stringResource(R.string.pallasync_recovery_new_root_create_chain),
                                    enabled =
                                        !busy &&
                                            (activeTargetChain == null || activeTargetChain == sourceChain) &&
                                            serverOrigin.isNotBlank(),
                                    onClick = {
                                        createNewRootFor = entry
                                        showCreateNewRootConfirm = true
                                    },
                                )
                                Text(
                                    stringResource(
                                        R.string.pallasync_recovery_new_root_migration_target,
                                        activeTargetChain ?: stringResource(R.string.pallasync_recovery_unknown),
                                    ),
                                )
                                TextButton(
                                    text = stringResource(R.string.pallasync_recovery_new_root_migration),
                                    enabled = !busy && activeTargetChain?.let { it != sourceChain } == true,
                                    onClick = { migrating = entry },
                                )
                                migrationReceiptProgress?.takeIf { it.first == sourceChain }?.let { (_, verified, total) ->
                                    Text(stringResource(R.string.pallasync_recovery_new_root_migration_progress, verified, total))
                                }
                                migrationResult?.takeIf { it.first == sourceChain }?.let { (_, matches) ->
                                    Text(
                                        stringResource(
                                            if (matches) {
                                                R.string.pallasync_recovery_new_root_migration_verified
                                            } else {
                                                R.string.pallasync_recovery_new_root_migration_mismatch
                                            },
                                        ),
                                    )
                                }
                                activeTargetChain?.let { target ->
                                    if (migrationResult == (sourceChain to true)) {
                                        val targetRecoveryConfigured =
                                            current.any { candidate ->
                                                candidate.manifest["chain_id"]?.jsonPrimitive?.content == target &&
                                                    candidate.state == "active" &&
                                                    !candidate.compromised
                                            }
                                        if (!targetRecoveryConfigured) {
                                            TextButton(
                                                text = stringResource(R.string.pallasync_recovery_new_root_create_recovery_set),
                                                enabled = !busy,
                                                onClick = { creating = true },
                                            )
                                        }
                                        TextButton(
                                            text = stringResource(R.string.pallasync_recovery_new_root_manage_devices),
                                            enabled = !busy,
                                            onClick = onManageDevices,
                                        )
                                        PallaSyncNewRootRetirementControls(manager, sourceChain, target) {
                                            migrationResult = null
                                            migrationReceiptProgress = null
                                            failed = false
                                            revision++
                                        }
                                    }
                                }
                            }
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
    if (showCreateNewRootConfirm) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_recovery_new_root_create_chain),
            summary = stringResource(R.string.pallasync_recovery_new_root_create_chain_warning),
            confirmText = stringResource(R.string.action_confirm),
            destructive = true,
            onDismiss = {
                showCreateNewRootConfirm = false
                createNewRootFor = null
            },
            onConfirm = {
                showCreateNewRootConfirm = false
                showNewRootTrustDialog = true
            },
        )
    }
    if (showNewRootTrustDialog) {
        PallaSyncTrustDialog(
            origin = serverOrigin,
            sourceKind = "new_chain_local_state",
            sourceLabel = stringResource(R.string.pallasync_v3_local_source),
            onDismiss = {
                showNewRootTrustDialog = false
                createNewRootFor = null
            },
            onAccepted = {
                showNewRootTrustDialog = false
                val selected = createNewRootFor
                scope.launch {
                    busy = true
                    try {
                        val current = management.inventory().firstOrNull { it.id == selected?.id }
                        check(current != null && (current.compromised || current.state == "compromised")) {
                            "C_RECOVERY_SHARE_COMPROMISED"
                        }
                        val mnemonic = manager.generateGenesisMnemonic()
                        check(mnemonic.isNotBlank()) { "C_SCHEMA_VIOLATION" }
                        genesisPhrase = mnemonic
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                        createNewRootFor = null
                    } finally {
                        busy = false
                    }
                }
            },
        )
    }
    genesisPhrase?.let { phrase ->
        PallaSyncGenesisDialog(manager, phrase, serverOrigin) {
            genesisPhrase = null
            createNewRootFor = null
            revision++
        }
    }
    collecting?.let { (entry, operation) ->
        PallaSyncRecoveryCollectDialog(manager, entry, operation) {
            collecting = null
            revision++
        }
    }
    migrating?.let { entry ->
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_recovery_new_root_migration),
            summary =
                stringResource(
                    R.string.pallasync_recovery_new_root_migration_warning,
                    entry
                        .manifest
                        .getValue("chain_id")
                        .jsonPrimitive
                        .content,
                    activeTargetChain ?: stringResource(R.string.pallasync_recovery_unknown),
                ),
            confirmText = stringResource(R.string.action_confirm),
            destructive = true,
            onDismiss = { migrating = null },
            onConfirm = {
                migrating = null
                failed = false
                migrationResult = null
                migrationReceiptProgress = null
                busy = true
                scope.launch {
                    try {
                        val source =
                            entry
                                .manifest
                                .getValue("chain_id")
                                .jsonPrimitive
                                .content
                        val target = checkNotNull(manager.activeNewRootMigrationTarget()) { "C_MISSING_DEPENDENCY" }
                        check(target == activeTargetChain && source != target) { "C_ROLLBACK_DETECTED" }
                        val stillCompromised =
                            management.inventory().any { current ->
                                current.id == entry.id && (current.compromised || current.state == "compromised")
                            }
                        check(
                            stillCompromised && (entry.compromised || entry.state == "compromised"),
                        ) { "C_RECOVERY_SHARE_COMPROMISED" }
                        var progress = manager.newRootMigrationProgress(target)
                        while (progress?.checkpoint?.complete != true) {
                            manager.advanceNewRootMigrationPage(source, target)
                            check(manager.syncNow()) { "C_ACCEPTANCE_UNPROVEN" }
                            val advanced = manager.newRootMigrationProgress(target)
                            check(advanced != null && advanced.checkpoint.anchor.chainId == source) { "C_ROLLBACK_DETECTED" }
                            check(advanced.checkpoint.complete || advanced.checkpoint != progress?.checkpoint) { "C_ACCEPTANCE_UNPROVEN" }
                            progress = advanced
                            migrationReceiptProgress = Triple(source, advanced.receipts.receiptVerified, advanced.receipts.staged)
                        }
                        while (progress?.receipts?.complete != true) {
                            check(manager.syncNow()) { "C_ACCEPTANCE_UNPROVEN" }
                            val advanced = manager.newRootMigrationProgress(target) ?: error("C_MISSING_DEPENDENCY")
                            check(advanced.receipts != progress?.receipts) { "C_ACCEPTANCE_UNPROVEN" }
                            progress = advanced
                            migrationReceiptProgress = Triple(source, advanced.receipts.receiptVerified, advanced.receipts.staged)
                        }
                        check(manager.syncNow()) { "C_ACCEPTANCE_UNPROVEN" }
                        val verified = manager.verifyNewRootMigrationState(source, target)
                        migrationResult = source to (verified.logicalStateMatches == true)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                    } finally {
                        busy = false
                        revision++
                    }
                }
            },
        )
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
