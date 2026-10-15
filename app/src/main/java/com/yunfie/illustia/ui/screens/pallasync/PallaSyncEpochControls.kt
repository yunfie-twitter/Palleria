package com.yunfie.illustia.ui.screens.pallasync

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.yunfie.illustia.pallasync.PallaSyncEpochStatus
import com.yunfie.illustia.pallasync.PallaSyncRootCancellation
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.Section
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

private enum class EpochAction(
    val title: Int,
    val explanation: Int,
    val reason: String?,
    val admin: Boolean = false,
) {
    FETCH(R.string.pallasync_v3_epoch_fetch, R.string.pallasync_v3_epoch_fetch_explanation, null),
    MANUAL(R.string.pallasync_v3_epoch_manual, R.string.pallasync_v3_epoch_manual_explanation, "manual"),
    SCHEDULED(R.string.pallasync_v3_epoch_scheduled, R.string.pallasync_v3_epoch_manual_explanation, "scheduled"),
    EMERGENCY(R.string.pallasync_v3_epoch_emergency, R.string.pallasync_v3_epoch_emergency_explanation, "emergency", true),
}

@Composable
internal fun PallaSyncEpochControls(
    manager: PalleriaSyncManager,
    synchronizationEnabled: Boolean = true,
) {
    var status by remember { mutableStateOf<PallaSyncEpochStatus?>(null) }
    var pending by remember { mutableStateOf(0L) }
    var selected by remember { mutableStateOf<EpochAction?>(null) }
    LaunchedEffect(manager) {
        while (isActive) {
            status = manager.getEpochStatus()
            pending = manager.getPendingLocalEditCount()
            delay(2_000)
        }
    }
    Section(stringResource(R.string.pallasync_v3_epoch_title)) {
        ElevatedPanel(contentPadding = PaddingValues(16.dp)) {
            Column {
                val current = status
                Text(
                    if (current ==
                        null
                    ) {
                        stringResource(R.string.pallasync_v3_epoch_unknown)
                    } else {
                        stringResource(R.string.pallasync_v3_epoch_number, current.storedEpoch, current.knownEpoch)
                    },
                )
                Text(stringResource(epochStateMessage(current?.writeState)))
                if (!synchronizationEnabled) Text(stringResource(R.string.pallasync_v3_sync_paused))
                if (pending > 0) Text(stringResource(R.string.pallasync_v3_unsigned_pending, pending))
                Text(
                    stringResource(
                        when {
                            current == null -> R.string.pallasync_v3_epoch_unknown
                            current.scheduledRotationMs == 0L -> R.string.pallasync_v3_epoch_schedule_disabled
                            current.rotationDue -> R.string.pallasync_v3_epoch_due
                            else -> R.string.pallasync_v3_epoch_waiting
                        },
                    ),
                )
                for (action in EpochAction.entries) {
                    val enabled =
                        synchronizationEnabled && current != null && (
                            action == EpochAction.FETCH || (
                                current.writeState == "active" &&
                                    (action.admin || current.deviceRotationAllowed) &&
                                    (action != EpochAction.SCHEDULED || current.rotationDue)
                            )
                        )
                    TextButton(text = stringResource(action.title), enabled = enabled, onClick = { selected = action })
                }
            }
        }
    }
    selected?.let { action ->
        PallaSyncRootActionDialog(manager, stringResource(action.title), stringResource(action.explanation), onDismiss = {
            selected = null
        }) { words, passphrase, cancellation ->
            if (action.reason ==
                null
            ) {
                manager.refreshEpochs(words, passphrase, cancellation)
            } else {
                manager.rotateEpoch(words, action.reason, admin = action.admin, passphrase = passphrase, cancellation = cancellation)
            }
        }
    }
}

@Composable
internal fun PallaSyncDeviceRevocationControl(
    manager: PalleriaSyncManager,
    deviceId: String,
) {
    var status by remember { mutableStateOf<PallaSyncEpochStatus?>(null) }
    var selected by remember { mutableStateOf(false) }
    LaunchedEffect(manager, deviceId) {
        while (isActive) {
            status = manager.getEpochStatus()
            delay(2_000)
        }
    }
    val current = status
    TextButton(
        text = stringResource(R.string.pallasync_v3_device_revoke),
        enabled =
            current != null && current.writeState == "active" && current.deviceId != deviceId &&
                deviceId in current.approvedDeviceIds,
        onClick = { selected = true },
    )
    if (selected) {
        PallaSyncRootActionDialog(
            manager,
            stringResource(R.string.pallasync_v3_device_revoke),
            stringResource(R.string.pallasync_v3_device_revoke_explanation, deviceId),
            onDismiss = { selected = false },
        ) { words, passphrase, cancellation ->
            manager.rotateEpoch(
                words,
                "emergency",
                revoked = listOf(deviceId),
                admin = true,
                passphrase = passphrase,
                cancellation = cancellation,
            )
        }
    }
}

private fun epochStateMessage(state: String?) =
    when (state) {
        "active" -> R.string.pallasync_v3_epoch_ready
        "epoch_pending_keys" -> R.string.pallasync_v3_epoch_keys_needed
        "epoch_rebase_required" -> R.string.pallasync_v3_epoch_edits_pending
        "integrity_paused" -> R.string.pallasync_v3_epoch_integrity_paused
        "device_revoked" -> R.string.pallasync_v3_epoch_device_revoked
        "deletion_unverified" -> R.string.pallasync_v3_epoch_deletion_unverified
        else -> R.string.pallasync_v3_epoch_unknown
    }

@Composable
internal fun PallaSyncRootActionDialog(
    manager: PalleriaSyncManager,
    title: String,
    explanation: String,
    onDismiss: () -> Unit,
    action: suspend (String, String, PallaSyncRootCancellation) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var failureCode by remember { mutableStateOf<String?>(null) }
    val cancellation = remember { PallaSyncRootCancellation() }
    DisposableEffect(manager) {
        onDispose {
            operation?.cancel()
            cancellation.cancel()
        }
    }
    PallaSyncRootInputDialog(title, explanation, busy, onDismiss = {
        operation?.cancel()
        cancellation.cancel()
        onDismiss()
    }, failureCode = failureCode, onConfirm = { words, passphrase ->
        busy = true
        failureCode = null
        operation =
            scope.launch {
                try {
                    val completed = action(words, passphrase, cancellation)
                    Toast
                        .makeText(
                            context,
                            if (completed) R.string.pallasync_v3_root_completed else R.string.pallasync_v3_root_paused,
                            Toast.LENGTH_LONG,
                        ).show()
                    onDismiss()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: IllegalStateException) {
                    failureCode = failure.message?.takeIf { it.startsWith("C_") }.orEmpty()
                } catch (_: Exception) {
                    failureCode = ""
                } finally {
                    busy = false
                }
            }
    })
}
