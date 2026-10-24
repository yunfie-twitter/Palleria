package com.yunfie.illustia.ui.screens.pallasync

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncRecoveryDealerWorkflow
import com.yunfie.illustia.pallasync.PallaSyncRecoveryLocalFiles
import com.yunfie.illustia.pallasync.PallaSyncRecoverySetupOwner
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import java.nio.charset.CharacterCodingException
import kotlin.coroutines.coroutineContext

/** §§7.11.5/18.6: public descriptor QR and local encrypted files; no relay transport. */
@Composable
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal fun PallaSyncRecoveryDealerControls(
    owner: PallaSyncRecoverySetupOwner,
    incoming: Uri,
    outgoing: Uri,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val flow = remember(owner) { PallaSyncRecoveryDealerWorkflow(owner) }
    var evidence by remember { mutableStateOf(flow.evidence) }
    var stage by remember { mutableStateOf(flow.stage) }
    var descriptor by remember { mutableStateOf<String?>(null) }
    var sas by remember { mutableStateOf<String?>(null) }
    var shares by remember { mutableStateOf(0) }
    var testing by remember { mutableStateOf(false) }
    var tested by remember { mutableStateOf(false) }
    var selectedHolder by remember { mutableStateOf<Int?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var interactionRevision by remember(flow) { mutableStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var confirmSas by remember { mutableStateOf(false) }
    val currentStage by rememberUpdatedState(stage)

    suspend fun writeOriginal(original: String) {
        // Exact bytes remain available for local IO retry; no new encryption/sequence is manufactured.
        pending = original
        withContext(Dispatchers.IO) {
            coroutineContext.ensureActive()
            PallaSyncRecoveryLocalFiles.write(context, outgoing, original)
        }
        pending = null
    }

    fun run(action: suspend () -> Unit) {
        if (busy) return
        interactionRevision += 1
        busy = true
        failed = false
        scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (failure is CharacterCodingException || failure.message == "C_RECOVERY_SHARE_INVALID") {
                    runCatching { flow.close() }
                    pending = null
                }
                failed = true
            } finally {
                stage = flow.stage
                descriptor = flow.descriptor
                sas = flow.visual
                shares = flow.distinctShares
                testing = flow.testing
                tested = flow.thresholdTestPassed
                if (stage != PallaSyncRecoveryDealerWorkflow.Stage.CLOSED) evidence = flow.evidence
                busy = false
            }
        }
    }
    DisposableEffect(flow, lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP &&
                    currentStage !in setOf(PallaSyncRecoveryDealerWorkflow.Stage.ACTIVE, PallaSyncRecoveryDealerWorkflow.Stage.CLOSED)
                ) {
                    pending = null
                    descriptor = null
                    sas = null
                    shares = 0
                    runCatching { flow.close() }
                    stage = flow.stage
                }
            }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            pending = null
            runCatching { flow.close() }
        }
    }
    LaunchedEffect(flow, interactionRevision, busy, stage, confirmSas) {
        if (!busy && !confirmSas &&
            stage !in setOf(PallaSyncRecoveryDealerWorkflow.Stage.ACTIVE, PallaSyncRecoveryDealerWorkflow.Stage.CLOSED)
        ) {
            delay(PALLASYNC_RECOVERY_SESSION_IDLE_TIMEOUT_MS)
            runCatching { flow.close() }
            pending = null
            stage = flow.stage
        }
    }
    val enabled = !busy && pending == null && stage != PallaSyncRecoveryDealerWorkflow.Stage.CLOSED
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.pallasync_recovery_setup_live_guidance))
        Text(stringResource(R.string.pallasync_recovery_set_id, evidence.id))
        Text(stringResource(R.string.pallasync_recovery_capacity, evidence.usable, evidence.threshold, evidence.total))
        if (failed) Text(stringResource(R.string.pallasync_recovery_action_failed))
        if (owner.auditStorageFailed) Text(stringResource(R.string.pallasync_recovery_audit_storage_failed))
        if (stage == PallaSyncRecoveryDealerWorkflow.Stage.CLOSED) Text(stringResource(R.string.pallasync_recovery_fresh_session_needed))
        if (pending != null && stage != PallaSyncRecoveryDealerWorkflow.Stage.CLOSED) {
            TextButton(stringResource(R.string.pallasync_recovery_retry_file), enabled = !busy, onClick = {
                val original = checkNotNull(pending)
                run { writeOriginal(original) }
            })
        }
        TextButton(stringResource(R.string.pallasync_recovery_write_manifest), enabled = enabled, onClick = {
            run { writeOriginal(evidence.manifestJson) }
        })
        TextButton(stringResource(R.string.pallasync_recovery_save_status), enabled = enabled, onClick = {
            run { writeOriginal(evidence.statusesJson) }
        })
        if (!testing && !tested && stage == PallaSyncRecoveryDealerWorkflow.Stage.READY) {
            evidence.holders.forEachIndexed { index, holder ->
                TextButton(
                    stringResource(R.string.pallasync_recovery_distribute_to, index + 1),
                    enabled = enabled && !holder.available,
                    onClick = {
                        selectedHolder = index + 1
                        run {
                            val original = withContext(Dispatchers.IO) { flow.beginDistribution(index + 1) }
                            writeOriginal(original)
                        }
                    },
                )
            }
            TextButton(
                stringResource(R.string.pallasync_recovery_start_threshold_test),
                enabled = enabled && evidence.usable == evidence.total,
                onClick = {
                    selectedHolder = null
                    run { withContext(Dispatchers.IO) { flow.beginThresholdTest(true) } }
                },
            )
        }
        if (testing && stage == PallaSyncRecoveryDealerWorkflow.Stage.READY) {
            Text(stringResource(R.string.pallasync_recovery_collected_count, shares, evidence.threshold))
            listOf(false, true).forEach { offline ->
                TextButton(
                    stringResource(if (offline) R.string.pallasync_recovery_collect_reader else R.string.pallasync_recovery_collect_holder),
                    enabled = enabled,
                    onClick = {
                        run {
                            val original = withContext(Dispatchers.IO) { flow.beginCollectionPair(offline) }
                            writeOriginal(original)
                        }
                    },
                )
            }
            TextButton(
                stringResource(R.string.pallasync_recovery_finish_threshold_test),
                enabled = enabled && shares >= evidence.threshold,
                onClick = { run { withContext(Dispatchers.IO) { flow.finishThresholdTest(true) } } },
            )
        }
        descriptor?.let { original ->
            PallaSyncRecoveryDescriptorQr(original)
            Text(stringResource(R.string.pallasync_recovery_session_recipient, selectedHolder ?: 0))
        }
        if (stage == PallaSyncRecoveryDealerWorkflow.Stage.SAS) {
            TextButton(stringResource(R.string.pallasync_recovery_read_peer_descriptor), enabled = enabled, onClick = {
                run { withContext(Dispatchers.IO) { flow.bind(PallaSyncRecoveryLocalFiles.read(context, incoming)) } }
            })
            sas?.let { visual ->
                Text(stringResource(R.string.pallasync_recovery_live_sas, visual))
                TextButton(stringResource(R.string.pallasync_recovery_confirm_sas), enabled = enabled, onClick = { confirmSas = true })
            }
        }
        if (stage == PallaSyncRecoveryDealerWorkflow.Stage.DISTRIBUTING || stage == PallaSyncRecoveryDealerWorkflow.Stage.COLLECTING) {
            TextButton(stringResource(R.string.pallasync_recovery_read_packet), enabled = enabled, onClick = {
                run {
                    val response = withContext(Dispatchers.IO) { flow.receive(PallaSyncRecoveryLocalFiles.read(context, incoming)) }
                    if (response != null) writeOriginal(response)
                }
            })
        }
        if (tested && stage == PallaSyncRecoveryDealerWorkflow.Stage.READY) {
            TextButton(stringResource(R.string.pallasync_recovery_activate), enabled = enabled, onClick = {
                run { writeOriginal(withContext(Dispatchers.IO) { flow.activate(System.currentTimeMillis()) }) }
            })
        }
        if (stage == PallaSyncRecoveryDealerWorkflow.Stage.ACTIVE) {
            Text(stringResource(R.string.pallasync_recovery_activation_propagation))
        }
        TextButton(stringResource(R.string.action_cancel), onClick = onDismiss)
    }
    if (confirmSas) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_recovery_confirm_sas),
            summary = stringResource(R.string.pallasync_recovery_sas_guidance, sas.orEmpty()),
            confirmText = stringResource(R.string.action_confirm),
            onDismiss = { confirmSas = false },
            onConfirm = {
                confirmSas = false
                run {
                    val original = withContext(Dispatchers.IO) { flow.confirm(true) }
                    if (original != null) writeOriginal(original)
                }
            },
        )
    }
}

@Composable
internal fun PallaSyncRecoveryDescriptorQr(descriptor: String) {
    val public = remember(descriptor) { Json.parseToJsonElement(descriptor).jsonObject }
    Text(
        stringResource(
            R.string.pallasync_recovery_pair_binding,
            public.getValue("chain_id").jsonPrimitive.content,
            public.getValue("generation").jsonPrimitive.content,
            public.getValue("recovery_set_id").jsonPrimitive.content,
            public.getValue("role").jsonPrimitive.content,
            public.getValue("purpose").jsonPrimitive.content,
            public.getValue("endpoint_id").jsonPrimitive.content,
        ),
    )
    val bitmap = remember(descriptor) { BarcodeEncoder().encodeBitmap(descriptor, BarcodeFormat.QR_CODE, 512, 512).asImageBitmap() }
    Image(bitmap, contentDescription = stringResource(R.string.pallasync_recovery_descriptor_qr), modifier = Modifier.fillMaxWidth())
}
