package com.yunfie.illustia.ui.screens.pallasync

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncRecoveryCollectorOwner
import com.yunfie.illustia.pallasync.PallaSyncRecoveryLocalFiles
import com.yunfie.illustia.pallasync.PallaSyncRecoveryManagement
import com.yunfie.illustia.pallasync.PallaSyncRecoveryOfflineMedia
import com.yunfie.illustia.pallasync.PallaSyncRecoverySetStore
import com.yunfie.illustia.pallasync.PallaSyncRootCancellation
import com.yunfie.illustia.pallasync.PallaSyncRootSession
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import java.nio.charset.CharacterCodingException

/** §§5.5/11.4/18.7: collect only for the explicitly selected operation, never display recovered inputs. */
@Composable
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal fun PallaSyncRecoveryCollectDialog(
    manager: PalleriaSyncManager,
    entry: PallaSyncRecoverySetStore.Entry,
    operation: PallaSyncRootSession.Operation,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    val dismiss by rememberUpdatedState(onDismiss)
    val owner =
        remember(entry, operation) {
            PallaSyncRecoveryCollectorOwner(entry, operation, PallaSyncRecoverySetStore(context.applicationContext).auditQueue)
        }
    val cancellation = remember(owner) { PallaSyncRootCancellation() }
    val management = remember(manager) { PallaSyncRecoveryManagement(context.applicationContext, manager) }
    var incoming by remember { mutableStateOf<Uri?>(null) }
    var outgoing by remember { mutableStateOf<Uri?>(null) }
    var consent by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf(owner.stage) }
    var shares by remember { mutableStateOf(0) }
    var descriptor by remember { mutableStateOf<String?>(null) }
    var sas by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var succeeded by remember { mutableStateOf(false) }
    var confirmSas by remember { mutableStateOf(false) }
    var confirmOperation by remember { mutableStateOf(false) }
    val title =
        stringResource(
            if (operation == PallaSyncRootSession.Operation.RECOVERY_DISABLE) {
                R.string.pallasync_recovery_collect_disable
            } else {
                R.string.pallasync_recovery_collect_rotate
            },
        )
    val open =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    PallaSyncRecoveryOfflineMedia.requireLocal(uri)
                    check(uri != outgoing) { "C_RECOVERY_STORAGE_UNSAFE" }
                    incoming = uri
                } catch (_: Exception) {
                    failed = true
                }
            }
        }
    val create =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                try {
                    PallaSyncRecoveryOfflineMedia.requireLocal(uri)
                    check(uri != incoming) { "C_RECOVERY_STORAGE_UNSAFE" }
                    outgoing = uri
                } catch (_: Exception) {
                    failed = true
                }
            }
        }
    DisposableEffect(owner, lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && started) {
                    cancellation.cancel()
                    owner.close()
                    dismiss()
                }
            }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            cancellation.cancel()
            owner.close()
        }
    }

    suspend fun write(original: String) {
        pending = original
        withContext(Dispatchers.IO) { PallaSyncRecoveryLocalFiles.write(context, checkNotNull(outgoing), original) }
        pending = null
    }

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        failed = false
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                action()
            } catch (cancelled: CancellationException) {
                cancellation.cancel()
                owner.close()
                throw cancelled
            } catch (failure: Exception) {
                if (failure is CharacterCodingException ||
                    (failure is IllegalStateException && failure.message != "C_MISSING_DEPENDENCY")
                ) {
                    cancellation.cancel()
                    owner.close()
                    pending = null
                }
                failed = true
            } finally {
                stage = owner.stage
                descriptor = owner.descriptor
                sas = owner.visual
                shares = owner.distinctShares
                busy = false
            }
        }
    }
    LaunchedEffect(descriptor) {
        if (descriptor != null) {
            delay(300_000)
            cancellation.cancel()
            owner.close()
            pending = null
            stage = owner.stage
        }
    }
    val enabled = !busy && pending == null && stage != PallaSyncRecoveryCollectorOwner.Stage.CLOSED
    PallaSyncProtectedDialog(title, onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.pallasync_recovery_collect_guidance))
            Text(stringResource(R.string.pallasync_recovery_set_id, entry.id))
            Text(stringResource(R.string.pallasync_recovery_collected_count, shares, entry.threshold))
            if (failed) Text(stringResource(R.string.pallasync_recovery_action_failed))
            if (succeeded) Text(stringResource(R.string.pallasync_recovery_collect_succeeded))
            if (stage == PallaSyncRecoveryCollectorOwner.Stage.CLOSED && !succeeded) {
                Text(stringResource(R.string.pallasync_recovery_fresh_session_needed))
            }
            if (stage == PallaSyncRecoveryCollectorOwner.Stage.NEW) {
                Text(stringResource(R.string.pallasync_recovery_local_files_guidance))
                TextButton(
                    stringResource(R.string.pallasync_recovery_choose_incoming),
                    onClick = { open.launch(arrayOf("application/json")) },
                )
                TextButton(
                    stringResource(R.string.pallasync_recovery_choose_outgoing),
                    onClick = { create.launch("recovery-collector.json") },
                )
                TextButton(
                    stringResource(
                        if (consent) {
                            R.string.pallasync_recovery_independent_confirmed
                        } else {
                            R.string.pallasync_recovery_reader_confirm
                        },
                    ),
                    onClick = { consent = !consent },
                )
                TextButton(
                    stringResource(R.string.pallasync_recovery_collect_begin),
                    enabled = enabled && consent && incoming != null && outgoing != null,
                    onClick = {
                        started = true
                        run {
                            withContext(Dispatchers.IO) {
                                owner.begin(checkNotNull(manager.recoveryChainContext()) { "C_MISSING_DEPENDENCY" }, true)
                            }
                        }
                    },
                )
            }
            if (pending != null && stage != PallaSyncRecoveryCollectorOwner.Stage.CLOSED) {
                TextButton(stringResource(R.string.pallasync_recovery_retry_file), enabled = !busy, onClick = {
                    val original = checkNotNull(pending)
                    run { write(original) }
                })
            }
            if (stage == PallaSyncRecoveryCollectorOwner.Stage.READY) {
                TextButton(
                    stringResource(R.string.pallasync_recovery_collect_reader),
                    enabled = enabled,
                    onClick = { run { write(withContext(Dispatchers.IO) { owner.beginPair(true) }) } },
                )
                TextButton(
                    stringResource(R.string.pallasync_recovery_collect_holder),
                    enabled = enabled,
                    onClick = { run { write(withContext(Dispatchers.IO) { owner.beginPair(false) }) } },
                )
                TextButton(title, enabled = enabled && shares >= entry.threshold, onClick = { confirmOperation = true })
            }
            if (stage == PallaSyncRecoveryCollectorOwner.Stage.SAS) {
                descriptor?.let { PallaSyncRecoveryDescriptorQr(it) }
                TextButton(stringResource(R.string.pallasync_recovery_read_peer_descriptor), enabled = enabled, onClick = {
                    run { withContext(Dispatchers.IO) { owner.bind(PallaSyncRecoveryLocalFiles.read(context, checkNotNull(incoming))) } }
                })
                sas?.let {
                    Text(stringResource(R.string.pallasync_recovery_live_sas, it))
                    TextButton(stringResource(R.string.pallasync_recovery_confirm_sas), enabled = enabled, onClick = { confirmSas = true })
                }
            }
            if (stage == PallaSyncRecoveryCollectorOwner.Stage.RECEIVING) {
                TextButton(stringResource(R.string.pallasync_recovery_read_packet), enabled = enabled, onClick = {
                    run { withContext(Dispatchers.IO) { owner.receive(PallaSyncRecoveryLocalFiles.read(context, checkNotNull(incoming))) } }
                })
            }
            TextButton(stringResource(R.string.action_cancel), onClick = onDismiss)
            if (owner.auditStorageFailed) Text(stringResource(R.string.pallasync_recovery_audit_storage_failed))
        }
    }
    if (confirmSas) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_recovery_confirm_sas),
            summary = stringResource(R.string.pallasync_recovery_sas_guidance, sas.orEmpty()),
            confirmText = stringResource(R.string.action_confirm),
            onDismiss = { confirmSas = false },
            onConfirm = {
                confirmSas = false
                run { owner.confirm(true) }
            },
        )
    }
    if (confirmOperation) {
        MiuixConfirmDialog(
            show = true,
            title = title,
            summary = stringResource(R.string.pallasync_recovery_collect_operation_confirm),
            confirmText = stringResource(R.string.action_confirm),
            destructive = operation == PallaSyncRootSession.Operation.RECOVERY_DISABLE,
            onDismiss = { confirmOperation = false },
            onConfirm = {
                confirmOperation = false
                run {
                    withContext(Dispatchers.IO) { owner.finish(true) }
                    succeeded =
                        if (operation == PallaSyncRootSession.Operation.RECOVERY_DISABLE) {
                            management.retireRecovered(entry, owner.recovered(), cancellation)
                            true
                        } else {
                            manager.rotateRecoveredEpoch(owner.recovered(), cancellation)
                        }
                    owner.operationFinished(succeeded)
                    owner.close()
                }
            },
        )
    }
}
