package com.yunfie.illustia.ui.screens.pallasync

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncRecoveryHolder
import com.yunfie.illustia.pallasync.PallaSyncRecoveryLocalFiles
import com.yunfie.illustia.pallasync.PallaSyncRecoveryReleaseOwner
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

/** §§7.11.5/18.7: a fresh authentication and explicit consent follow both live SAS confirmations. */
@Composable
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal fun PallaSyncRecoveryReleaseControls(
    owner: PallaSyncRecoveryReleaseOwner,
    outgoing: Uri,
    offline: Boolean,
    holder: PallaSyncRecoveryHolder,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.pallaSyncInputActivity() as? FragmentActivity
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(owner.stage) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var credentialInput by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }

    suspend fun write(original: String) {
        pending = original
        withContext(Dispatchers.IO) { PallaSyncRecoveryLocalFiles.write(context, outgoing, original) }
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
                owner.close()
                throw cancelled
            } catch (failure: Exception) {
                if (failure is IllegalStateException) {
                    owner.close()
                    pending = null
                }
                failed = true
            } finally {
                stage = owner.stage
                busy = false
            }
        }
    }
    LaunchedEffect(owner) {
        delay(300_000)
        if (owner.stage != PallaSyncRecoveryReleaseOwner.Stage.RELEASED) {
            owner.close()
            pending = null
            stage = owner.stage
        }
    }
    val enabled = !busy && pending == null && stage != PallaSyncRecoveryReleaseOwner.Stage.CLOSED
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.pallasync_recovery_reader_warning))
        Text(stringResource(R.string.pallasync_recovery_receive_identity, owner.holderInstanceId.orEmpty()))
        Text(stringResource(R.string.pallasync_recovery_receive_class, owner.protection.orEmpty()))
        if (failed) Text(stringResource(R.string.pallasync_recovery_action_failed))
        if (stage == PallaSyncRecoveryReleaseOwner.Stage.CLOSED) Text(stringResource(R.string.pallasync_recovery_fresh_session_needed))
        if (pending != null && stage != PallaSyncRecoveryReleaseOwner.Stage.CLOSED) {
            TextButton(stringResource(R.string.pallasync_recovery_retry_file), enabled = !busy, onClick = {
                val original = checkNotNull(pending)
                run { write(original) }
            })
        }
        if (stage == PallaSyncRecoveryReleaseOwner.Stage.SAS) {
            PallaSyncRecoveryDescriptorQr(checkNotNull(owner.descriptor))
            TextButton(
                stringResource(R.string.pallasync_recovery_receive_write_descriptor),
                enabled = enabled,
                onClick = { run { write(checkNotNull(owner.descriptor)) } },
            )
            Text(stringResource(R.string.pallasync_recovery_live_sas, owner.visual.orEmpty()))
            TextButton(stringResource(R.string.pallasync_recovery_confirm_sas), enabled = enabled, onClick = { confirming = true })
        }
        if (stage == PallaSyncRecoveryReleaseOwner.Stage.READY) {
            TextButton(
                stringResource(R.string.pallasync_recovery_release_consent),
                enabled = enabled && (offline || activity != null),
                onClick = {
                    if (offline) {
                        credentialInput = true
                    } else {
                        run { write(owner.releaseDevice(holder, checkNotNull(activity), true)) }
                    }
                },
            )
        }
        if (stage == PallaSyncRecoveryReleaseOwner.Stage.RELEASED) {
            Text(stringResource(R.string.pallasync_recovery_release_done))
            TextButton(
                stringResource(R.string.pallasync_recovery_retry_file),
                enabled = enabled,
                onClick = { run { write(owner.originalPacket()) } },
            )
        }
        TextButton(stringResource(R.string.action_cancel), onClick = onDismiss)
    }
    if (confirming) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_recovery_confirm_sas),
            summary = stringResource(R.string.pallasync_recovery_sas_guidance, owner.visual.orEmpty()),
            confirmText = stringResource(R.string.action_confirm),
            onDismiss = { confirming = false },
            onConfirm = {
                confirming = false
                run { owner.confirm(true) }
            },
        )
    }
    if (credentialInput) {
        PallaSyncRecoveryCredentialDialog(onDismiss = { credentialInput = false }, onConfirm = { credential ->
            credentialInput = false
            run {
                try {
                    val original = withContext(Dispatchers.IO) { owner.releaseOffline(credential, true) }
                    write(original)
                } finally {
                    credential.fill(0)
                }
            }
        })
    }
}
