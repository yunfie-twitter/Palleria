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
import com.yunfie.illustia.pallasync.PallaSyncRootCancellation
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Retry the persisted identical Genesis request after collecting fresh, transient root input (§5.5/§10.2). */
@Composable
internal fun PallaSyncGenesisRetryDialog(
    manager: PalleriaSyncManager,
    origin: String,
    initialFailure: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cancellation = remember(origin) { PallaSyncRootCancellation() }
    var operation by remember(origin) { mutableStateOf<Job?>(null) }
    var busy by remember(origin) { mutableStateOf(false) }
    var failure by remember(origin) { mutableStateOf(initialFailure) }
    DisposableEffect(cancellation) {
        onDispose {
            operation?.cancel()
            cancellation.cancel()
        }
    }
    PallaSyncRootInputDialog(
        title = stringResource(R.string.pallasync_v3_genesis_retry_title),
        explanation = stringResource(R.string.pallasync_v3_genesis_retry_explanation),
        busy = busy,
        failureCode = failure,
        onDismiss = {
            operation?.cancel()
            cancellation.cancel()
            onDismiss()
        },
    ) { mnemonic, passphrase ->
        failure = ""
        busy = true
        val previousLog = PalleriaSyncManager.syncLogs.value.firstOrNull()
        operation =
            scope.launch {
                try {
                    val completed = manager.createGenesis(mnemonic, origin, passphrase, cancellation)
                    if (completed) {
                        Toast
                            .makeText(context, R.string.pallasync_v3_root_completed, Toast.LENGTH_LONG)
                            .show()
                        onDismiss()
                    } else {
                        failure =
                            PalleriaSyncManager.syncLogs.value
                                .firstOrNull()
                                ?.takeIf { it != previousLog }
                                ?.substringAfter("] ")
                                ?: context.getString(R.string.error_pallasync_create_chain_failed)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failure = context.getString(R.string.error_pallasync_create_chain_failed)
                } finally {
                    busy = false
                }
            }
    }
}
