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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncRecoveryHolder
import com.yunfie.illustia.pallasync.PallaSyncRecoveryKeyProtector
import com.yunfie.illustia.pallasync.PallaSyncRecoveryLocalFiles
import com.yunfie.illustia.pallasync.PallaSyncRecoveryOfflineMedia
import com.yunfie.illustia.pallasync.PallaSyncRecoveryRecipientOwner
import com.yunfie.illustia.pallasync.PallaSyncRecoverySlot
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField

/** §§7.11.3–7.11.5/18.6/18.9: file selection and explicit replacement precede any secret session. */
@Composable
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal fun PallaSyncRecoveryReceiveDialog(
    manager: PalleriaSyncManager,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    val dismiss by rememberUpdatedState(onDismiss)
    val owner = remember { PallaSyncRecoveryRecipientOwner() }
    val slot = remember { PallaSyncRecoverySlot(context.applicationContext) }
    val protector = remember { PallaSyncRecoveryKeyProtector(context.applicationContext) }
    val activity = context.pallaSyncInputActivity() as? FragmentActivity
    var files by remember { mutableStateOf<Map<RecoveryReceiveFile, Uri>>(emptyMap()) }
    var choosing by remember { mutableStateOf(RecoveryReceiveFile.MANIFEST) }
    var offline by remember { mutableStateOf(false) }
    var instance by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf(false) }
    var independence by remember { mutableStateOf(false) }
    var occupied by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var admin by remember { mutableStateOf("") }

    fun select(uri: Uri?) {
        if (uri == null) return
        try {
            PallaSyncRecoveryOfflineMedia.requireLocal(uri)
            check(files.filterKeys { it != choosing }.values.none { it == uri }) { "C_RECOVERY_STORAGE_UNSAFE" }
            files = files + (choosing to uri)
            failed = false
        } catch (_: Exception) {
            failed = true
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), ::select)
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), ::select)
    DisposableEffect(owner, lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && started) {
                    owner.close()
                    dismiss()
                }
            }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            owner.close()
        }
    }
    PallaSyncProtectedDialog(stringResource(R.string.pallasync_recovery_receive), onDismiss) {
        if (ready) {
            PallaSyncRecoveryRecipientControls(owner, files, admin, offline, onDismiss)
        } else {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.pallasync_recovery_independent_warning))
                Text(stringResource(R.string.pallasync_recovery_local_files_guidance))
                TextButton(
                    stringResource(if (offline) R.string.pallasync_recovery_holder_offline else R.string.pallasync_recovery_holder_device),
                    enabled = !busy && !started,
                    onClick = {
                        offline = !offline
                        independence = false
                    },
                )
                if (offline) {
                    TextField(
                        instance,
                        {
                            instance = it
                            independence = false
                        },
                        label = stringResource(R.string.pallasync_recovery_holder_instance),
                        singleLine = true,
                    )
                } else {
                    Text(stringResource(R.string.pallasync_recovery_receive_identity, slot.instanceId))
                    Text(stringResource(R.string.pallasync_recovery_receive_protection))
                }
                RecoveryReceiveFile.entries.filter { offline || it != RecoveryReceiveFile.PACKAGE }.forEach { file ->
                    TextButton(
                        stringResource(file.label) + if (files.containsKey(file)) " ✓" else "",
                        enabled = !busy && !started,
                        onClick = {
                            choosing = file
                            if (file.output) {
                                create.launch(
                                    if (file ==
                                        RecoveryReceiveFile.PACKAGE
                                    ) {
                                        "recovery.psr3"
                                    } else {
                                        "recovery-control.json"
                                    },
                                )
                            } else {
                                open.launch(arrayOf("application/json", "application/octet-stream"))
                            }
                        },
                    )
                }
                TextButton(
                    stringResource(
                        if (independence) {
                            R.string.pallasync_recovery_independent_confirmed
                        } else {
                            R.string.pallasync_recovery_confirm_independent
                        },
                    ),
                    enabled = !busy && !started,
                    onClick = { independence = !independence },
                )
                Text(stringResource(R.string.pallasync_recovery_receive_replacement_warning))
                TextButton(
                    stringResource(
                        if (replacement) {
                            R.string.pallasync_recovery_receive_replacement_confirmed
                        } else {
                            R.string.pallasync_recovery_receive_replace
                        },
                    ),
                    enabled = !busy && !started,
                    onClick = { replacement = !replacement },
                )
                if (occupied) Text(stringResource(R.string.pallasync_recovery_receive_occupied))
                if (failed) Text(stringResource(R.string.pallasync_recovery_action_failed))
                if (started && !busy) Text(stringResource(R.string.pallasync_recovery_fresh_session_needed))
                TextButton(
                    stringResource(R.string.pallasync_recovery_receive_begin),
                    enabled =
                        !busy && !started && independence && (offline || (activity != null && protector.available())) &&
                            RecoveryReceiveFile.entries.filter { offline || it != RecoveryReceiveFile.PACKAGE }.all {
                                files.containsKey(
                                    it,
                                )
                            },
                    onClick = {
                        occupied = !offline && slot.read() != null
                        if (!occupied || replacement) {
                            started = true
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        val chain = checkNotNull(manager.recoveryChainContext()) { "C_MISSING_DEPENDENCY" }
                                        admin = chain.adminPublicKey
                                        val manifest =
                                            PallaSyncRecoveryLocalFiles.read(
                                                context,
                                                files.getValue(RecoveryReceiveFile.MANIFEST),
                                            )
                                        val statuses =
                                            PallaSyncRecoveryLocalFiles.read(
                                                context,
                                                files.getValue(RecoveryReceiveFile.STATUSES),
                                            )
                                        val peer = PallaSyncRecoveryLocalFiles.read(context, files.getValue(RecoveryReceiveFile.PEER))
                                        val holder = if (offline) null else PallaSyncRecoveryHolder(slot, protector)
                                        owner.begin(
                                            chain,
                                            manifest,
                                            statuses,
                                            peer,
                                            if (offline) instance else slot.instanceId,
                                            holder,
                                            replacement,
                                        )
                                    }
                                    ready = true
                                } catch (cancelled: CancellationException) {
                                    owner.close()
                                    throw cancelled
                                } catch (_: Exception) {
                                    owner.close()
                                    failed = true
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                )
                TextButton(stringResource(R.string.action_cancel), onClick = onDismiss)
            }
        }
    }
}
