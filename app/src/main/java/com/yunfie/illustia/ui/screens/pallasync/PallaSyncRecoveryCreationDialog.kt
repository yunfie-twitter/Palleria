package com.yunfie.illustia.ui.screens.pallasync

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncRecoveryConfiguration
import com.yunfie.illustia.pallasync.PallaSyncRecoveryOfflineMedia
import com.yunfie.illustia.pallasync.PallaSyncRecoverySetStore
import com.yunfie.illustia.pallasync.PallaSyncRecoverySetupOwner
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField

private data class RecoveryHolderInput(
    val instance: String = "",
    val device: String = "",
    val offline: Boolean = false,
)

/** §§18.6/18.9: explicit configuration precedes root input and foreground-only distribution. */
@Composable
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal fun PallaSyncRecoveryCreationDialog(
    manager: PalleriaSyncManager,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    val owner = remember { PallaSyncRecoverySetupOwner(PallaSyncRecoverySetStore(context.applicationContext)) }
    var threshold by remember { mutableStateOf("2") }
    var holders by remember { mutableStateOf(listOf(RecoveryHolderInput(), RecoveryHolderInput(), RecoveryHolderInput(offline = true))) }
    var independent by remember { mutableStateOf(false) }
    var deviceOnlyRisk by remember { mutableStateOf(false) }
    var configuration by remember { mutableStateOf<PallaSyncRecoveryConfiguration?>(null) }
    var incoming by remember { mutableStateOf<Uri?>(null) }
    var outgoing by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    val chooseInput =
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
    val chooseOutput =
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
                    runCatching { owner.close() }
                    onDismiss()
                }
            }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            runCatching { owner.close() }
        }
    }
    if (prepared) {
        PallaSyncProtectedDialog(stringResource(R.string.pallasync_recovery_create), onDismiss) {
            PallaSyncRecoveryDealerControls(owner, checkNotNull(incoming), checkNotNull(outgoing), onDismiss)
        }
    } else if (configuration != null) {
        PallaSyncRootInputDialog(
            title = stringResource(R.string.pallasync_recovery_create),
            explanation = stringResource(R.string.pallasync_recovery_setup_root_guidance),
            busy = busy,
            onDismiss = onDismiss,
            onConfirm = { words, passphrase ->
                busy = true
                started = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val chain = checkNotNull(manager.recoveryChainContext()) { "C_MISSING_DEPENDENCY" }
                            owner.prepare(chain, checkNotNull(configuration), words, passphrase, System.currentTimeMillis())
                        }
                        prepared = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                        configuration = null
                    } finally {
                        busy = false
                    }
                }
            },
        )
    } else {
        PallaSyncProtectedDialog(stringResource(R.string.pallasync_recovery_create), onDismiss) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.pallasync_recovery_setup_guidance))
                Text(stringResource(R.string.pallasync_recovery_local_files_guidance))
                if (failed) Text(stringResource(R.string.pallasync_recovery_action_failed))
                TextButton(
                    stringResource(
                        if (incoming ==
                            null
                        ) {
                            R.string.pallasync_recovery_choose_incoming
                        } else {
                            R.string.pallasync_recovery_incoming_chosen
                        },
                    ),
                    onClick = { chooseInput.launch(arrayOf("application/json")) },
                )
                TextButton(
                    stringResource(
                        if (outgoing ==
                            null
                        ) {
                            R.string.pallasync_recovery_choose_outgoing
                        } else {
                            R.string.pallasync_recovery_outgoing_chosen
                        },
                    ),
                    onClick = { chooseOutput.launch("pallasync-local-control.json") },
                )
                TextField(
                    threshold,
                    onValueChange = { threshold = it },
                    label = stringResource(R.string.pallasync_recovery_threshold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Text(stringResource(R.string.pallasync_recovery_total_holders, holders.size))
                holders.forEachIndexed { index, holder ->
                    RecoveryHolderFields(index, holder, holders.size > 3, onChange = { changed ->
                        holders = holders.toMutableList().also { it[index] = changed }
                        independent = false
                    }, onRemove = {
                        holders = holders.filterIndexed { position, _ -> position != index }
                        independent = false
                    })
                }
                TextButton(
                    stringResource(R.string.pallasync_recovery_add_holder),
                    enabled =
                        holders.size < PallaSyncRecoveryConfiguration.MAX_HOLDERS,
                    onClick = {
                        holders = holders + RecoveryHolderInput()
                        independent = false
                    },
                )
                TextButton(
                    stringResource(
                        if (independent) {
                            R.string.pallasync_recovery_independent_confirmed
                        } else {
                            R.string.pallasync_recovery_confirm_independent
                        },
                    ),
                    onClick = { independent = !independent },
                )
                if (holders.none { it.offline }) {
                    Text(stringResource(R.string.pallasync_recovery_no_offline_warning))
                    TextButton(
                        stringResource(
                            if (deviceOnlyRisk) R.string.pallasync_recovery_risk_confirmed else R.string.pallasync_recovery_confirm_risk,
                        ),
                        onClick = { deviceOnlyRisk = !deviceOnlyRisk },
                    )
                }
                TextButton(
                    stringResource(R.string.action_confirm),
                    enabled = incoming != null && outgoing != null && !started,
                    onClick = {
                        try {
                            val candidate =
                                PallaSyncRecoveryConfiguration(
                                    threshold.toIntOrNull() ?: 0,
                                    holders.map { PallaSyncRecoveryConfiguration.Holder(it.instance, if (it.offline) null else it.device) },
                                    independent,
                                    deviceOnlyRisk,
                                )
                            candidate.holdersJson()
                            configuration = candidate
                            failed = false
                        } catch (_: Exception) {
                            failed = true
                        }
                    },
                )
                TextButton(stringResource(R.string.action_cancel), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun RecoveryHolderFields(
    index: Int,
    holder: RecoveryHolderInput,
    removable: Boolean,
    onChange: (RecoveryHolderInput) -> Unit,
    onRemove: () -> Unit,
) {
    Text(stringResource(R.string.pallasync_recovery_holder_number, index + 1))
    TextButton(
        stringResource(if (holder.offline) R.string.pallasync_recovery_holder_offline else R.string.pallasync_recovery_holder_device),
        onClick = { onChange(holder.copy(offline = !holder.offline)) },
    )
    TextField(
        holder.instance,
        onValueChange = { onChange(holder.copy(instance = it)) },
        label = stringResource(R.string.pallasync_recovery_holder_instance),
        singleLine = true,
    )
    if (!holder.offline) {
        TextField(
            holder.device,
            onValueChange = { onChange(holder.copy(device = it)) },
            label = stringResource(R.string.pallasync_recovery_holder_device_id),
            singleLine = true,
        )
    }
    if (removable) TextButton(stringResource(R.string.pallasync_recovery_remove_holder), onClick = onRemove)
}
