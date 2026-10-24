package com.yunfie.illustia.ui.screens.pallasync

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.yunfie.illustia.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

/** Explicit §19.3 capacity recovery; merely showing this control never retries or clears data. */
@Composable
internal fun PallaSyncLocalStorageRecovery(
    count: Long,
    retry: suspend () -> Int,
) {
    var busy by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf<Int?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column {
        if (count > 0) {
            Text(stringResource(R.string.pallasync_storage_recovery_help, count))
            PallaSyncActionButton(
                text =
                    stringResource(
                        if (busy) R.string.pallasync_pending_evidence_retry_running else R.string.pallasync_storage_recovery_retry,
                    ),
                enabled = !busy,
                onClick = {
                    busy = true
                    failed = false
                    applied = null
                    scope.launch {
                        try {
                            applied = retry()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }
        if (failed) Text(stringResource(R.string.pallasync_storage_recovery_failed))
        applied?.let { Text(stringResource(R.string.pallasync_pending_evidence_retry_result, it)) }
    }
}
