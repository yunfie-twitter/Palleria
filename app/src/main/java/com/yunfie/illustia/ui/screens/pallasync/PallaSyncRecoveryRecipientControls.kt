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
import com.yunfie.illustia.pallasync.PallaSyncRecoveryLocalFiles
import com.yunfie.illustia.pallasync.PallaSyncRecoveryOfflineMedia
import com.yunfie.illustia.pallasync.PallaSyncRecoveryRecipient
import com.yunfie.illustia.pallasync.PallaSyncRecoveryRecipientOwner
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

/** §§7.11/18.6: only verified persistence/readback produces the original encrypted acknowledgement. */
@Composable
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal fun PallaSyncRecoveryRecipientControls(
    owner: PallaSyncRecoveryRecipientOwner,
    files: Map<RecoveryReceiveFile, Uri>,
    admin: String,
    offline: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.pallaSyncInputActivity() as? FragmentActivity
    val scope = rememberCoroutineScope()
    val recipient = remember(owner) { owner.current() }
    var stage by remember { mutableStateOf(recipient.stage) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var credentialInput by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }

    suspend fun write(original: String) {
        pending = original
        withContext(Dispatchers.IO) { PallaSyncRecoveryLocalFiles.write(context, files.getValue(RecoveryReceiveFile.OUTGOING), original) }
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
                if (failure is CharacterCodingException || failure is IllegalStateException) {
                    owner.close()
                    pending = null
                }
                failed = true
            } finally {
                stage = recipient.stage
                busy = false
            }
        }
    }
    LaunchedEffect(recipient) {
        delay(300_000)
        if (recipient.stage != PallaSyncRecoveryRecipient.Stage.PERSISTED) {
            owner.close()
            pending = null
            stage = recipient.stage
        }
    }
    val enabled = !busy && pending == null && stage != PallaSyncRecoveryRecipient.Stage.CLOSED
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.pallasync_recovery_receive_class, owner.protection.orEmpty()))
        Text(stringResource(R.string.pallasync_recovery_receive_identity, recipient.holderInstanceId))
        Text(stringResource(R.string.pallasync_recovery_setup_live_guidance))
        if (failed) Text(stringResource(R.string.pallasync_recovery_action_failed))
        if (stage == PallaSyncRecoveryRecipient.Stage.CLOSED) Text(stringResource(R.string.pallasync_recovery_fresh_session_needed))
        if (pending != null && stage != PallaSyncRecoveryRecipient.Stage.CLOSED) {
            TextButton(stringResource(R.string.pallasync_recovery_retry_file), enabled = !busy, onClick = {
                val original = checkNotNull(pending)
                run { write(original) }
            })
        }
        if (stage == PallaSyncRecoveryRecipient.Stage.SAS) {
            PallaSyncRecoveryDescriptorQr(recipient.descriptor)
            TextButton(
                stringResource(R.string.pallasync_recovery_receive_write_descriptor),
                enabled = enabled,
                onClick = { run { write(recipient.descriptor) } },
            )
            Text(stringResource(R.string.pallasync_recovery_live_sas, recipient.visual))
            TextButton(stringResource(R.string.pallasync_recovery_confirm_sas), enabled = enabled, onClick = { confirming = true })
        }
        if (stage == PallaSyncRecoveryRecipient.Stage.RECEIVING) {
            TextButton(stringResource(R.string.pallasync_recovery_read_packet), enabled = enabled, onClick = {
                run {
                    val response =
                        withContext(Dispatchers.IO) {
                            recipient.receive(
                                PallaSyncRecoveryLocalFiles.read(context, files.getValue(RecoveryReceiveFile.INCOMING)),
                                System.currentTimeMillis(),
                            )
                        }
                    if (response != null) write(response)
                }
            })
        }
        if (stage == PallaSyncRecoveryRecipient.Stage.READY_TO_PERSIST) {
            TextButton(
                stringResource(R.string.pallasync_recovery_receive_persist),
                enabled = enabled && (offline || activity != null),
                onClick = {
                    if (offline) {
                        credentialInput = true
                    } else {
                        run { write(recipient.persistDevice(checkNotNull(activity))) }
                    }
                },
            )
        }
        if (stage == PallaSyncRecoveryRecipient.Stage.PERSISTED) {
            Text(stringResource(R.string.pallasync_recovery_receive_saved))
            TextButton(
                stringResource(R.string.pallasync_recovery_receive_retry_ack),
                enabled = enabled,
                onClick = { run { write(recipient.persistedAcknowledgement()) } },
            )
        }
        TextButton(stringResource(R.string.action_cancel), onClick = onDismiss)
    }
    if (confirming) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_recovery_confirm_sas),
            summary = stringResource(R.string.pallasync_recovery_sas_guidance, recipient.visual),
            confirmText = stringResource(R.string.action_confirm),
            onDismiss = { confirming = false },
            onConfirm = {
                confirming = false
                run { recipient.confirm(true) }
            },
        )
    }
    if (credentialInput) {
        PallaSyncRecoveryCredentialDialog(onDismiss = { credentialInput = false }, onConfirm = { credential ->
            credentialInput = false
            run {
                try {
                    val ack =
                        withContext(Dispatchers.IO) {
                            val original = recipient.prepareOfflinePackage(credential.copyOf())
                            val medium = files.getValue(RecoveryReceiveFile.PACKAGE)
                            PallaSyncRecoveryOfflineMedia.write(context, medium, original, admin, null)
                            val reopened = PallaSyncRecoveryOfflineMedia.read(context, medium, admin, null)
                            recipient.acknowledgeOfflineReadback(reopened, credential)
                        }
                    write(ack)
                } finally {
                    credential.fill(0)
                }
            }
        })
    }
}
