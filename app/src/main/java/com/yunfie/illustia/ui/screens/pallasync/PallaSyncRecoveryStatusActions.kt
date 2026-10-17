package com.yunfie.illustia.ui.screens.pallasync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncRecoveryManagement
import com.yunfie.illustia.pallasync.PallaSyncRecoveryPublicStatusMedia
import com.yunfie.illustia.pallasync.PallaSyncRecoverySetStore
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

/** Signed retirement/status-array exchange (§§18.6–18.7); no new transfer kind or cryptography. */
@Composable
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal fun PallaSyncRecoveryStatusActions(
    management: PallaSyncRecoveryManagement,
    manager: PalleriaSyncManager,
    entry: PallaSyncRecoverySetStore.Entry,
    busy: Boolean,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var signing by remember(entry.id) { mutableStateOf(false) }
    var working by remember(entry.id) { mutableStateOf(false) }
    var failed by remember(entry.id) { mutableStateOf(false) }
    var selected by remember(entry.id) { mutableStateOf<PallaSyncRecoverySetStore.Entry?>(null) }

    // OS/provider errors become fixed UI messages; exceptions and artifacts never enter diagnostics.
    fun run(action: suspend () -> Unit) {
        working = true
        failed = false
        scope.launch {
            try {
                action()
                onChanged()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            } finally {
                selected = null
                working = false
            }
        }
    }
    val save =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val target = selected
            if (uri != null && target != null) {
                run {
                    withContext(Dispatchers.IO) { PallaSyncRecoveryPublicStatusMedia.write(context, uri, target) }
                }
            } else {
                selected = null
            }
        }
    val load =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val target = selected
            if (uri != null && target != null) {
                run {
                    val statuses = withContext(Dispatchers.IO) { PallaSyncRecoveryPublicStatusMedia.read(context, uri, target) }
                    management.importStatuses(target, statuses)
                }
            } else {
                selected = null
            }
        }
    val enabled = !busy && !working && selected == null
    if (failed) Text(stringResource(R.string.pallasync_recovery_action_failed))
    Text(stringResource(R.string.pallasync_recovery_public_status_guidance))
    TextButton(text = stringResource(R.string.pallasync_recovery_save_status), enabled = enabled, onClick = {
        selected = entry
        save.launch("pallasync-recovery-status.json")
    })
    TextButton(text = stringResource(R.string.pallasync_recovery_load_status), enabled = enabled, onClick = {
        selected = entry
        load.launch(arrayOf("application/json"))
    })
    TextButton(
        text = stringResource(R.string.pallasync_recovery_sign_retirement),
        enabled = enabled && entry.state in setOf("prepared", "active") && entry.conflictingStatuses == null,
        onClick = { signing = true },
    )
    if (signing) {
        PallaSyncRootActionDialog(
            manager,
            stringResource(R.string.pallasync_recovery_sign_retirement),
            stringResource(R.string.pallasync_recovery_retirement_guidance),
            onDismiss = {
                signing = false
                onChanged()
            },
        ) { mnemonic, passphrase, cancellation ->
            try {
                management.retire(entry, mnemonic, passphrase, cancellation)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        }
    }
}
