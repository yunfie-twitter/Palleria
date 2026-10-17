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
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncRootCancellation
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** §5.5: the display, job and native input belong to the same cancellable foreground operation. */
@Composable
internal fun PallaSyncGenesisDialog(
    manager: PalleriaSyncManager,
    phrase: String,
    origin: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cancellation = remember(phrase, origin) { PallaSyncRootCancellation() }
    var operation by remember(phrase, origin) { mutableStateOf<Job?>(null) }
    var busy by remember(phrase, origin) { mutableStateOf(false) }
    DisposableEffect(cancellation) {
        onDispose {
            operation?.cancel()
            cancellation.cancel()
        }
    }
    PallaSyncMnemonicDialog(phrase, onDismiss = {
        operation?.cancel()
        cancellation.cancel()
        onDismiss()
    }, busy = busy, onSaved = { passphrase ->
        busy = true
        operation =
            scope.launch {
                try {
                    val completed = manager.createGenesis(phrase, origin, passphrase, cancellation)
                    Toast
                        .makeText(
                            context,
                            if (completed) R.string.pallasync_v3_root_completed else R.string.error_pallasync_create_chain_failed,
                            Toast.LENGTH_LONG,
                        ).show()
                    onDismiss()
                } finally {
                    busy = false
                }
            }
    })
}
