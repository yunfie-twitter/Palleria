package com.yunfie.illustia.ui.screens.pallasync

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncDeletionEvidenceArchive
import com.yunfie.illustia.pallasync.PallaSyncPeerTransferFile
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.PallaSyncDeviceEntity
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

// SAF/JCS boundary maps provider and malformed-file failures to protocol-safe UI outcomes.
@Composable
@Suppress("TooGenericExceptionCaught", "ThrowsCount")
fun PallaSyncDevicesScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onDeviceClick: (String, String) -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val context = LocalContext.current
    val syncManager = remember { PalleriaSyncManager(context = context) }
    val scrollBehavior = MiuixScrollBehavior()
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<PallaSyncDeviceEntity>>(emptyList()) }
    var peerOffer by remember { mutableStateOf<String?>(null) }
    var offerTranscript by remember { mutableStateOf<com.yunfie.illustia.pallasync.PallaSyncPeerJournal.Transcript?>(null) }
    var approvalWork by remember { mutableStateOf<com.yunfie.illustia.pallasync.PallaSyncPeerApprovalWork?>(null) }
    var peerBusy by remember { mutableStateOf(false) }
    var peerError by remember { mutableStateOf<String?>(null) }
    var pendingPeerTransfer by remember { mutableStateOf<String?>(null) }
    var acceptedPeerResponse by remember { mutableStateOf<String?>(null) }
    var acceptedPeerTranscript by remember { mutableStateOf<com.yunfie.illustia.pallasync.PallaSyncPeerJournal.Transcript?>(null) }
    var submittedPeerBody by remember { mutableStateOf<String?>(null) }
    var creatingOffer by remember { mutableStateOf(false) }
    var offerError by remember { mutableStateOf<String?>(null) }
    val offerBitmap =
        remember(peerOffer) {
            peerOffer?.let {
                runCatching {
                    BarcodeEncoder()
                        .encodeBitmap(
                            it,
                            BarcodeFormat.QR_CODE,
                            768,
                            768,
                        ).asImageBitmap()
                }.getOrNull()
            }
        }

    val peerTransferExporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val value = pendingPeerTransfer
            if (uri != null && value != null) {
                scope.launch {
                    try {
                        val returned =
                            withContext(Dispatchers.IO) {
                                val bytes = PallaSyncPeerTransferFile.encode(value)
                                checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "C_LOCAL_STORAGE_FULL" }.use {
                                    it.write(bytes)
                                    it.flush()
                                }
                                checkNotNull(context.contentResolver.openInputStream(uri)) { "C_MISSING_DEPENDENCY" }.use { input ->
                                    PallaSyncPeerTransferFile
                                        .read(input)
                                        .toByteArray(Charsets.UTF_8)
                                        .also { check(it.contentEquals(bytes)) { "C_MISSING_DEPENDENCY" } }
                                }
                            }
                        check(returned.toString(Charsets.UTF_8) == value) { "C_MISSING_DEPENDENCY" }
                        Toast.makeText(context, R.string.pallasync_peer_file_exported, Toast.LENGTH_LONG).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        peerError = failure.message?.takeIf { it.startsWith("C_") } ?: "C_LOCAL_STORAGE_FULL"
                    } finally {
                        pendingPeerTransfer = null
                    }
                }
            } else {
                pendingPeerTransfer = null
            }
        }

    val peerApprovalImporter =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    try {
                        val bytes =
                            withContext(Dispatchers.IO) {
                                checkNotNull(context.contentResolver.openInputStream(uri)) { "C_MISSING_DEPENDENCY" }.use { input ->
                                    PallaSyncPeerTransferFile.read(input).toByteArray(Charsets.UTF_8)
                                }
                            }
                        val value = PallaSyncDeletionEvidenceArchive.decodeUtf8(bytes)
                        val objectValue = PallaSyncDeletionEvidenceArchive.parseStrictObject(value)
                        check(objectValue.keys == setOf("device_record", "envelopes", "admin_operation")) { "C_SCHEMA_VIOLATION" }
                        submittedPeerBody = value
                        peerError = null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        peerError = failure.message?.takeIf { it.startsWith("C_") } ?: "C_SCHEMA_VIOLATION"
                    }
                }
            }
        }

    DisposableEffect(approvalWork?.session?.handle) {
        onDispose { approvalWork?.close() }
    }

    val cancelApproval = {
        val work = approvalWork
        approvalWork = null
        if (work != null) {
            scope.launch {
                runCatching { syncManager.cancelPeerEnrollmentApproval(work) }
            }
        }
    }

    LaunchedEffect(settings.pallaSyncEnabled) {
        if (!settings.pallaSyncEnabled) return@LaunchedEffect

        val dao = PallaSyncDatabase.getDatabase(context).pallaSyncDao()
        devices = withContext(Dispatchers.IO) { dao.getAllDevices() }

        val chainState = withContext(Dispatchers.IO) { dao.getActiveChainStates() }.firstOrNull()
        if (chainState != null) {
            syncManager.fetchDevices(syncManager.getServerUrl(), chainState.chainId)
            devices = withContext(Dispatchers.IO) { dao.getAllDevices() }
        }

        while (isActive) {
            delay(2_000)
            devices = withContext(Dispatchers.IO) { dao.getAllDevices() }
        }
    }

    if (peerOffer != null) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.pallasync_peer_offer_title),
            summary = stringResource(R.string.pallasync_peer_offer_guidance),
            onDismissRequest = { peerOffer = null },
        ) {
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                offerBitmap?.let { Image(bitmap = it, contentDescription = stringResource(R.string.pallasync_peer_offer_qr)) }
                if (offerBitmap == null) Text(stringResource(R.string.pallasync_peer_offer_qr_failed))
                Text(peerOffer.orEmpty())
                Text(stringResource(R.string.pallasync_peer_authority_guidance))
                TextButton(
                    text = stringResource(R.string.pallasync_peer_authority_export),
                    enabled = !peerBusy && offerTranscript != null,
                    onClick = {
                        val transcript = offerTranscript ?: return@TextButton
                        peerBusy = true
                        peerError = null
                        scope.launch {
                            try {
                                pendingPeerTransfer = syncManager.peerAuthorityForOffer(transcript)
                                peerTransferExporter.launch("pallasync-authority-${transcript.requestId}.json")
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                peerError = pallaSyncFailureCode(failure) ?: "unknown"
                            } finally {
                                peerBusy = false
                            }
                        }
                    },
                )
                TextButton(
                    text =
                        stringResource(
                            if (peerBusy) R.string.pallasync_peer_request_checking else R.string.pallasync_peer_request_check,
                        ),
                    enabled = !peerBusy && offerTranscript != null,
                    onClick = {
                        val transcript = offerTranscript ?: return@TextButton
                        peerBusy = true
                        peerError = null
                        scope.launch {
                            try {
                                val item = syncManager.pollPeerEnrollmentRequest(transcript)
                                if (item == null) {
                                    Toast.makeText(context, R.string.pallasync_peer_request_none, Toast.LENGTH_LONG).show()
                                } else {
                                    approvalWork = syncManager.beginPeerEnrollmentApproval(transcript, item)
                                    submittedPeerBody = null
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                peerError = failure.message?.takeIf { it.startsWith("C_") } ?: "C_BOOTSTRAP_TRUST_REQUIRED"
                            } finally {
                                peerBusy = false
                            }
                        }
                    },
                )
                PallaSyncErrorHint(peerError)
                TextButton(text = stringResource(R.string.action_cancel), onClick = { peerOffer = null })
            }
        }
    }

    approvalWork?.let { work ->
        val transcript = work.transcript
        OverlayDialog(
            show = true,
            title = stringResource(R.string.pallasync_peer_approval_title),
            summary = stringResource(R.string.pallasync_peer_approval_guidance),
            onDismissRequest = cancelApproval,
        ) {
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (transcript.stage == com.yunfie.illustia.pallasync.PallaSyncPeerJournal.Stage.BOUND) {
                    Text(work.sas)
                    Text(stringResource(R.string.pallasync_peer_sas_compare))
                    Button(
                        enabled = !peerBusy,
                        onClick = {
                            peerBusy = true
                            peerError = null
                            scope.launch {
                                try {
                                    val transferred = syncManager.confirmPeerEnrollmentSas(work)
                                    approvalWork = work.copy(transcript = transferred)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    peerError = failure.message?.takeIf { it.startsWith("C_") } ?: "C_BOOTSTRAP_TRUST_REQUIRED"
                                } finally {
                                    peerBusy = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.pallasync_peer_sas_confirm))
                    }
                }
                if (transcript.stage == com.yunfie.illustia.pallasync.PallaSyncPeerJournal.Stage.TRANSFERRED) {
                    Text(stringResource(R.string.pallasync_peer_transfer_guidance))
                    TextButton(
                        text = stringResource(R.string.pallasync_peer_transfer_export),
                        enabled = !peerBusy,
                        onClick = {
                            val certificate = transcript.certificate
                            val envelopes = transcript.envelopes
                            if (certificate == null || envelopes.isNullOrEmpty()) {
                                peerError = "C_MISSING_DEPENDENCY"
                            } else {
                                pendingPeerTransfer =
                                    buildJsonObject {
                                        put("certificate", certificate)
                                        put("envelopes", JsonArray(envelopes))
                                    }.toString()
                                peerTransferExporter.launch("pallasync-certificate-envelope-${transcript.requestId}.json")
                            }
                        },
                    )
                    TextButton(
                        text = stringResource(R.string.pallasync_peer_body_import),
                        enabled = !peerBusy,
                        onClick = { peerApprovalImporter.launch(arrayOf("application/json", "text/json", "*/*")) },
                    )
                    submittedPeerBody?.let {
                        Text(stringResource(R.string.pallasync_peer_body_ready))
                        Button(
                            enabled = !peerBusy,
                            onClick = {
                                val body = submittedPeerBody ?: return@Button
                                peerBusy = true
                                peerError = null
                                scope.launch {
                                    try {
                                        val accepted = syncManager.approvePeerEnrollment(work, body)
                                        acceptedPeerResponse = accepted.acceptance?.toString() ?: error("C_ACCEPTANCE_UNPROVEN")
                                        acceptedPeerTranscript = accepted
                                        approvalWork = null
                                        submittedPeerBody = null
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        peerError = failure.message?.takeIf { it.startsWith("C_") } ?: "C_ACCEPTANCE_UNPROVEN"
                                        if (work.transcript.stage == com.yunfie.illustia.pallasync.PallaSyncPeerJournal.Stage.SUBMITTED) {
                                            approvalWork = work.copy(transcript = work.transcript)
                                        } else {
                                            approvalWork = null
                                        }
                                    } finally {
                                        peerBusy = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.pallasync_peer_approval_submit))
                        }
                    }
                }
                if (transcript.stage == com.yunfie.illustia.pallasync.PallaSyncPeerJournal.Stage.SUBMITTED) {
                    Text(stringResource(R.string.pallasync_peer_approval_unknown))
                    Button(
                        enabled = !peerBusy,
                        onClick = {
                            peerBusy = true
                            peerError = null
                            scope.launch {
                                try {
                                    val accepted = syncManager.retryPeerEnrollmentApproval(work.transcript)
                                    acceptedPeerResponse = accepted.acceptance?.toString() ?: error("C_ACCEPTANCE_UNPROVEN")
                                    acceptedPeerTranscript = accepted
                                    approvalWork = null
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    peerError = failure.message?.takeIf { it.startsWith("C_") } ?: "C_ACCEPTANCE_UNPROVEN"
                                } finally {
                                    peerBusy = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.pallasync_peer_approval_retry))
                    }
                }
                PallaSyncErrorHint(peerError)
                TextButton(text = stringResource(R.string.action_cancel), onClick = cancelApproval)
            }
        }
    }

    acceptedPeerResponse?.let { response ->
        OverlayDialog(
            show = true,
            title = stringResource(R.string.pallasync_peer_approved_title),
            summary = stringResource(R.string.pallasync_peer_approved_guidance),
            onDismissRequest = { acceptedPeerResponse = null },
        ) {
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.pallasync_peer_approval_receipt_export),
                    enabled = !peerBusy,
                    onClick = {
                        val transcript = acceptedPeerTranscript ?: return@TextButton
                        peerBusy = true
                        peerError = null
                        scope.launch {
                            try {
                                pendingPeerTransfer = syncManager.peerAcceptanceTransfer(transcript)
                                peerTransferExporter.launch("pallasync-approval-response.json")
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                peerError = pallaSyncFailureCode(failure) ?: "unknown"
                            } finally {
                                peerBusy = false
                            }
                        }
                    },
                )
                PallaSyncErrorHint(peerError)
                TextButton(text = stringResource(R.string.action_cancel), onClick = { acceptedPeerResponse = null })
            }
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.login_feature_sync),
                largeTitle = stringResource(R.string.login_feature_sync),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .background(MiuixTheme.colorScheme.surface),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = scaffoldPadding.calculateTopPadding() + 16.dp,
                    bottom = 96.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Section(stringResource(R.string.pallasync_participating_devices)) {
                    ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                        Button(
                            onClick = {
                                if (!creatingOffer) {
                                    creatingOffer = true
                                    offerError = null
                                    scope.launch {
                                        try {
                                            val transcript = syncManager.createPeerDeviceOffer()
                                            offerTranscript = transcript
                                            peerOffer = transcript.offer.toString()
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            // Relay rejections surface as PallaSyncInitialHistory.Failure,
                                            // which is not an IllegalStateException; catching only that
                                            // left the QR offer failing with no feedback at all.
                                            offerError = pallaSyncFailureCode(failure) ?: "unknown"
                                        } finally {
                                            creatingOffer = false
                                        }
                                    }
                                }
                            },
                            enabled = settings.pallaSyncEnabled && !creatingOffer,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (creatingOffer) R.string.pallasync_peer_offer_creating else R.string.pallasync_peer_offer_create,
                                ),
                            )
                        }
                        offerError?.let { code ->
                            Text(
                                text = pallaSyncErrorGuidance(code),
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MiuixTheme.colorScheme.error,
                                style = MiuixTheme.textStyles.body2,
                            )
                            Text(
                                text = code,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                style = MiuixTheme.textStyles.footnote1,
                            )
                        }
                        DividerLine()
                        if (devices.isEmpty()) {
                            Text(
                                text = stringResource(R.string.pallasync_no_participating_devices),
                                modifier = Modifier.padding(18.dp),
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                style = MiuixTheme.textStyles.body2,
                            )
                        } else {
                            devices.forEachIndexed { index, device ->
                                SettingLinkRow(
                                    title = device.deviceName,
                                    summary = stringResource(R.string.pallasync_device_id, device.deviceId),
                                    onClick = { onDeviceClick(device.deviceId, device.deviceName) },
                                )
                                if (index < devices.lastIndex) {
                                    DividerLine()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
