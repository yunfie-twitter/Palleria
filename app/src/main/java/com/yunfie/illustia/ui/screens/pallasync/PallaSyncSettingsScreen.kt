package com.yunfie.illustia.ui.screens.pallasync

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PALLASYNC_EXPORT_MEDIA_TYPE
import com.yunfie.illustia.pallasync.PallaSyncClientStatus
import com.yunfie.illustia.pallasync.PallaSyncDeletionEvidenceArchive
import com.yunfie.illustia.pallasync.PallaSyncDeletionRequestResult
import com.yunfie.illustia.pallasync.PallaSyncExportArchiveVerifier
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.IOException

private const val MAX_DELETION_EVIDENCE_ARCHIVE_BYTES = 262_144

@Composable
@Suppress("ThrowsCount") // SAF provider failures are mapped to explicit retained-state UI outcomes.
fun PallaSyncSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onPairDevice: () -> Unit,
    onDeviceClick: (String, String) -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncManager = remember { PalleriaSyncManager(context = context) }

    var showUrlDialog by remember { mutableStateOf(false) }
    var tempUrl by remember { mutableStateOf("") }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteRootInput by remember { mutableStateOf(false) }
    var deletingChain by remember { mutableStateOf(false) }
    var pendingDeletionProof by remember { mutableStateOf<String?>(null) }
    var pendingDeletionArchive by remember { mutableStateOf<String?>(null) }
    var witnessInputArchive by remember { mutableStateOf<String?>(null) }
    var witnessOutputArchive by remember { mutableStateOf<String?>(null) }
    var exportedDeletionProof by remember { mutableStateOf<String?>(null) }
    var showOfflineDeletionCopyConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var isCreatingChain by remember { mutableStateOf(false) }
    var showTrustDialog by remember { mutableStateOf(false) }
    var showPolicyDialog by remember { mutableStateOf(false) }
    var generatedMnemonic by remember { mutableStateOf<String?>(null) }
    var hasLocalChain by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<com.yunfie.illustia.pallasync.data.PallaSyncDeviceEntity>>(emptyList()) }
    var clientStatus by remember { mutableStateOf<PallaSyncClientStatus?>(null) }

    val relayExportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PALLASYNC_EXPORT_MEDIA_TYPE)) { uri ->
            if (uri == null) {
                Toast.makeText(context, R.string.pallasync_export_cancelled, Toast.LENGTH_SHORT).show()
            } else {
                scope.launch {
                    try {
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            syncManager.exportRelayArchive(
                                checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "C_LOCAL_STORAGE_FULL" },
                            )
                            val verified =
                                checkNotNull(context.contentResolver.openInputStream(uri)) { "C_SCHEMA_VIOLATION" }.use(
                                    PallaSyncExportArchiveVerifier::verify,
                                )
                            check(!verified.includeRecovery && !verified.complete) { "C_SCHEMA_VIOLATION" }
                        }
                        Toast.makeText(context, R.string.pallasync_export_saved, Toast.LENGTH_LONG).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IOException) {
                        Toast.makeText(context, R.string.pallasync_export_failed, Toast.LENGTH_LONG).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(context, R.string.pallasync_export_failed, Toast.LENGTH_LONG).show()
                    } catch (_: IllegalStateException) {
                        Toast.makeText(context, R.string.pallasync_export_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    LaunchedEffect(state.settings.pallaSyncEnabled) {
        if (!state.settings.pallaSyncEnabled) {
            clientStatus = null
            return@LaunchedEffect
        }
        while (isActive) {
            try {
                clientStatus = syncManager.getClientStatus()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the last verified summary visible; a local read failure is not sync progress.
            }
            kotlinx.coroutines.delay(2_000)
        }
    }

    val deletionProofExporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val proof = pendingDeletionProof
            if (uri == null || proof == null) {
                Toast.makeText(context, R.string.pallasync_delete_export_required, Toast.LENGTH_LONG).show()
            } else {
                deletingChain = true
                scope.launch {
                    try {
                        val readBack =
                            withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val bytes = proof.toByteArray(Charsets.UTF_8)
                                checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "C_LOCAL_STORAGE_FULL" }.use { output ->
                                    output.write(bytes)
                                    output.flush()
                                }
                                val returned =
                                    checkNotNull(
                                        context.contentResolver.openInputStream(uri),
                                    ) { "C_DELETION_UNVERIFIED" }.use { it.readBytes() }
                                check(returned.contentEquals(bytes)) { "C_DELETION_UNVERIFIED" }
                                returned.toString(Charsets.UTF_8)
                            }
                        exportedDeletionProof = readBack
                        showOfflineDeletionCopyConfirm = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IOException) {
                        Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                    } catch (_: IllegalStateException) {
                        Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                    } finally {
                        deletingChain = false
                    }
                }
            }
        }

    val deletionArchiveExporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val archive = pendingDeletionArchive
            if (uri == null || archive == null) {
                Toast.makeText(context, R.string.pallasync_delete_export_required, Toast.LENGTH_LONG).show()
            } else {
                scope.launch {
                    try {
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val bytes = archive.toByteArray(Charsets.UTF_8)
                            check(bytes.size <= MAX_DELETION_EVIDENCE_ARCHIVE_BYTES) { "C_LOCAL_LIMIT" }
                            checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "C_LOCAL_STORAGE_FULL" }.use { output ->
                                output.write(bytes)
                                output.flush()
                            }
                            val readBack =
                                checkNotNull(
                                    context.contentResolver.openInputStream(uri),
                                ) { "C_DELETION_UNVERIFIED" }.use {
                                    PallaSyncDeletionEvidenceArchive.readBounded(it, MAX_DELETION_EVIDENCE_ARCHIVE_BYTES)
                                }
                            check(readBack.contentEquals(bytes)) { "C_DELETION_UNVERIFIED" }
                        }
                        Toast.makeText(context, R.string.pallasync_delete_bundle_exported, Toast.LENGTH_LONG).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IOException) {
                        Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                    } catch (_: IllegalStateException) {
                        Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    val deletionWitnessExporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val archive = witnessOutputArchive
            if (uri == null || archive == null) {
                Toast.makeText(context, R.string.pallasync_witness_export_cancelled, Toast.LENGTH_LONG).show()
            } else {
                scope.launch {
                    try {
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val bytes = archive.toByteArray(Charsets.UTF_8)
                            checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "C_LOCAL_STORAGE_FULL" }.use { output ->
                                output.write(bytes)
                                output.flush()
                            }
                            val readBack =
                                checkNotNull(
                                    context.contentResolver.openInputStream(uri),
                                ) { "C_DELETION_UNVERIFIED" }.use { it.readBytes() }
                            check(readBack.contentEquals(bytes)) { "C_DELETION_UNVERIFIED" }
                        }
                        Toast.makeText(context, R.string.pallasync_witness_exported, Toast.LENGTH_LONG).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IOException) {
                        Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                    } catch (_: IllegalStateException) {
                        Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                    } finally {
                        witnessOutputArchive = null
                    }
                }
            }
        }

    val deletionWitnessImporter =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                Toast.makeText(context, R.string.pallasync_witness_import_cancelled, Toast.LENGTH_SHORT).show()
            } else {
                scope.launch {
                    try {
                        val bytes =
                            withContext(kotlinx.coroutines.Dispatchers.IO) {
                                checkNotNull(
                                    context.contentResolver.openInputStream(uri),
                                ) { "C_SCHEMA_VIOLATION" }.use {
                                    PallaSyncDeletionEvidenceArchive.readBounded(it, MAX_DELETION_EVIDENCE_ARCHIVE_BYTES)
                                }
                            }
                        val archive = bytes.toString(Charsets.UTF_8)
                        val value = Json.parseToJsonElement(archive).jsonObject
                        check(value.keys == setOf("delete_receipt", "evidence_bundle")) { "C_SCHEMA_VIOLATION" }
                        witnessInputArchive = archive
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IOException) {
                        Toast.makeText(context, R.string.pallasync_witness_import_failed, Toast.LENGTH_LONG).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(context, R.string.pallasync_witness_import_failed, Toast.LENGTH_LONG).show()
                    } catch (_: IllegalStateException) {
                        Toast.makeText(context, R.string.pallasync_witness_import_failed, Toast.LENGTH_LONG).show()
                    } catch (_: IllegalArgumentException) {
                        Toast.makeText(context, R.string.pallasync_witness_import_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    LaunchedEffect(state.settings.pallaSyncEnabled) {
        hasLocalChain = syncManager.hasLocalChain()
        pendingDeletionProof = syncManager.pendingDeletionProof()
        if (state.settings.pallaSyncEnabled) {
            val db =
                com.yunfie.illustia.pallasync.data.PallaSyncDatabase
                    .getDatabase(context)
            val dao = db.pallaSyncDao()

            launch(kotlinx.coroutines.Dispatchers.IO) {
                while (isActive) {
                    val fetchedDevices = dao.getAllDevices()
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        devices = fetchedDevices
                    }
                    kotlinx.coroutines.delay(2000)
                }
            }

            launch(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.delay(1000)
                val chainState = dao.getActiveChainStates().firstOrNull()
                if (chainState != null) {
                    val serverUrl = syncManager.getServerUrl()
                    syncManager.fetchDevices(serverUrl, chainState.chainId)
                }
            }
        }
    }

    if (showUrlDialog) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.pallasync_server_url_dialog_title),
            summary = stringResource(R.string.pallasync_server_url_dialog_desc),
            backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest,
            onDismissRequest = { showUrlDialog = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TextField(
                    value = tempUrl,
                    onValueChange = { tempUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = { showUrlDialog = false },
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.action_cancel),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        onClick = {
                            val normalized = syncManager.normalizeServerUrl(tempUrl)
                            if (normalized == null) {
                                Toast.makeText(context, R.string.error_pallasync_invalid_server_url, Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.updatePallaSyncServerUrl(normalized)
                                showUrlDialog = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        text = stringResource(R.string.action_confirm),
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_delete_chain),
            summary = stringResource(R.string.pallasync_delete_chain_confirm),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                showDeleteConfirm = false
                showDeleteRootInput = true
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    if (showDeleteRootInput) {
        PallaSyncRootInputDialog(
            title = stringResource(R.string.pallasync_delete_chain),
            explanation = stringResource(R.string.pallasync_delete_chain_confirm),
            busy = deletingChain,
            onDismiss = { showDeleteRootInput = false },
            onConfirm = { mnemonic, passphrase ->
                deletingChain = true
                scope.launch {
                    try {
                        when (syncManager.requestChainDeletion(mnemonic, passphrase)) {
                            PallaSyncDeletionRequestResult.PROOF_READY_FOR_EXPORT -> {
                                val proof = syncManager.pendingDeletionProof()
                                if (proof == null) {
                                    Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                                } else {
                                    pendingDeletionProof = proof
                                    deletionProofExporter.launch("pallasync-deletion-evidence.json")
                                }
                            }

                            PallaSyncDeletionRequestResult.PENDING_EVIDENCE -> {
                                Toast.makeText(context, R.string.pallasync_v3_epoch_deletion_unverified, Toast.LENGTH_LONG).show()
                            }

                            PallaSyncDeletionRequestResult.FAILED -> {
                                Toast.makeText(context, R.string.error_pallasync_delete_chain_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    } finally {
                        deletingChain = false
                        showDeleteRootInput = false
                    }
                }
            },
        )
    }

    if (showOfflineDeletionCopyConfirm) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_delete_export_title),
            summary = stringResource(R.string.pallasync_delete_export_written_confirm),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                showOfflineDeletionCopyConfirm = false
                val proof = exportedDeletionProof
                if (proof == null) {
                    Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                } else {
                    deletingChain = true
                    scope.launch {
                        try {
                            if (syncManager.completeDeletionAfterExport(proof, explicitlyConfirmedOfflineCopy = true)) {
                                viewModel.updatePallaSyncEnabled(false)
                                Toast.makeText(context, R.string.msg_pallasync_chain_deleted, Toast.LENGTH_LONG).show()
                                pendingDeletionProof = null
                                exportedDeletionProof = null
                            } else {
                                Toast.makeText(context, R.string.error_pallasync_delete_chain_failed, Toast.LENGTH_LONG).show()
                            }
                        } finally {
                            deletingChain = false
                        }
                    }
                }
            },
            onDismiss = {
                showOfflineDeletionCopyConfirm = false
                Toast.makeText(context, R.string.pallasync_delete_export_required, Toast.LENGTH_LONG).show()
            },
        )
    }

    witnessInputArchive?.let { archive ->
        val info =
            runCatching {
                val receipt =
                    Json
                        .parseToJsonElement(archive)
                        .jsonObject
                        .getValue("delete_receipt")
                        .jsonObject
                "${receipt.getValue("chain_id").jsonPrimitive.content}\n${receipt.getValue("deletion_id").jsonPrimitive.content}"
            }.getOrDefault("")
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_witness_confirm_title),
            summary = stringResource(R.string.pallasync_witness_confirm_summary, info),
            confirmText = stringResource(R.string.pallasync_witness_confirm_action),
            onConfirm = {
                witnessInputArchive = null
                scope.launch {
                    try {
                        witnessOutputArchive = syncManager.addDeletionIntentWitness(archive, explicitlyConfirmed = true)
                        if (witnessOutputArchive == null) {
                            Toast.makeText(context, R.string.pallasync_witness_create_failed, Toast.LENGTH_LONG).show()
                        } else {
                            deletionWitnessExporter.launch("pallasync-deletion-witness-bundle.json")
                        }
                    } catch (_: IllegalStateException) {
                        Toast.makeText(context, R.string.pallasync_witness_create_failed, Toast.LENGTH_LONG).show()
                    } catch (_: IllegalArgumentException) {
                        Toast.makeText(context, R.string.pallasync_witness_create_failed, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { witnessInputArchive = null },
        )
    }

    if (showLeaveConfirm) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_leave_chain),
            summary = stringResource(R.string.pallasync_leave_chain_confirm),
            confirmText = stringResource(R.string.pallasync_leave_chain),
            destructive = true,
            onConfirm = {
                showLeaveConfirm = false
                scope.launch {
                    if (syncManager.deleteChain(callApi = false)) {
                        viewModel.updatePallaSyncEnabled(false)
                        Toast.makeText(context, R.string.msg_pallasync_chain_left, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showLeaveConfirm = false },
        )
    }

    if (showTrustDialog) {
        PallaSyncTrustDialog(
            origin = state.settings.pallaSyncServerUrl,
            sourceKind = "new_chain_local_state",
            sourceLabel = stringResource(R.string.pallasync_v3_local_source),
            onDismiss = { showTrustDialog = false },
            onAccepted = {
                showTrustDialog = false
                scope.launch {
                    isCreatingChain = true
                    try {
                        val phrase = syncManager.generateGenesisMnemonic()
                        if (phrase.isBlank()) {
                            Toast.makeText(context, R.string.error_pallasync_create_chain_failed, Toast.LENGTH_SHORT).show()
                        } else {
                            generatedMnemonic = phrase
                        }
                    } finally {
                        isCreatingChain = false
                    }
                }
            },
        )
    }
    if (showPolicyDialog) {
        PallaSyncAcceptedPolicyDialog(
            origin = state.settings.pallaSyncServerUrl,
            onDismiss = { showPolicyDialog = false },
            onWithdrawn = {
                syncManager.stopBackgroundSync()
                showPolicyDialog = false
            },
        )
    }
    generatedMnemonic?.let { phrase ->
        PallaSyncGenesisDialog(syncManager, phrase, state.settings.pallaSyncServerUrl, onDismiss = { generatedMnemonic = null })
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.pallasync_settings_title),
                largeTitle = stringResource(R.string.pallasync_settings_title),
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
                Section(stringResource(R.string.settings_general)) {
                    ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                        SettingSwitchRow(
                            title = stringResource(R.string.pallasync_enable),
                            checked = state.settings.pallaSyncEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !state.settings.pallaSyncEnabled && !isCreatingChain) {
                                    scope.launch {
                                        if (syncManager.hasLocalChain()) {
                                            if (syncManager.resumeBoundChain()) {
                                                viewModel.updatePallaSyncEnabled(true)
                                            } else {
                                                Toast.makeText(context, R.string.pallasync_v3_root_paused, Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            showTrustDialog = true
                                        }
                                    }
                                } else if (!enabled && state.settings.pallaSyncEnabled) {
                                    syncManager.stopBackgroundSync()
                                    viewModel.updatePallaSyncEnabled(false)
                                }
                            },
                            enabled = !isCreatingChain,
                            summary = stringResource(R.string.pallasync_enable_desc),
                        )
                        DividerLine()
                        SettingLinkRow(
                            title = stringResource(R.string.pallasync_v3_privacy),
                            onClick = { showPolicyDialog = true },
                        )
                        DividerLine()
                        SettingLinkRow(
                            title = stringResource(R.string.pallasync_server_url),
                            summary = state.settings.pallaSyncServerUrl,
                            onClick = {
                                tempUrl = state.settings.pallaSyncServerUrl
                                showUrlDialog = true
                            },
                        )
                        if (pendingDeletionProof != null) {
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_delete_export_title),
                                summary = stringResource(R.string.pallasync_delete_export_required),
                                onClick = { deletionProofExporter.launch("pallasync-deletion-evidence.json") },
                            )
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_delete_bundle_export),
                                summary = stringResource(R.string.pallasync_delete_bundle_export_desc),
                                onClick = {
                                    scope.launch {
                                        try {
                                            pendingDeletionArchive = syncManager.pendingDeletionEvidenceArchive()
                                            if (pendingDeletionArchive == null) {
                                                Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                                            } else {
                                                deletionArchiveExporter.launch("pallasync-deletion-bundle.json")
                                            }
                                        } catch (_: IllegalStateException) {
                                            Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                            )
                        }
                        DividerLine()
                        SettingLinkRow(
                            title = stringResource(R.string.pallasync_pair_device),
                            summary = stringResource(R.string.pallasync_pair_device_desc),
                            onClick = onPairDevice,
                        )
                        if (state.settings.pallaSyncEnabled) {
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_export_title),
                                summary = stringResource(R.string.pallasync_export_device_summary),
                                onClick = { relayExportLauncher.launch("pallasync-device-export.jsonl") },
                            )
                        }
                        if (state.settings.pallaSyncEnabled) {
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_witness_import_title),
                                summary = stringResource(R.string.pallasync_witness_import_summary),
                                onClick = { deletionWitnessImporter.launch(arrayOf("application/json")) },
                            )
                        }
                        if (state.settings.pallaSyncEnabled) {
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_sync_now),
                                summary = stringResource(R.string.pallasync_sync_now_desc),
                                onClick = {
                                    scope.launch {
                                        val success = syncManager.syncNow()
                                        Toast
                                            .makeText(
                                                context,
                                                if (success) {
                                                    context.getString(R.string.msg_pallasync_sync_completed)
                                                } else {
                                                    context.getString(R.string.error_pallasync_sync_failed)
                                                },
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (state.settings.pallaSyncEnabled || hasLocalChain) {
                item { PallaSyncEpochControls(syncManager, state.settings.pallaSyncEnabled) }
                item { PallaSyncRecoveryControls(syncManager) }
            }
            if (state.settings.pallaSyncEnabled) {
                if (devices.isNotEmpty()) {
                    item {
                        Section(stringResource(R.string.pallasync_participating_devices)) {
                            ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                                devices.forEachIndexed { index, device ->
                                    SettingLinkRow(
                                        title = device.deviceName,
                                        summary = stringResource(R.string.pallasync_device_id, device.deviceId),
                                        onClick = { onDeviceClick(device.deviceId, device.deviceName) },
                                    )
                                    if (index < devices.size - 1) {
                                        DividerLine()
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Section(stringResource(R.string.data_section_cleanup)) {
                        ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_leave_chain),
                                summary = stringResource(R.string.pallasync_leave_chain_desc),
                                onClick = { showLeaveConfirm = true },
                            )
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_delete_chain),
                                summary = stringResource(R.string.pallasync_delete_chain_desc),
                                onClick = { showDeleteConfirm = true },
                            )
                        }
                    }
                }
            }

            item {
                clientStatus?.let { status ->
                    Section(stringResource(R.string.pallasync_progress_title)) {
                        ElevatedPanel(contentPadding = PaddingValues(16.dp)) {
                            Text(stringResource(R.string.pallasync_progress_state, status.writeState))
                            Text(
                                stringResource(
                                    R.string.pallasync_progress_positions,
                                    status.targetSeq,
                                    status.verifiedSeq,
                                    status.materializedSeq,
                                ),
                            )
                            Text(
                                stringResource(
                                    R.string.pallasync_progress_transparency,
                                    status.verifiedTransparencyIndex,
                                    status.observedTransparencyTargetIndex,
                                ),
                            )
                            if (status.transparencyObservationConflict) {
                                Text(stringResource(R.string.pallasync_progress_transparency_conflict))
                            }
                            Text(
                                stringResource(
                                    if (status.partial) R.string.pallasync_progress_partial else R.string.pallasync_progress_current,
                                    status.initialPullCompleted,
                                ),
                            )
                            Text(
                                stringResource(
                                    R.string.pallasync_progress_blockers,
                                    status.pendingOutbox,
                                    status.authorGaps,
                                    status.unresolvedQuarantine,
                                ),
                            )
                            DividerLine()
                            Text(stringResource(R.string.pallasync_capacity_search, status.searchCount, status.searchCeiling))
                            Text(stringResource(R.string.pallasync_capacity_viewed, status.viewedCount, status.viewedCeiling))
                            Text(stringResource(R.string.pallasync_capacity_seen, status.seenCount, status.seenCeiling))
                        }
                    }
                }
            }

            item {
                val logs by PalleriaSyncManager.syncLogs.collectAsStateWithLifecycle()
                Section(stringResource(R.string.pallasync_sync_logs)) {
                    ElevatedPanel(contentPadding = PaddingValues(16.dp)) {
                        if (logs.isEmpty()) {
                            Text(
                                stringResource(R.string.pallasync_no_logs),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        } else {
                            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                                logs.forEach { logMsg ->
                                    Text(
                                        logMsg,
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
