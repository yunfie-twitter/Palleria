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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncRecoveryHolder
import com.yunfie.illustia.pallasync.PallaSyncRecoveryKeyProtector
import com.yunfie.illustia.pallasync.PallaSyncRecoveryLocalFiles
import com.yunfie.illustia.pallasync.PallaSyncRecoveryOfflineMedia
import com.yunfie.illustia.pallasync.PallaSyncRecoveryReleaseOwner
import com.yunfie.illustia.pallasync.PallaSyncRecoverySetStore
import com.yunfie.illustia.pallasync.PallaSyncRecoverySlot
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

/** §§7.11.4/18.7/18.9: user-selected independent local reader or OS-authenticated device endpoint. */
@Composable
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal fun PallaSyncRecoveryReleaseDialog(
    manager: PalleriaSyncManager,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    val dismiss by rememberUpdatedState(onDismiss)
    val owner = remember { PallaSyncRecoveryReleaseOwner() }
    val slot = remember { PallaSyncRecoverySlot(context.applicationContext) }
    val protector = remember { PallaSyncRecoveryKeyProtector(context.applicationContext) }
    val store = remember { PallaSyncRecoverySetStore(context.applicationContext) }
    var offline by remember { mutableStateOf(false) }
    var peer by remember { mutableStateOf<Uri?>(null) }
    var outgoing by remember { mutableStateOf<Uri?>(null) }
    var medium by remember { mutableStateOf<Uri?>(null) }
    var chooseMedium by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    val open =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    PallaSyncRecoveryOfflineMedia.requireLocal(uri)
                    check(uri != outgoing && uri != (if (chooseMedium) peer else medium)) { "C_RECOVERY_STORAGE_UNSAFE" }
                    if (chooseMedium) medium = uri else peer = uri
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
                    check(uri != peer && uri != medium) { "C_RECOVERY_STORAGE_UNSAFE" }
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
    PallaSyncProtectedDialog(stringResource(R.string.pallasync_recovery_release), onDismiss) {
        if (ready) {
            val holder = remember(slot, protector) { PallaSyncRecoveryHolder(slot, protector) }
            PallaSyncRecoveryReleaseControls(owner, checkNotNull(outgoing), offline, holder, onDismiss)
        } else {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.pallasync_recovery_independent_warning))
                Text(stringResource(R.string.pallasync_recovery_reader_warning))
                Text(stringResource(R.string.pallasync_recovery_local_files_guidance))
                TextButton(
                    stringResource(if (offline) R.string.pallasync_recovery_holder_offline else R.string.pallasync_recovery_holder_device),
                    enabled = !busy && !started,
                    onClick = {
                        offline = !offline
                        consent = false
                    },
                )
                TextButton(
                    stringResource(R.string.pallasync_recovery_choose_collector),
                    enabled = !busy && !started,
                    onClick = {
                        chooseMedium = false
                        open.launch(arrayOf("application/json"))
                    },
                )
                TextButton(
                    stringResource(R.string.pallasync_recovery_choose_outgoing),
                    enabled = !busy && !started,
                    onClick = { create.launch("recovery-collect.json") },
                )
                if (offline) {
                    TextButton(
                        stringResource(R.string.pallasync_recovery_choose_saved_medium),
                        enabled = !busy && !started,
                        onClick = {
                            chooseMedium = true
                            open.launch(arrayOf("application/octet-stream", "text/plain"))
                        },
                    )
                }
                TextButton(
                    stringResource(
                        if (consent) {
                            R.string.pallasync_recovery_independent_confirmed
                        } else {
                            R.string.pallasync_recovery_reader_confirm
                        },
                    ),
                    enabled = !busy && !started,
                    onClick = { consent = !consent },
                )
                if (failed) Text(stringResource(R.string.pallasync_recovery_action_failed))
                if (started && !busy) Text(stringResource(R.string.pallasync_recovery_fresh_session_needed))
                TextButton(
                    stringResource(R.string.pallasync_recovery_release_begin),
                    enabled = !busy && !started && consent && peer != null && outgoing != null && (!offline || medium != null),
                    onClick = {
                        started = true
                        busy = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    val chain = checkNotNull(manager.recoveryChainContext()) { "C_MISSING_DEPENDENCY" }
                                    val stored = slot.read()
                                    check(stored?.conflictingStatusChain == null) { "C_RECOVERY_SHARE_INVALID" }
                                    check(offline || stored?.compromised != true) { "C_RECOVERY_SHARE_COMPROMISED" }
                                    val artifact =
                                        if (offline) {
                                            PallaSyncRecoveryOfflineMedia.read(context, checkNotNull(medium), chain.adminPublicKey, null)
                                        } else {
                                            checkNotNull(stored) { "C_RECOVERY_WRAP_KEY_UNAVAILABLE" }.packageJson
                                        }
                                    val setId =
                                        Json
                                            .parseToJsonElement(artifact)
                                            .jsonObject
                                            .getValue("record")
                                            .jsonObject
                                            .getValue("recovery_set_id")
                                            .jsonPrimitive.content
                                    owner.begin(
                                        chain,
                                        artifact,
                                        PallaSyncRecoveryLocalFiles.read(context, checkNotNull(peer)),
                                        offline,
                                        store.read(setId),
                                        offline && stored != null,
                                        consent,
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
                    },
                )
                TextButton(stringResource(R.string.action_cancel), onClick = onDismiss)
            }
        }
    }
}
