package com.yunfie.illustia.ui.screens.pallasync

import android.widget.Toast
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
import com.yunfie.illustia.pallasync.PallaSyncDeletionRequestResult
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.TextButton

/** §13.8/§10.15: require a verified new-root handoff and a separately exported signed delete proof. */
@Composable
internal fun PallaSyncNewRootRetirementControls(
    manager: PalleriaSyncManager,
    sourceChain: String,
    targetChain: String,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var requesting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var proof by remember { mutableStateOf<String?>(null) }
    var exportedProof by remember { mutableStateOf<String?>(null) }
    var inputVisible by remember { mutableStateOf(false) }
    var confirmErase by remember { mutableStateOf(false) }

    val exporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val content = proof
            if (uri == null || content == null) {
                Toast.makeText(context, R.string.pallasync_delete_export_required, Toast.LENGTH_LONG).show()
            } else {
                busy = true
                scope.launch {
                    try {
                        val readBack =
                            withContext(Dispatchers.IO) {
                                val bytes = content.toByteArray(Charsets.UTF_8)
                                checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "C_LOCAL_STORAGE_FULL" }.use { output ->
                                    output.write(bytes)
                                    output.flush()
                                }
                                val returned =
                                    checkNotNull(context.contentResolver.openInputStream(uri)) { "C_DELETION_UNVERIFIED" }
                                        .use { it.readBytes() }
                                check(returned.contentEquals(bytes)) { "C_DELETION_UNVERIFIED" }
                                returned.toString(Charsets.UTF_8)
                            }
                        exportedProof = readBack
                        confirmErase = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                    } finally {
                        busy = false
                    }
                }
            }
        }

    TextButton(
        text = stringResource(R.string.pallasync_recovery_new_root_retire_source),
        enabled = !busy,
        onClick = { inputVisible = true },
    )

    if (inputVisible) {
        PallaSyncRootInputDialog(
            title = stringResource(R.string.pallasync_recovery_new_root_retire_source),
            explanation =
                stringResource(
                    R.string.pallasync_recovery_new_root_retire_source_warning,
                    sourceChain,
                    targetChain,
                ),
            busy = busy,
            onDismiss = { inputVisible = false },
            onConfirm = { mnemonic, passphrase ->
                busy = true
                scope.launch {
                    try {
                        when (manager.requestNewRootSourceDeletion(sourceChain, targetChain, mnemonic, passphrase)) {
                            PallaSyncDeletionRequestResult.PROOF_READY_FOR_EXPORT -> {
                                proof = manager.pendingNewRootSourceDeletionProof(sourceChain, targetChain)
                                if (proof == null) {
                                    Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                                } else {
                                    exporter.launch("pallasync-old-chain-deletion-proof.json")
                                }
                            }

                            PallaSyncDeletionRequestResult.PENDING_EVIDENCE -> {
                                Toast.makeText(context, R.string.pallasync_v3_epoch_deletion_unverified, Toast.LENGTH_LONG).show()
                            }

                            PallaSyncDeletionRequestResult.FAILED -> {
                                Toast.makeText(context, R.string.error_pallasync_delete_chain_failed, Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.error_pallasync_delete_chain_failed, Toast.LENGTH_LONG).show()
                    } finally {
                        busy = false
                        inputVisible = false
                    }
                }
            },
        )
    }

    if (confirmErase) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_recovery_new_root_retire_source),
            summary = stringResource(R.string.pallasync_delete_export_written_confirm),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onDismiss = { confirmErase = false },
            onConfirm = {
                confirmErase = false
                val saved = exportedProof
                if (saved == null) {
                    Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                } else {
                    busy = true
                    scope.launch {
                        try {
                            if (
                                manager.completeNewRootSourceDeletionAfterExport(
                                    sourceChain,
                                    targetChain,
                                    saved,
                                    explicitlyConfirmedOfflineCopy = true,
                                )
                            ) {
                                proof = null
                                exportedProof = null
                                onDeleted()
                            } else {
                                Toast.makeText(context, R.string.error_pallasync_delete_chain_failed, Toast.LENGTH_LONG).show()
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            Toast.makeText(context, R.string.error_pallasync_delete_chain_failed, Toast.LENGTH_LONG).show()
                        } finally {
                            busy = false
                        }
                    }
                }
            },
        )
    }
}
