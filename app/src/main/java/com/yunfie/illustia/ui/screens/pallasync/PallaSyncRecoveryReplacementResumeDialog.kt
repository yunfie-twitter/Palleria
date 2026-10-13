package com.yunfie.illustia.ui.screens.pallasync

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncRecoveryManagement
import com.yunfie.illustia.pallasync.PallaSyncRecoverySetStore
import com.yunfie.illustia.pallasync.PallaSyncRootCancellation
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** §18.7: finish the public CAS with fresh root input; no dealer secrets survive interruption. */
@Composable
internal fun PallaSyncRecoveryReplacementResumeDialog(
    manager: PalleriaSyncManager,
    management: PallaSyncRecoveryManagement,
    entry: PallaSyncRecoverySetStore.Entry,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cancellation = remember { PallaSyncRootCancellation() }
    var busy by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(manager) {
        onDispose {
            operation?.cancel()
            cancellation.cancel()
        }
    }
    PallaSyncRootInputDialog(
        title = stringResource(R.string.pallasync_recovery_resume_replacement),
        explanation = stringResource(R.string.pallasync_recovery_resume_replacement_guidance),
        busy = busy,
        onDismiss = onDismiss,
    ) { mnemonic, passphrase ->
        busy = true
        operation =
            scope.launch {
                try {
                    management.finishReplacement(entry, mnemonic, passphrase, cancellation)
                    onDismiss()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IllegalStateException) {
                    Toast.makeText(context, R.string.pallasync_recovery_action_failed, Toast.LENGTH_LONG).show()
                } catch (_: IllegalArgumentException) {
                    Toast.makeText(context, R.string.pallasync_recovery_action_failed, Toast.LENGTH_LONG).show()
                } finally {
                    busy = false
                }
            }
    }
}
