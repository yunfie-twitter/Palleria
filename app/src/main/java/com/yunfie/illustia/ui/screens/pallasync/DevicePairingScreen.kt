package com.yunfie.illustia.ui.screens.pallasync

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncBootstrapReview
import com.yunfie.illustia.pallasync.PallaSyncDeletionEvidenceArchive
import com.yunfie.illustia.pallasync.PallaSyncPeerSubjectEnrollment
import com.yunfie.illustia.pallasync.PallaSyncPeerTransferFile
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** §§5.5/10.4/11.6/18.1: root and physically approved peer enrollment are separate ceremonies. */
@Composable
@Suppress(
    "TooGenericExceptionCaught",
    "ThrowsCount",
) // SAF/ZXing failures are normalized at the UI boundary; native failures keep protocol codes.
fun DevicePairingScreen(
    serverUrl: String,
    onBack: () -> Unit,
    onPairSuccess: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val context = LocalContext.current
    val manager = remember { PalleriaSyncManager(context = context) }
    val scope = rememberCoroutineScope()
    var trustSource by remember { mutableStateOf<String?>(null) }
    var showRoot by remember { mutableStateOf(false) }
    var joined by remember { mutableStateOf(false) }
    var peerSetup by remember { mutableStateOf(false) }
    var peerBusy by remember { mutableStateOf(false) }
    var peerError by remember { mutableStateOf<String?>(null) }
    var offerJson by remember { mutableStateOf<String?>(null) }
    var authorityJson by remember { mutableStateOf<String?>(null) }
    var authorityLabel by remember { mutableStateOf<String?>(null) }
    var work by remember { mutableStateOf<PallaSyncPeerSubjectEnrollment.Work?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    var candidate by remember { mutableStateOf<PallaSyncPeerSubjectEnrollment.Candidate?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var importPurpose by remember { mutableStateOf<ImportPurpose?>(null) }
    var bootstrapReviewState by remember { mutableStateOf<PallaSyncBootstrapReview?>(null) }
    var bootstrapReviewReply by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }

    LaunchedEffect(serverUrl) {
        try {
            candidate = manager.pendingPeerSubjectEnrollment(serverUrl)
        } catch (failure: Exception) {
            peerError = failure.message?.takeIf { it.matches(Regex("C_[A-Z_]+")) } ?: "C_BOOTSTRAP_TRUST_REQUIRED"
        }
    }

    val performHaptic = rememberHapticFeedbackAction()
    val scanner =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            result.contents?.let {
                offerJson = it
                peerError = null
                // Confirms the scan landed without the scanner activity needing a result banner.
                performHaptic(AppHapticEffect.Success)
            }
        }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val purpose = importPurpose
            importPurpose = null
            if (uri != null && purpose != null) {
                scope.launch {
                    peerBusy = true
                    try {
                        val text =
                            withContext(Dispatchers.IO) {
                                checkNotNull(context.contentResolver.openInputStream(uri)) { "C_MISSING_DEPENDENCY" }
                                    .use { input ->
                                        PallaSyncPeerTransferFile.read(input)
                                    }
                            }
                        val objectValue = PallaSyncDeletionEvidenceArchive.parseStrictObject(text)
                        when (purpose) {
                            ImportPurpose.AUTHORITY -> {
                                authorityJson = text
                                val importedPin =
                                    objectValue["authority"]
                                        ?.jsonObject
                                        ?.get("pin")
                                        ?.jsonObject
                                        ?: objectValue["pin"]?.jsonObject
                                authorityLabel = importedPin?.get("log_id")?.toString()
                                peerError = null
                            }

                            ImportPurpose.CERTIFICATE_ENVELOPE -> {
                                check(objectValue.keys == setOf("certificate", "envelopes")) { "C_SCHEMA_VIOLATION" }
                                val current = checkNotNull(work) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                                candidate =
                                    manager.preparePeerSubjectEnrollment(
                                        current,
                                        objectValue.getValue("certificate").toString(),
                                        objectValue.getValue("envelopes").jsonArray.toString(),
                                        Build.MODEL.take(256).ifBlank { "Android device" },
                                    )
                                pendingExport = candidate?.requestBody
                                peerError = null
                            }

                            ImportPurpose.ACCEPTANCE -> {
                                manager.completePeerSubjectEnrollment(text)
                                peerError = null
                                joined = true
                                onPairSuccess()
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        peerError = failure.message?.takeIf { it.matches(Regex("C_[A-Z_]+")) } ?: "C_SCHEMA_VIOLATION"
                        if (purpose == ImportPurpose.CERTIFICATE_ENVELOPE) {
                            work?.close()
                            work = null
                            confirmed = false
                            offerJson = null
                        }
                    } finally {
                        peerBusy = false
                    }
                }
            }
        }
    val exporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val value = pendingExport
            pendingExport = null
            if (uri != null && value != null) {
                scope.launch {
                    peerBusy = true
                    try {
                        withContext(Dispatchers.IO) {
                            val bytes = PallaSyncPeerTransferFile.encode(value)
                            checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "C_LOCAL_STORAGE_FULL" }.use {
                                it.write(bytes)
                                it.flush()
                            }
                            checkNotNull(context.contentResolver.openInputStream(uri)) { "C_MISSING_DEPENDENCY" }.use { input ->
                                val readBack = PallaSyncDeletionEvidenceArchive.readBounded(input, PallaSyncPeerTransferFile.MAX_BYTES)
                                check(readBack.contentEquals(bytes)) { "C_MISSING_DEPENDENCY" }
                            }
                        }
                        Toast.makeText(context, R.string.pallasync_peer_subject_request_exported, Toast.LENGTH_LONG).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        peerError = failure.message?.takeIf { it.matches(Regex("C_[A-Z_]+")) } ?: "C_LOCAL_STORAGE_FULL"
                    } finally {
                        peerBusy = false
                    }
                }
            }
        }

    DisposableEffect(work?.session?.handle) {
        onDispose { work?.close() }
    }

    if (trustSource != null) {
        val source = checkNotNull(trustSource)
        val peer = source == "authenticated_export"
        PallaSyncTrustDialog(
            origin = serverUrl,
            sourceKind = source,
            sourceLabel =
                stringResource(
                    if (peer) R.string.pallasync_peer_subject_trust_source else R.string.pallasync_v3_relay_replay_source,
                ),
            onDismiss = { trustSource = null },
            onAccepted = {
                trustSource = null
                if (peer) {
                    peerSetup = true
                } else {
                    joined = false
                    showRoot = true
                }
            },
        )
    }

    if (showRoot) {
        PallaSyncRootActionDialog(
            manager,
            title = stringResource(R.string.pallasync_v3_root_enroll),
            explanation = stringResource(R.string.pallasync_v3_relay_replay_source),
            onDismiss = {
                showRoot = false
                if (joined) onPairSuccess()
            },
        ) { words, passphrase, cancellation ->
            manager
                .joinChain(
                    words,
                    serverUrl = serverUrl,
                    passphrase = passphrase,
                    cancellation = cancellation,
                    bootstrapReview = { review ->
                        val reply = CompletableDeferred<Boolean>()
                        withContext(Dispatchers.Main.immediate) {
                            bootstrapReviewState = review
                            bootstrapReviewReply = reply
                        }
                        try {
                            reply.await()
                        } finally {
                            withContext(NonCancellable + Dispatchers.Main.immediate) {
                                if (bootstrapReviewReply === reply) {
                                    bootstrapReviewReply = null
                                    bootstrapReviewState = null
                                }
                            }
                        }
                    },
                ).also { joined = it }
        }
    }

    bootstrapReviewState?.let { review ->
        PallaSyncBootstrapReviewDialog(
            review = review,
            onConfirm = { bootstrapReviewReply?.complete(true) },
            onDismiss = { bootstrapReviewReply?.complete(false) },
        )
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.pallasync_sync_devices),
                navigationIcon = { HeaderIcon(MiuixIcons.Back, onClick = onBack) },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(serverUrl)
            Text(stringResource(R.string.pallasync_v3_relay_replay_source))
            // The step shown is derived from observable state, never advanced by tapping through.
            PairingStepper(
                current =
                    when {
                        candidate != null || confirmed -> PairingStep.CONFIRM
                        work != null -> PairingStep.SCAN
                        else -> PairingStep.SHOW_CODE
                    },
            )
            PairingRoleCard(
                title = stringResource(R.string.pallasync_role_add_device),
                description = stringResource(R.string.pallasync_role_add_device_description),
                icon = MiuixIcons.Add,
                selected = peerSetup,
                enabled = trustSource == null && !showRoot && work == null && candidate == null,
                onClick = { trustSource = "authenticated_export" },
            )
            PairingRoleCard(
                title = stringResource(R.string.pallasync_role_join_chain),
                description = stringResource(R.string.pallasync_role_join_chain_description),
                icon = MiuixIcons.Scan,
                selected = showRoot,
                enabled = trustSource == null && !showRoot && !peerSetup,
                onClick = { trustSource = "relay_full_replay" },
            )
            if (peerSetup) {
                Text(stringResource(R.string.pallasync_peer_subject_guidance))
                TextButton(
                    text = stringResource(R.string.pallasync_peer_subject_scan),
                    enabled = !peerBusy && work == null && candidate == null,
                    onClick = {
                        scanner.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt(context.getString(R.string.pallasync_peer_subject_scan_prompt))
                                .setBeepEnabled(false)
                                .setOrientationLocked(false),
                        )
                    },
                )
                Text(
                    offerJson?.let {
                        runCatching {
                            Json
                                .parseToJsonElement(
                                    it,
                                ).jsonObject
                                .getValue("chain_id")
                                .toString()
                        }.getOrDefault("")
                    }
                        ?: stringResource(R.string.pallasync_peer_subject_no_offer),
                )
                TextButton(
                    text = stringResource(R.string.pallasync_peer_subject_import_authority),
                    enabled = !peerBusy && work == null && candidate == null,
                    onClick = {
                        importPurpose = ImportPurpose.AUTHORITY
                        importer.launch(arrayOf("application/json", "text/json", "*/*"))
                    },
                )
                authorityLabel?.let { Text(stringResource(R.string.pallasync_peer_subject_authority_loaded, it)) }
                Button(
                    onClick = {
                        val offer = offerJson ?: return@Button
                        val authority = authorityJson ?: return@Button
                        peerBusy = true
                        peerError = null
                        scope.launch {
                            try {
                                work = manager.beginPeerSubjectEnrollment(offer, authority, serverUrl)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                peerError = failure.message?.takeIf { it.matches(Regex("C_[A-Z_]+")) } ?: "C_BOOTSTRAP_TRUST_REQUIRED"
                                offerJson = null
                                authorityJson = null
                                authorityLabel = null
                                work?.close()
                                work = null
                                confirmed = false
                            } finally {
                                peerBusy = false
                            }
                        }
                    },
                    enabled = !peerBusy && work == null && candidate == null && offerJson != null && authorityJson != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.pallasync_peer_subject_submit))
                }
                // The native call can take a while; a spinner plus the current step proves progress.
                if (peerBusy) {
                    PallaSyncWorkingState(text = stringResource(R.string.pallasync_peer_subject_working))
                }
                work?.let { active ->
                    Text(stringResource(R.string.pallasync_peer_subject_sas_title))
                    PairingSasCode(sas = active.sas)
                    Text(stringResource(R.string.pallasync_peer_subject_sas_guidance))
                    Button(
                        enabled = !peerBusy && !confirmed,
                        onClick = {
                            peerBusy = true
                            scope.launch {
                                try {
                                    manager.confirmPeerSubjectSas(active, active.sas)
                                    confirmed = true
                                    performHaptic(AppHapticEffect.Success)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    peerError = failure.message?.takeIf { it.matches(Regex("C_[A-Z_]+")) } ?: "C_BOOTSTRAP_TRUST_REQUIRED"
                                } finally {
                                    peerBusy = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.pallasync_peer_subject_sas_confirm))
                    }
                    if (confirmed) {
                        TextButton(
                            text = stringResource(R.string.pallasync_peer_subject_import_certificate),
                            enabled = !peerBusy && candidate == null,
                            onClick = {
                                importPurpose = ImportPurpose.CERTIFICATE_ENVELOPE
                                importer.launch(arrayOf("application/json", "text/json", "*/*"))
                            },
                        )
                    }
                }
                candidate?.let {
                    Text(stringResource(R.string.pallasync_peer_subject_pending))
                    Text(it.keys.deviceId.orEmpty())
                    TextButton(
                        text = stringResource(R.string.pallasync_peer_subject_export_request),
                        enabled = !peerBusy,
                        onClick = { exporter.launch("pallasync-enrollment-${it.keys.deviceId}.json") },
                    )
                    TextButton(
                        text = stringResource(R.string.pallasync_peer_subject_import_acceptance),
                        enabled = !peerBusy,
                        onClick = {
                            importPurpose = ImportPurpose.ACCEPTANCE
                            importer.launch(arrayOf("application/json", "text/json", "*/*"))
                        },
                    )
                }
                // A raw C_* code means nothing to the user, so it is paired with a way out.
                PallaSyncErrorHint(code = peerError)
            }
        }
    }
}

private enum class ImportPurpose { AUTHORITY, CERTIFICATE_ENVELOPE, ACCEPTANCE }
