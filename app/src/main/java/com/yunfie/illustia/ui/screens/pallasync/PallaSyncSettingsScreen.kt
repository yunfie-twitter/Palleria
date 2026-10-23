package com.yunfie.illustia.ui.screens.pallasync

import android.text.format.Formatter
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PALLASYNC_EXPORT_MEDIA_TYPE
import com.yunfie.illustia.pallasync.PallaSyncCheckpointFile
import com.yunfie.illustia.pallasync.PallaSyncClientStatus
import com.yunfie.illustia.pallasync.PallaSyncDeletionEvidenceArchive
import com.yunfie.illustia.pallasync.PallaSyncDeletionEvidenceRevision
import com.yunfie.illustia.pallasync.PallaSyncDeletionRequestResult
import com.yunfie.illustia.pallasync.PallaSyncDeletionWitnessPreview
import com.yunfie.illustia.pallasync.PallaSyncExportArchiveVerifier
import com.yunfie.illustia.pallasync.PallaSyncHttpResult
import com.yunfie.illustia.pallasync.PallaSyncLegacyMigrationReport
import com.yunfie.illustia.pallasync.PallaSyncLocalIntents
import com.yunfie.illustia.pallasync.PallaSyncQuarantineDisposition
import com.yunfie.illustia.pallasync.PallaSyncRecoveryGuidance
import com.yunfie.illustia.pallasync.PallaSyncRecoveryHint
import com.yunfie.illustia.pallasync.PallaSyncReseedSourceConflict
import com.yunfie.illustia.pallasync.PallaSyncRetryGate
import com.yunfie.illustia.pallasync.PallaSyncRootSession
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import com.yunfie.illustia.pallasync.authenticateDeletionWitness
import com.yunfie.illustia.pallasync.data.ProtectedEvidenceEntity
import com.yunfie.illustia.pallasync.data.QuarantineSummaryRow
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
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.IOException
import java.util.UUID
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton

/** Keep the sync-settings actions aligned and equally tappable across long and short labels. */
@Composable
private fun PallaSyncActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    MiuixTextButton(
        text = text,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        enabled = enabled,
    )
}

@Composable
private fun PallaSyncRecoveryHintText(code: String) {
    val message =
        when (PallaSyncRecoveryGuidance.forCode(code)) {
            PallaSyncRecoveryHint.REFRESH_TOKEN -> R.string.pallasync_recovery_hint_token
            PallaSyncRecoveryHint.EXPLICIT_ADMIN_AUTH -> R.string.pallasync_recovery_hint_admin
            PallaSyncRecoveryHint.APPROVED_DEVICE_FLOW -> R.string.pallasync_recovery_hint_device
            PallaSyncRecoveryHint.DEVICE_SLOT_RELEASE -> R.string.pallasync_recovery_hint_device_limit
            PallaSyncRecoveryHint.VERIFY_ORIGIN_AND_HISTORY -> R.string.pallasync_recovery_hint_origin
            PallaSyncRecoveryHint.VERIFY_EPOCH_HISTORY -> R.string.pallasync_recovery_hint_epoch
            PallaSyncRecoveryHint.NEW_ENROLLMENT_OFFER -> R.string.pallasync_recovery_hint_offer
            PallaSyncRecoveryHint.RESEND_AUTHORIZED_ENVELOPE -> R.string.pallasync_recovery_hint_envelope
            PallaSyncRecoveryHint.RESTORE_SNAPSHOT -> R.string.pallasync_recovery_hint_snapshot
            PallaSyncRecoveryHint.REBUILD_STALE_REQUEST -> R.string.pallasync_recovery_hint_rebuild
            PallaSyncRecoveryHint.RESTART_DURABLE_CURSOR -> R.string.pallasync_recovery_hint_cursor
            PallaSyncRecoveryHint.SPLIT_OVERSIZED_BATCH -> R.string.pallasync_recovery_hint_payload
            PallaSyncRecoveryHint.HONOR_RETRY_AFTER -> R.string.pallasync_recovery_hint_rate
            PallaSyncRecoveryHint.RETAIN_AND_RECONCILE -> R.string.pallasync_recovery_hint_reconcile
            PallaSyncRecoveryHint.EXPLICIT_RELAY_MIGRATION -> R.string.pallasync_recovery_hint_migration
            PallaSyncRecoveryHint.USER_CAPACITY_RECOVERY -> R.string.pallasync_recovery_hint_capacity
            PallaSyncRecoveryHint.WAIT_FOR_RECEIPT_EVIDENCE -> R.string.pallasync_recovery_hint_receipt
            PallaSyncRecoveryHint.CORRECT_REQUEST -> R.string.pallasync_recovery_hint_request
            PallaSyncRecoveryHint.REBUILD_AUTHORIZATION -> R.string.pallasync_recovery_hint_authorization
            PallaSyncRecoveryHint.VERIFY_IDENTITY -> R.string.pallasync_recovery_hint_identity
            PallaSyncRecoveryHint.COMPARE_EXISTING_STATE -> R.string.pallasync_recovery_hint_conflict
            PallaSyncRecoveryHint.VERIFY_DELETION -> R.string.pallasync_recovery_hint_deleted
            null -> return
        }
    Text(stringResource(message))
}

@Composable
@Suppress("ThrowsCount", "TooGenericExceptionCaught") // SAF and snapshot-source failures are mapped to retained-state UI outcomes.
fun PallaSyncSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onPairDevice: () -> Unit,
    onManagePallaSyncDevices: () -> Unit,
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
    var showDeletedReceiptRootInput by remember { mutableStateOf(false) }
    var deletingChain by remember { mutableStateOf(false) }
    var showPruneConfirm by remember { mutableStateOf(false) }
    var showPruneRootInput by remember { mutableStateOf(false) }
    var pruningLog by remember { mutableStateOf(false) }
    var showRepairRootInput by remember { mutableStateOf(false) }
    var repairingRelayHistory by remember { mutableStateOf(false) }
    var showCommitmentRepairRootInput by remember { mutableStateOf(false) }
    var repairingCommitmentEnvelope by remember { mutableStateOf(false) }
    var pendingCommitmentRepairHash by remember { mutableStateOf<String?>(null) }
    var showRecoveryExportRootInput by remember { mutableStateOf(false) }
    var exportingRecoveryArchive by remember { mutableStateOf(false) }
    var recoveryExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingRecoveryArchiveUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var recoveryArchiveRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showRecoveryArchiveRestoreRootInput by remember { mutableStateOf(false) }
    var restoringRecoveryArchive by remember { mutableStateOf(false) }
    var showReseedImportTarget by remember { mutableStateOf(false) }
    var showReseedImportRootInput by remember { mutableStateOf(false) }
    var showReseedCommitConfirm by remember { mutableStateOf(false) }
    var showReseedCommitRootInput by remember { mutableStateOf(false) }
    var reseedImportTargetUrl by remember { mutableStateOf("") }
    var selectedReseedImportTargetUrl by remember { mutableStateOf<String?>(null) }
    var stagingReseedImport by remember { mutableStateOf(false) }
    var committingReseedImport by remember { mutableStateOf(false) }
    var stagedReseedTargetUrl by remember { mutableStateOf<String?>(null) }
    var pendingReseedCutoverTarget by remember { mutableStateOf<String?>(null) }
    var pendingReseedCommitTarget by remember { mutableStateOf<String?>(null) }
    var reseedSourceConflict by remember { mutableStateOf<PallaSyncReseedSourceConflict.Summary?>(null) }
    var showReseedSourceConflict by remember { mutableStateOf(false) }
    var canAuditReseedSource by remember { mutableStateOf(false) }
    var auditingReseedSource by remember { mutableStateOf(false) }
    var pendingDeletionProof by remember { mutableStateOf<String?>(null) }
    var pendingDeletionArchive by remember { mutableStateOf<String?>(null) }
    var deletedChainTargets by remember { mutableStateOf<List<ProtectedEvidenceEntity>>(emptyList()) }
    var selectedDeletedChain by remember { mutableStateOf<String?>(null) }
    var retainedDeletionRevisions by remember { mutableStateOf<List<PallaSyncDeletionEvidenceRevision>>(emptyList()) }
    var showRetainedDeletionRevisions by remember { mutableStateOf(false) }
    var witnessInputArchive by remember { mutableStateOf<String?>(null) }
    var witnessInputPreview by remember { mutableStateOf<PallaSyncDeletionWitnessPreview?>(null) }
    var witnessOutputArchive by remember { mutableStateOf<String?>(null) }
    var exportedDeletionProof by remember { mutableStateOf<String?>(null) }
    var showOfflineDeletionCopyConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var isCreatingChain by remember { mutableStateOf(false) }
    var publishingSnapshot by remember { mutableStateOf(false) }
    var showTrustDialog by remember { mutableStateOf(false) }
    var showPolicyDialog by remember { mutableStateOf(false) }
    var generatedMnemonic by remember { mutableStateOf<String?>(null) }
    var hasLocalChain by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<com.yunfie.illustia.pallasync.data.PallaSyncDeviceEntity>>(emptyList()) }
    var clientStatus by remember { mutableStateOf<PallaSyncClientStatus?>(null) }
    var relayShutdownNotice by remember { mutableStateOf<PallaSyncRetryGate.RelayShutdownNotice?>(null) }
    var fetchingRelayNotice by remember { mutableStateOf(false) }
    var showTrustedSnapshotDialog by remember { mutableStateOf(false) }
    var loadingTrustedSnapshots by remember { mutableStateOf(false) }
    var installingTrustedSnapshot by remember { mutableStateOf(false) }
    var trustedSnapshotFailure by remember { mutableStateOf<String?>(null) }
    var trustedSnapshotCandidates by remember {
        mutableStateOf<List<com.yunfie.illustia.pallasync.PallaSyncTrustedSnapshotCandidate>>(emptyList())
    }
    var legacyMigrations by remember { mutableStateOf<List<PallaSyncLocalIntents.LegacyMigrationProgress>>(emptyList()) }
    var legacyMigrationCursor by remember { mutableStateOf<String?>(null) }
    var loadingLegacyMigrationPage by remember { mutableStateOf(false) }
    var unresolvedQuarantine by remember { mutableStateOf<List<QuarantineSummaryRow>>(emptyList()) }
    var quarantineCursor by remember { mutableStateOf<String?>(null) }
    var loadingQuarantinePage by remember { mutableStateOf(false) }
    var retryingQuarantineHash by remember { mutableStateOf<String?>(null) }
    var pendingQuarantineExclusion by remember { mutableStateOf<QuarantineSummaryRow?>(null) }
    var showCapacityRecoveryConfirm by remember { mutableStateOf(false) }
    var resolvingCapacityRecovery by remember { mutableStateOf(false) }
    var showFavoriteOrderReindexConfirm by remember { mutableStateOf(false) }
    var reindexingFavoriteOrder by remember { mutableStateOf(false) }
    var pendingBlockedRetryCode by remember { mutableStateOf<String?>(null) }
    var retryingBlockedRequestCode by remember { mutableStateOf<String?>(null) }
    var selectedLegacyMigration by remember { mutableStateOf<PallaSyncLocalIntents.LegacyMigrationProgress?>(null) }
    var pendingLegacyReport by remember { mutableStateOf<PallaSyncLocalIntents.LegacyMigrationProgress?>(null) }
    var legacyDecisions by remember { mutableStateOf<List<PallaSyncLocalIntents.LegacySourceDecision>>(emptyList()) }
    var legacyDecisionCursor by remember { mutableStateOf<Long?>(null) }
    var legacyCandidates by remember { mutableStateOf<List<PallaSyncLocalIntents.LegacyCandidate>>(emptyList()) }
    var legacyCandidateCursor by remember { mutableStateOf<Long?>(null) }
    var loadingLegacyDecisions by remember { mutableStateOf(false) }
    var showLegacySourceDialog by remember { mutableStateOf(false) }
    var inspectingLegacySource by remember { mutableStateOf(false) }
    var legacySourceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val legacyReportExporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val migration = pendingLegacyReport
            pendingLegacyReport = null
            if (uri == null || migration == null) {
                Toast.makeText(context, R.string.pallasync_legacy_migration_report_cancelled, Toast.LENGTH_SHORT).show()
            } else {
                scope.launch {
                    try {
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            PallaSyncLegacyMigrationReport.export(
                                context.cacheDir,
                                context.contentResolver,
                                uri,
                                migration,
                                fetchPage = { afterIndex ->
                                    syncManager.legacyMigrationDecisionPage(migration.migrationId, afterIndex)
                                },
                                fetchCandidatePage = { afterIndex ->
                                    syncManager.legacyMigrationCandidatePage(migration.migrationId, afterIndex)
                                },
                                fetchSourcePage = { afterSequence ->
                                    syncManager.legacyMigrationSourceEvidencePage(migration.migrationId, afterSequence)
                                },
                            )
                        }
                        Toast.makeText(context, R.string.pallasync_legacy_migration_report_exported, Toast.LENGTH_LONG).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IOException) {
                        Toast.makeText(context, R.string.pallasync_legacy_migration_report_failed, Toast.LENGTH_LONG).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(context, R.string.pallasync_legacy_migration_report_failed, Toast.LENGTH_LONG).show()
                    } catch (_: IllegalStateException) {
                        Toast.makeText(context, R.string.pallasync_legacy_migration_report_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    val relayExportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PALLASYNC_EXPORT_MEDIA_TYPE)) { uri ->
            if (uri == null) {
                Toast.makeText(context, R.string.pallasync_export_cancelled, Toast.LENGTH_SHORT).show()
            } else {
                scope.launch {
                    try {
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val expectedSource =
                                syncManager.exportRelayArchive(
                                    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "C_LOCAL_STORAGE_FULL" },
                                )
                            val verified =
                                checkNotNull(context.contentResolver.openInputStream(uri)) { "C_SCHEMA_VIOLATION" }.use(
                                    { input -> PallaSyncExportArchiveVerifier.verify(input, expectedSource) },
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

    val recoveryRelayExportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PALLASYNC_EXPORT_MEDIA_TYPE)) { uri ->
            if (uri == null) {
                Toast.makeText(context, R.string.pallasync_export_cancelled, Toast.LENGTH_SHORT).show()
            } else {
                recoveryExportUri = uri
                showRecoveryExportRootInput = true
            }
        }

    val recoveryArchiveVerifier =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                Toast.makeText(context, R.string.pallasync_archive_verify_cancelled, Toast.LENGTH_SHORT).show()
            } else {
                scope.launch {
                    try {
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val expectedSource = syncManager.recoveryArchiveExpectedSource()
                            val verified =
                                checkNotNull(context.contentResolver.openInputStream(uri)) { "C_SCHEMA_VIOLATION" }
                                    .use { input -> syncManager.verifyRelayArchive(input, expectedSource) }
                            check(verified.includeRecovery && verified.complete) { "C_SCHEMA_VIOLATION" }
                        }
                        pendingRecoveryArchiveUri = uri
                        Toast.makeText(context, R.string.pallasync_archive_verify_complete, Toast.LENGTH_LONG).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IOException) {
                        Toast.makeText(context, R.string.pallasync_archive_restore_failed, Toast.LENGTH_LONG).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(context, R.string.pallasync_archive_restore_failed, Toast.LENGTH_LONG).show()
                    } catch (_: RuntimeException) {
                        Toast.makeText(context, R.string.pallasync_archive_restore_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    val checkpointExporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) {
                Toast.makeText(context, R.string.pallasync_checkpoint_cancelled, Toast.LENGTH_SHORT).show()
            } else {
                scope.launch {
                    try {
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val checkpoint = syncManager.createDeviceCheckpoint()
                            val expected = PallaSyncCheckpointFile.encode(checkpoint)
                            checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "C_LOCAL_STORAGE_FULL" }
                                .use { PallaSyncCheckpointFile.write(it, checkpoint) }
                            val actual =
                                checkNotNull(context.contentResolver.openInputStream(uri)) { "C_SCHEMA_VIOLATION" }
                                    .use(PallaSyncCheckpointFile::read)
                                    .toByteArray(Charsets.UTF_8)
                            check(expected.contentEquals(actual)) { "C_SCHEMA_VIOLATION" }
                        }
                        Toast.makeText(context, R.string.pallasync_checkpoint_saved, Toast.LENGTH_LONG).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IOException) {
                        Toast.makeText(context, R.string.pallasync_checkpoint_failed, Toast.LENGTH_LONG).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(context, R.string.pallasync_checkpoint_failed, Toast.LENGTH_LONG).show()
                    } catch (_: RuntimeException) {
                        Toast.makeText(context, R.string.pallasync_checkpoint_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    val checkpointImporter =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                Toast.makeText(context, R.string.pallasync_checkpoint_cancelled, Toast.LENGTH_SHORT).show()
            } else {
                scope.launch {
                    try {
                        val outcome =
                            withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val checkpoint =
                                    checkNotNull(context.contentResolver.openInputStream(uri)) { "C_SCHEMA_VIOLATION" }
                                        .use(PallaSyncCheckpointFile::read)
                                syncManager.importDeviceCheckpoint(checkpoint)
                            }
                        Toast
                            .makeText(
                                context,
                                if (outcome == "verified_data_and_transparency_prefix") {
                                    R.string.pallasync_checkpoint_import_verified
                                } else {
                                    R.string.pallasync_checkpoint_import_partial
                                },
                                Toast.LENGTH_LONG,
                            ).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IOException) {
                        Toast.makeText(context, R.string.pallasync_checkpoint_import_failed, Toast.LENGTH_LONG).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(context, R.string.pallasync_checkpoint_import_failed, Toast.LENGTH_LONG).show()
                    } catch (error: RuntimeException) {
                        Toast
                            .makeText(
                                context,
                                if (error.message == "C_DEVICE_EQUIVOCATION") {
                                    R.string.pallasync_checkpoint_conflict
                                } else {
                                    R.string.pallasync_checkpoint_import_failed
                                },
                                Toast.LENGTH_LONG,
                            ).show()
                    }
                }
            }
        }

    LaunchedEffect(state.settings.pallaSyncEnabled, hasLocalChain) {
        if (!state.settings.pallaSyncEnabled && !hasLocalChain) {
            clientStatus = null
            relayShutdownNotice = null
            legacyMigrations = emptyList()
            return@LaunchedEffect
        }
        while (isActive) {
            try {
                pendingReseedCutoverTarget = syncManager.pendingReseedCutoverTarget()
                pendingReseedCommitTarget = syncManager.pendingReseedCommitTarget()
                canAuditReseedSource = syncManager.canAuditReseedSource()
                reseedSourceConflict = syncManager.pendingReseedSourceConflict()
                relayShutdownNotice =
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        syncManager.relayShutdownNotice(state.settings.pallaSyncServerUrl)
                    }
                if (state.settings.pallaSyncEnabled) clientStatus = syncManager.getClientStatus()
                val page = syncManager.legacyMigrationProgressPage(limit = 20)
                legacyMigrations =
                    (legacyMigrations.associateBy { it.migrationId } + page.items.associateBy { it.migrationId }).values.toList()
                if (legacyMigrationCursor == null) legacyMigrationCursor = page.nextMigrationId
                if (state.settings.pallaSyncEnabled) {
                    val quarantinePage = syncManager.getUnresolvedQuarantinePage(limit = 50)
                    unresolvedQuarantine =
                        (
                            unresolvedQuarantine.associateBy {
                                it.objectHash
                            } + quarantinePage.items.associateBy { it.objectHash }
                        ).values.toList()
                    if (quarantineCursor == null) quarantineCursor = quarantinePage.nextHash
                }
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
                                    ) { "C_DELETION_UNVERIFIED" }.use {
                                        PallaSyncDeletionEvidenceArchive.readBounded(
                                            it,
                                            PallaSyncDeletionEvidenceArchive.MAX_PROTOCOL_BODY_BYTES,
                                        )
                                    }
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
                            check(bytes.size <= PallaSyncDeletionEvidenceArchive.MAX_BYTES) { "C_LOCAL_LIMIT" }
                            checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "C_LOCAL_STORAGE_FULL" }.use { output ->
                                output.write(bytes)
                                output.flush()
                            }
                            val readBack =
                                checkNotNull(
                                    context.contentResolver.openInputStream(uri),
                                ) { "C_DELETION_UNVERIFIED" }.use {
                                    PallaSyncDeletionEvidenceArchive.readBounded(it, PallaSyncDeletionEvidenceArchive.MAX_BYTES)
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
                            check(bytes.size <= PallaSyncDeletionEvidenceArchive.MAX_BYTES) { "C_LOCAL_LIMIT" }
                            checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "C_LOCAL_STORAGE_FULL" }.use { output ->
                                output.write(bytes)
                                output.flush()
                            }
                            val readBack =
                                checkNotNull(
                                    context.contentResolver.openInputStream(uri),
                                ) { "C_DELETION_UNVERIFIED" }.use {
                                    PallaSyncDeletionEvidenceArchive.readBounded(it, PallaSyncDeletionEvidenceArchive.MAX_BYTES)
                                }
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
                                    PallaSyncDeletionEvidenceArchive.readBounded(it, PallaSyncDeletionEvidenceArchive.MAX_BYTES)
                                }
                            }
                        val archive = PallaSyncDeletionEvidenceArchive.decodeUtf8(bytes)
                        val value = PallaSyncDeletionEvidenceArchive.parseStrictObject(archive)
                        check(value.keys == setOf("delete_receipt", "evidence_bundle")) { "C_SCHEMA_VIOLATION" }
                        witnessInputPreview = checkNotNull(syncManager.previewDeletionIntentWitness(archive)) { "C_DELETION_UNVERIFIED" }
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
        deletedChainTargets = syncManager.retainedDeletedChainTargets()
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
                    MiuixTextButton(
                        onClick = { showUrlDialog = false },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        text = stringResource(R.string.action_cancel),
                    )
                    Spacer(Modifier.width(20.dp))
                    MiuixTextButton(
                        onClick = {
                            val normalized = syncManager.normalizeServerUrl(tempUrl)
                            if (normalized == null) {
                                Toast.makeText(context, R.string.error_pallasync_invalid_server_url, Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.updatePallaSyncServerUrl(normalized)
                                showUrlDialog = false
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        text = stringResource(R.string.action_confirm),
                    )
                }
            }
        }
    }

    if (showReseedImportTarget) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.pallasync_reseed_import_title),
            summary = stringResource(R.string.pallasync_reseed_import_target_desc),
            backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest,
            onDismissRequest = { showReseedImportTarget = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TextField(
                    value = reseedImportTargetUrl,
                    onValueChange = { reseedImportTargetUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MiuixTextButton(
                        onClick = { showReseedImportTarget = false },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        text = stringResource(R.string.action_cancel),
                    )
                    Spacer(Modifier.width(20.dp))
                    MiuixTextButton(
                        onClick = {
                            val normalized = syncManager.normalizeServerUrl(reseedImportTargetUrl)
                            if (normalized == null) {
                                Toast.makeText(context, R.string.error_pallasync_invalid_server_url, Toast.LENGTH_SHORT).show()
                            } else {
                                selectedReseedImportTargetUrl = normalized
                                showReseedImportTarget = false
                                showReseedImportRootInput = true
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        text = stringResource(R.string.action_confirm),
                    )
                }
            }
        }
    }

    if (showCapacityRecoveryConfirm) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_relay_capacity_confirm_title),
            summary = stringResource(R.string.pallasync_relay_capacity_confirm_summary),
            confirmText = stringResource(R.string.action_confirm),
            destructive = false,
            onConfirm = {
                showCapacityRecoveryConfirm = false
                resolvingCapacityRecovery = true
                scope.launch {
                    val resolved =
                        try {
                            syncManager.confirmRelayCapacityRecovered()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            false
                        } finally {
                            resolvingCapacityRecovery = false
                        }
                    clientStatus = syncManager.getClientStatus()
                    Toast
                        .makeText(
                            context,
                            if (resolved) {
                                R.string.pallasync_relay_capacity_resumed
                            } else {
                                R.string.pallasync_relay_capacity_not_resolved
                            },
                            Toast.LENGTH_LONG,
                        ).show()
                }
            },
            onDismiss = { showCapacityRecoveryConfirm = false },
        )
    }

    if (showFavoriteOrderReindexConfirm) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_favorite_order_reindex_title),
            summary = stringResource(R.string.pallasync_favorite_order_reindex_summary),
            confirmText = stringResource(R.string.pallasync_favorite_order_reindex_action),
            destructive = false,
            onConfirm = {
                showFavoriteOrderReindexConfirm = false
                reindexingFavoriteOrder = true
                scope.launch {
                    val resumed =
                        try {
                            syncManager.reindexBlockedFavoriteOrder()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            false
                        } finally {
                            reindexingFavoriteOrder = false
                        }
                    clientStatus = syncManager.getClientStatus()
                    Toast
                        .makeText(
                            context,
                            if (resumed) {
                                R.string.pallasync_favorite_order_reindex_started
                            } else {
                                R.string.pallasync_favorite_order_reindex_failed
                            },
                            Toast.LENGTH_LONG,
                        ).show()
                }
            },
            onDismiss = { showFavoriteOrderReindexConfirm = false },
        )
    }

    pendingBlockedRetryCode?.let { code ->
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_blocked_retry_title),
            summary = stringResource(R.string.pallasync_blocked_retry_summary, code),
            confirmText = stringResource(R.string.pallasync_blocked_retry_action),
            destructive = false,
            onConfirm = {
                pendingBlockedRetryCode = null
                retryingBlockedRequestCode = code
                scope.launch {
                    val resumed =
                        try {
                            syncManager.retryBlockedRequestsAfterResolution(code)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            false
                        } finally {
                            retryingBlockedRequestCode = null
                        }
                    clientStatus = syncManager.getClientStatus()
                    Toast
                        .makeText(
                            context,
                            if (resumed) R.string.pallasync_blocked_retry_resumed else R.string.pallasync_blocked_retry_none,
                            Toast.LENGTH_LONG,
                        ).show()
                }
            },
            onDismiss = { pendingBlockedRetryCode = null },
        )
    }

    if (pendingRecoveryArchiveUri != null) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_archive_restore_title),
            summary = stringResource(R.string.pallasync_archive_restore_summary),
            confirmText = stringResource(R.string.pallasync_archive_restore_action),
            destructive = true,
            onConfirm = {
                val uri = checkNotNull(pendingRecoveryArchiveUri)
                pendingRecoveryArchiveUri = null
                recoveryArchiveRestoreUri = uri
                showRecoveryArchiveRestoreRootInput = true
            },
            onDismiss = { pendingRecoveryArchiveUri = null },
        )
    }

    pendingQuarantineExclusion?.let { entry ->
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_quarantine_exclude_title),
            summary = stringResource(R.string.pallasync_quarantine_exclude_summary, entry.reason, entry.objectHash),
            confirmText = stringResource(R.string.pallasync_quarantine_exclude_action),
            destructive = true,
            onConfirm = {
                pendingQuarantineExclusion = null
                scope.launch {
                    val recorded =
                        try {
                            syncManager.explicitlyExcludeQuarantinedRecord(entry.objectHash)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            false
                        }
                    if (recorded) {
                        unresolvedQuarantine =
                            unresolvedQuarantine.map { item ->
                                if (item.objectHash == entry.objectHash) item.copy(resolutionState = "excluded") else item
                            }
                        clientStatus = syncManager.getClientStatus()
                    }
                    Toast
                        .makeText(
                            context,
                            if (recorded) {
                                R.string.pallasync_quarantine_exclude_recorded
                            } else {
                                R.string.pallasync_quarantine_exclude_failed
                            },
                            Toast.LENGTH_LONG,
                        ).show()
                }
            },
            onDismiss = { pendingQuarantineExclusion = null },
        )
    }

    if (showRecoveryArchiveRestoreRootInput) {
        PallaSyncRootInputDialog(
            title = stringResource(R.string.pallasync_archive_restore_root_title),
            explanation = stringResource(R.string.pallasync_archive_restore_root_summary),
            busy = restoringRecoveryArchive,
            onDismiss = {
                showRecoveryArchiveRestoreRootInput = false
                recoveryArchiveRestoreUri = null
            },
            onConfirm = { mnemonic, passphrase ->
                val uri = recoveryArchiveRestoreUri
                restoringRecoveryArchive = true
                scope.launch {
                    try {
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val expectedSource = syncManager.recoveryArchiveExpectedSource()
                            checkNotNull(uri) { "C_SCHEMA_VIOLATION" }
                            checkNotNull(context.contentResolver.openInputStream(uri)) { "C_SCHEMA_VIOLATION" }
                                .use { input -> syncManager.restoreRelayArchive(input, expectedSource, mnemonic, passphrase) }
                        }
                        Toast.makeText(context, R.string.pallasync_archive_restore_complete, Toast.LENGTH_LONG).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IOException) {
                        Toast.makeText(context, R.string.pallasync_archive_restore_failed, Toast.LENGTH_LONG).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(context, R.string.pallasync_archive_restore_failed, Toast.LENGTH_LONG).show()
                    } catch (_: RuntimeException) {
                        Toast.makeText(context, R.string.pallasync_archive_restore_failed, Toast.LENGTH_LONG).show()
                    } finally {
                        restoringRecoveryArchive = false
                        showRecoveryArchiveRestoreRootInput = false
                        recoveryArchiveRestoreUri = null
                    }
                }
            },
        )
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

    if (showDeletedReceiptRootInput) {
        PallaSyncRootInputDialog(
            title = stringResource(R.string.pallasync_delete_revision_refresh_title),
            explanation = stringResource(R.string.pallasync_delete_revision_refresh_desc),
            busy = deletingChain,
            onDismiss = {
                showDeletedReceiptRootInput = false
                selectedDeletedChain = null
            },
            onConfirm = { mnemonic, passphrase ->
                val chainId = selectedDeletedChain
                deletingChain = true
                scope.launch {
                    try {
                        val archive = chainId?.let { syncManager.refreshDeletedChainEvidence(it, mnemonic, passphrase) }
                        if (archive == null) {
                            Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                        } else {
                            pendingDeletionArchive = archive
                            deletionArchiveExporter.launch("pallasync-deletion-revision.json")
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.pallasync_delete_export_failed, Toast.LENGTH_LONG).show()
                    } finally {
                        deletingChain = false
                        showDeletedReceiptRootInput = false
                        selectedDeletedChain = null
                    }
                }
            },
        )
    }

    if (showRetainedDeletionRevisions) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.pallasync_delete_revision_export_retained_title),
            onDismissRequest = { showRetainedDeletionRevisions = false },
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (retainedDeletionRevisions.isEmpty()) {
                    Text(stringResource(R.string.pallasync_delete_revision_export_none))
                } else {
                    retainedDeletionRevisions.forEach { revision ->
                        SettingLinkRow(
                            title =
                                stringResource(
                                    R.string.pallasync_delete_revision_export_item,
                                    revision.transparencyIndex,
                                    revision.completedAtMs?.toString()
                                        ?: stringResource(R.string.pallasync_delete_revision_pending),
                                ),
                            summary =
                                stringResource(
                                    R.string.pallasync_delete_revision_export_item_desc,
                                    revision.completedAtMs?.toString()
                                        ?: stringResource(R.string.pallasync_delete_revision_pending),
                                ),
                            onClick = {
                                pendingDeletionArchive = revision.archiveJson
                                showRetainedDeletionRevisions = false
                                val revisionTag = revision.completedAtMs?.toString() ?: "pending-${revision.transparencyIndex}"
                                deletionArchiveExporter.launch("pallasync-deletion-revision-$revisionTag.json")
                            },
                        )
                    }
                }
            }
        }
    }

    if (showPruneConfirm) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_prune_log_title),
            summary = stringResource(R.string.pallasync_prune_log_confirm),
            confirmText = stringResource(R.string.pallasync_prune_log_title),
            destructive = true,
            onConfirm = {
                showPruneConfirm = false
                showPruneRootInput = true
            },
            onDismiss = { showPruneConfirm = false },
        )
    }

    if (showPruneRootInput) {
        PallaSyncRootInputDialog(
            title = stringResource(R.string.pallasync_prune_log_title),
            explanation = stringResource(R.string.pallasync_prune_log_confirm),
            busy = pruningLog,
            onDismiss = { showPruneRootInput = false },
            onConfirm = { mnemonic, passphrase ->
                pruningLog = true
                scope.launch {
                    try {
                        val success = syncManager.prunePublishedLog(mnemonic, passphrase)
                        Toast
                            .makeText(
                                context,
                                if (success) R.string.pallasync_prune_log_succeeded else R.string.pallasync_prune_log_failed,
                                Toast.LENGTH_LONG,
                            ).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.pallasync_prune_log_failed, Toast.LENGTH_LONG).show()
                    } finally {
                        pruningLog = false
                        showPruneRootInput = false
                    }
                }
            },
        )
    }

    if (showRepairRootInput) {
        PallaSyncRootInputDialog(
            title = stringResource(R.string.pallasync_repair_history),
            explanation = stringResource(R.string.pallasync_repair_history_confirm),
            busy = repairingRelayHistory,
            onDismiss = { showRepairRootInput = false },
            onConfirm = { mnemonic, passphrase ->
                repairingRelayHistory = true
                scope.launch {
                    try {
                        val result = syncManager.repairRetainedRelayHistory(mnemonic, passphrase)
                        Toast
                            .makeText(
                                context,
                                context.getString(
                                    if (result.rejectedCodes.isEmpty() && !result.repairRequired) {
                                        R.string.pallasync_repair_history_complete
                                    } else {
                                        R.string.pallasync_repair_history_incomplete
                                    },
                                    result.accepted,
                                    result.submitted,
                                ),
                                Toast.LENGTH_LONG,
                            ).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.pallasync_repair_history_failed, Toast.LENGTH_LONG).show()
                    } finally {
                        repairingRelayHistory = false
                        showRepairRootInput = false
                    }
                }
            },
        )
    }

    if (showCommitmentRepairRootInput) {
        PallaSyncRootInputDialog(
            title = stringResource(R.string.pallasync_quarantine_commitment_repair_title),
            explanation = stringResource(R.string.pallasync_quarantine_commitment_repair_summary),
            busy = repairingCommitmentEnvelope,
            onDismiss = {
                showCommitmentRepairRootInput = false
                pendingCommitmentRepairHash = null
            },
            onConfirm = { mnemonic, passphrase ->
                val objectHash = pendingCommitmentRepairHash
                repairingCommitmentEnvelope = true
                scope.launch {
                    try {
                        val resolved =
                            withContext(kotlinx.coroutines.Dispatchers.IO) {
                                syncManager.repairQuarantinedCommitment(checkNotNull(objectHash), mnemonic, passphrase)
                            }
                        Toast
                            .makeText(
                                context,
                                if (resolved) {
                                    R.string.pallasync_quarantine_retry_succeeded
                                } else {
                                    R.string.pallasync_quarantine_retry_failed
                                },
                                Toast.LENGTH_LONG,
                            ).show()
                        if (resolved && objectHash != null) {
                            unresolvedQuarantine = unresolvedQuarantine.filterNot { it.objectHash == objectHash }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.pallasync_quarantine_retry_failed, Toast.LENGTH_LONG).show()
                    } finally {
                        repairingCommitmentEnvelope = false
                        showCommitmentRepairRootInput = false
                        pendingCommitmentRepairHash = null
                    }
                }
            },
        )
    }

    if (showRecoveryExportRootInput) {
        PallaSyncRootInputDialog(
            title = stringResource(R.string.pallasync_export_recovery_title),
            explanation = stringResource(R.string.pallasync_export_recovery_summary),
            busy = exportingRecoveryArchive,
            onDismiss = {
                showRecoveryExportRootInput = false
                recoveryExportUri = null
            },
            onConfirm = { mnemonic, passphrase ->
                val uri = recoveryExportUri
                exportingRecoveryArchive = true
                scope.launch {
                    try {
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            checkNotNull(uri) { "C_LOCAL_STORAGE_FULL" }
                            val expectedSource =
                                checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "C_LOCAL_STORAGE_FULL" }
                                    .use { output -> syncManager.exportRelayArchiveWithRecovery(output, mnemonic, passphrase) }
                            val verified =
                                checkNotNull(context.contentResolver.openInputStream(uri)) { "C_SCHEMA_VIOLATION" }.use(
                                    { input -> syncManager.verifyRelayArchive(input, expectedSource) },
                                )
                            check(verified.includeRecovery && verified.complete) { "C_SCHEMA_VIOLATION" }
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
                    } catch (_: RuntimeException) {
                        Toast.makeText(context, R.string.pallasync_export_failed, Toast.LENGTH_LONG).show()
                    } finally {
                        exportingRecoveryArchive = false
                        showRecoveryExportRootInput = false
                        recoveryExportUri = null
                    }
                }
            },
        )
    }

    if (showReseedImportRootInput) {
        PallaSyncRootInputDialog(
            title = stringResource(R.string.pallasync_reseed_import_title),
            explanation = stringResource(R.string.pallasync_reseed_import_confirm),
            busy = stagingReseedImport,
            onDismiss = {
                showReseedImportRootInput = false
                selectedReseedImportTargetUrl = null
            },
            onConfirm = { mnemonic, passphrase ->
                val target = selectedReseedImportTargetUrl
                if (target == null) {
                    showReseedImportRootInput = false
                    Toast.makeText(context, R.string.pallasync_reseed_import_failed, Toast.LENGTH_LONG).show()
                } else {
                    stagingReseedImport = true
                    scope.launch {
                        try {
                            val result = syncManager.stageReseedHistory(target, mnemonic, passphrase)
                            val message =
                                when (result) {
                                    is PallaSyncHttpResult.Success -> {
                                        stagedReseedTargetUrl = target
                                        showReseedCommitConfirm = true
                                        context.getString(
                                            R.string.pallasync_reseed_import_staged,
                                            result.value.chainId,
                                            result.value.logId,
                                        )
                                    }

                                    is PallaSyncHttpResult.ProtocolError, PallaSyncHttpResult.Gone, is PallaSyncHttpResult.Retryable -> {
                                        context.getString(R.string.pallasync_reseed_import_failed)
                                    }
                                }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            Toast.makeText(context, R.string.pallasync_reseed_import_failed, Toast.LENGTH_LONG).show()
                        } finally {
                            stagingReseedImport = false
                            showReseedImportRootInput = false
                            selectedReseedImportTargetUrl = null
                        }
                    }
                }
            },
        )
    }

    if (showReseedCommitConfirm) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_reseed_commit_title),
            summary = stringResource(R.string.pallasync_reseed_cutover_freeze_required, stagedReseedTargetUrl.orEmpty()),
            confirmText = stringResource(R.string.pallasync_reseed_sources_paused),
            destructive = true,
            onConfirm = {
                showReseedCommitConfirm = false
                showReseedCommitRootInput = true
            },
            onDismiss = {
                showReseedCommitConfirm = false
                stagedReseedTargetUrl = null
            },
        )
    }

    if (showReseedCommitRootInput) {
        PallaSyncRootInputDialog(
            title = stringResource(R.string.pallasync_reseed_commit_title),
            explanation = stringResource(R.string.pallasync_reseed_cutover_freeze_required, stagedReseedTargetUrl.orEmpty()),
            busy = committingReseedImport,
            onDismiss = {
                showReseedCommitRootInput = false
                stagedReseedTargetUrl = null
            },
            onConfirm = { mnemonic, passphrase ->
                val target = stagedReseedTargetUrl
                if (target == null) {
                    showReseedCommitRootInput = false
                    Toast.makeText(context, R.string.pallasync_reseed_import_failed, Toast.LENGTH_LONG).show()
                } else {
                    committingReseedImport = true
                    scope.launch {
                        try {
                            when (val result = syncManager.commitStagedReseedHistory(target, mnemonic, passphrase)) {
                                is PallaSyncHttpResult.Success -> {
                                    Toast.makeText(context, R.string.pallasync_reseed_commit_completed, Toast.LENGTH_LONG).show()
                                }

                                PallaSyncHttpResult.Gone, is PallaSyncHttpResult.ProtocolError, is PallaSyncHttpResult.Retryable -> {
                                    Toast.makeText(context, R.string.pallasync_reseed_import_failed, Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            Toast.makeText(context, R.string.pallasync_reseed_import_failed, Toast.LENGTH_LONG).show()
                        } finally {
                            committingReseedImport = false
                            showReseedCommitRootInput = false
                            stagedReseedTargetUrl = null
                        }
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
        val preview = checkNotNull(witnessInputPreview)
        val info =
            "${preview.chainId}\n${preview.deletionId}\n" +
                stringResource(R.string.pallasync_witness_receipt_hash, preview.receiptHash) + "\n" +
                stringResource(R.string.pallasync_witness_assurance, preview.assurance) + "\n" +
                stringResource(R.string.pallasync_witness_requested_at, preview.requestedAtMs) + "\n" +
                stringResource(R.string.pallasync_witness_existing_count, preview.existingWitnessCount)
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.pallasync_witness_confirm_title),
            summary = stringResource(R.string.pallasync_witness_confirm_summary, info),
            confirmText = stringResource(R.string.pallasync_witness_confirm_action),
            onConfirm = {
                witnessInputArchive = null
                witnessInputPreview = null
                scope.launch {
                    try {
                        val activity = context as? FragmentActivity ?: error("C_RECOVERY_AUTH_REQUIRED")
                        witnessOutputArchive =
                            authenticateDeletionWitness(
                                activity = activity,
                                context = context,
                                receiptHash = preview.receiptHash,
                                title = context.getString(R.string.pallasync_witness_auth_title),
                                subtitle = context.getString(R.string.pallasync_witness_auth_summary),
                            ) {
                                syncManager.addDeletionIntentWitness(archive, explicitlyConfirmed = true)
                            }
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
            onDismiss = {
                witnessInputArchive = null
                witnessInputPreview = null
            },
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

    reseedSourceConflict?.let { conflict ->
        MiuixConfirmDialog(
            show = showReseedSourceConflict,
            title = stringResource(R.string.pallasync_reseed_source_conflict_title),
            summary =
                stringResource(
                    R.string.pallasync_reseed_source_conflict_summary,
                    conflict.sourceOrigin,
                    conflict.entryCount,
                    conflict.firstSequence,
                    conflict.lastSequence,
                ),
            confirmText = stringResource(R.string.action_close),
            onConfirm = { showReseedSourceConflict = false },
            onDismiss = { showReseedSourceConflict = false },
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

    if (showTrustedSnapshotDialog) {
        PallaSyncTrustedSnapshotDialog(
            candidates = trustedSnapshotCandidates,
            busy = installingTrustedSnapshot,
            failure = trustedSnapshotFailure,
            onDismiss = { if (!installingTrustedSnapshot) showTrustedSnapshotDialog = false },
            onTrustAndInstall = { candidate ->
                installingTrustedSnapshot = true
                trustedSnapshotFailure = null
                scope.launch {
                    try {
                        val installed =
                            withContext(kotlinx.coroutines.Dispatchers.IO) {
                                syncManager.trustAndInstallSnapshot(
                                    candidate.compactionId,
                                    candidate.compactionHash,
                                    explicitlyConfirmed = true,
                                )
                            }
                        check(installed) { "C_MISSING_DEPENDENCY" }
                        val suffixComplete = syncManager.syncNow()
                        clientStatus = syncManager.getClientStatus()
                        showTrustedSnapshotDialog = false
                        Toast
                            .makeText(
                                context,
                                if (suffixComplete) {
                                    R.string.pallasync_snapshot_bootstrap_installed
                                } else {
                                    R.string.error_pallasync_sync_failed
                                },
                                Toast.LENGTH_LONG,
                            ).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        trustedSnapshotFailure = failure.message?.takeIf { it.startsWith("C_") } ?: "C_SNAPSHOT_INVALID"
                    } finally {
                        installingTrustedSnapshot = false
                    }
                }
            },
        )
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
                        deletedChainTargets.forEach { target ->
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_delete_revision_refresh_title),
                                summary = stringResource(R.string.pallasync_delete_revision_target, target.origin, target.chainId),
                                onClick = {
                                    selectedDeletedChain = target.chainId
                                    showDeletedReceiptRootInput = true
                                },
                            )
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_delete_revision_export_retained_title),
                                summary = stringResource(R.string.pallasync_delete_revision_export_retained_desc),
                                onClick = {
                                    scope.launch {
                                        try {
                                            retainedDeletionRevisions = syncManager.retainedDeletedChainEvidenceRevisions(target.chainId)
                                            showRetainedDeletionRevisions = true
                                        } catch (_: Exception) {
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
                                title = stringResource(R.string.pallasync_checkpoint_export_title),
                                summary = stringResource(R.string.pallasync_checkpoint_export_summary),
                                onClick = { checkpointExporter.launch("pallasync-checkpoint.json") },
                            )
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_checkpoint_import_title),
                                summary = stringResource(R.string.pallasync_checkpoint_import_summary),
                                onClick = { checkpointImporter.launch(arrayOf("application/json")) },
                            )
                        }
                        if (state.settings.pallaSyncEnabled) {
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_export_title),
                                summary = stringResource(R.string.pallasync_export_device_summary),
                                onClick = { relayExportLauncher.launch("pallasync-device-export.jsonl") },
                            )
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_export_recovery_title),
                                summary = stringResource(R.string.pallasync_export_recovery_summary),
                                onClick = { recoveryRelayExportLauncher.launch("pallasync-admin-export.jsonl") },
                            )
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_archive_verify_title),
                                summary = stringResource(R.string.pallasync_archive_verify_summary),
                                onClick = {
                                    recoveryArchiveVerifier.launch(
                                        arrayOf(PALLASYNC_EXPORT_MEDIA_TYPE, "application/jsonl", "application/json"),
                                    )
                                },
                            )
                        }
                        if (state.settings.pallaSyncEnabled) {
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_repair_history),
                                summary = stringResource(R.string.pallasync_repair_history_desc),
                                onClick = { showRepairRootInput = true },
                            )
                        }
                        if (state.settings.pallaSyncEnabled) {
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_reseed_import_title),
                                summary = stringResource(R.string.pallasync_reseed_import_target_desc),
                                onClick = {
                                    reseedImportTargetUrl = ""
                                    showReseedImportTarget = true
                                },
                            )
                        }
                        pendingReseedCutoverTarget?.let { target ->
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_reseed_resume_title),
                                summary = stringResource(R.string.pallasync_reseed_resume_summary, target),
                                onClick = {
                                    scope.launch {
                                        try {
                                            val resumed = syncManager.resumeStagedReseedCutover()
                                            pendingReseedCutoverTarget = syncManager.pendingReseedCutoverTarget()
                                            Toast
                                                .makeText(
                                                    context,
                                                    if (resumed) {
                                                        R.string.pallasync_reseed_commit_completed
                                                    } else {
                                                        R.string.pallasync_reseed_import_failed
                                                    },
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            Toast.makeText(context, R.string.pallasync_reseed_import_failed, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                            )
                        }
                        pendingReseedCommitTarget?.let { target ->
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_reseed_retry_title),
                                summary = stringResource(R.string.pallasync_reseed_retry_summary, target),
                                onClick = {
                                    stagedReseedTargetUrl = target
                                    showReseedCommitConfirm = true
                                },
                            )
                        }
                        if (canAuditReseedSource) {
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_reseed_source_audit_title),
                                summary = stringResource(R.string.pallasync_reseed_source_audit_summary),
                                onClick = {
                                    if (!auditingReseedSource) {
                                        auditingReseedSource = true
                                        scope.launch {
                                            try {
                                                val conflict = syncManager.auditReseedSource()
                                                reseedSourceConflict = conflict
                                                if (conflict == null) {
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            R.string.pallasync_reseed_source_audit_clean,
                                                            Toast.LENGTH_LONG,
                                                        ).show()
                                                } else {
                                                    showReseedSourceConflict = true
                                                }
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                Toast
                                                    .makeText(
                                                        context,
                                                        R.string.pallasync_reseed_source_audit_failed,
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                            } finally {
                                                auditingReseedSource = false
                                            }
                                        }
                                    }
                                },
                            )
                        }
                        reseedSourceConflict?.let { conflict ->
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_reseed_source_conflict_title),
                                summary =
                                    stringResource(
                                        R.string.pallasync_reseed_source_conflict_summary,
                                        conflict.sourceOrigin,
                                        conflict.entryCount,
                                        conflict.firstSequence,
                                        conflict.lastSequence,
                                    ),
                                onClick = { showReseedSourceConflict = true },
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
                                title = stringResource(R.string.pallasync_snapshot_publish),
                                summary =
                                    stringResource(
                                        if (publishingSnapshot) {
                                            R.string.pallasync_snapshot_publish_running
                                        } else {
                                            R.string.pallasync_snapshot_publish_desc
                                        },
                                    ),
                                onClick = {
                                    if (!publishingSnapshot) {
                                        publishingSnapshot = true
                                        scope.launch {
                                            try {
                                                val success = syncManager.publishSnapshot()
                                                Toast
                                                    .makeText(
                                                        context,
                                                        if (success) {
                                                            R.string.pallasync_snapshot_publish_succeeded
                                                        } else {
                                                            R.string.pallasync_snapshot_publish_failed
                                                        },
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                            } finally {
                                                publishingSnapshot = false
                                            }
                                        }
                                    }
                                },
                            )
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.pallasync_prune_log_title),
                                summary = stringResource(R.string.pallasync_prune_log_desc),
                                onClick = { if (!pruningLog) showPruneConfirm = true },
                            )
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
                item { PallaSyncRecoveryControls(syncManager, onManagePallaSyncDevices) }
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
                if (legacyMigrations.isNotEmpty() || (state.settings.pallaSyncEnabled && hasLocalChain)) {
                    Section(stringResource(R.string.pallasync_legacy_migration_title)) {
                        ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                            if (state.settings.pallaSyncEnabled && hasLocalChain) {
                                SettingLinkRow(
                                    title = stringResource(R.string.pallasync_legacy_source_action),
                                    summary = stringResource(R.string.pallasync_legacy_source_action_summary),
                                    onClick = { if (!inspectingLegacySource) showLegacySourceDialog = true },
                                )
                                if (legacyMigrations.isNotEmpty()) DividerLine()
                            }
                            legacyMigrations.forEachIndexed { index, migration ->
                                SettingLinkRow(
                                    title =
                                        stringResource(
                                            R.string.pallasync_legacy_migration_item,
                                            migration.sourceChain,
                                            migration.targetChain,
                                        ),
                                    summary =
                                        stringResource(
                                            R.string.pallasync_legacy_migration_summary,
                                            migration.stagedThrough,
                                            migration.sourceHighWater,
                                            migration.candidates,
                                            migration.pending,
                                            migration.constructed,
                                            migration.quarantined,
                                        ),
                                    onClick = {
                                        selectedLegacyMigration = migration
                                        legacyDecisions = emptyList()
                                        legacyDecisionCursor = null
                                        legacyCandidates = emptyList()
                                        legacyCandidateCursor = null
                                        loadingLegacyDecisions = true
                                        scope.launch {
                                            try {
                                                val page = syncManager.legacyMigrationDecisionPage(migration.migrationId, 0)
                                                legacyDecisions = page.items
                                                legacyDecisionCursor = page.nextIndex
                                                val candidatePage = syncManager.legacyMigrationCandidatePage(migration.migrationId, 0)
                                                legacyCandidates = candidatePage.items
                                                legacyCandidateCursor = candidatePage.nextIndex
                                            } catch (_: Exception) {
                                                Toast
                                                    .makeText(
                                                        context,
                                                        R.string.pallasync_legacy_migration_report_failed,
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                            } finally {
                                                loadingLegacyDecisions = false
                                            }
                                        }
                                    },
                                )
                                if (index < legacyMigrations.lastIndex) DividerLine()
                            }
                            legacyMigrationCursor?.let { cursor ->
                                if (!loadingLegacyMigrationPage) {
                                    DividerLine()
                                    PallaSyncActionButton(
                                        text = stringResource(R.string.pallasync_legacy_migration_more),
                                        onClick = {
                                            loadingLegacyMigrationPage = true
                                            scope.launch {
                                                try {
                                                    val page = syncManager.legacyMigrationProgressPage(cursor, 20)
                                                    legacyMigrations = legacyMigrations + page.items
                                                    legacyMigrationCursor = page.nextMigrationId
                                                } catch (_: Exception) {
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            R.string.pallasync_legacy_migration_report_failed,
                                                            Toast.LENGTH_LONG,
                                                        ).show()
                                                } finally {
                                                    loadingLegacyMigrationPage = false
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (hasLocalChain) {
                item {
                    Button(
                        onClick = {
                            scope.launch {
                                fetchingRelayNotice = true
                                try {
                                    relayShutdownNotice = syncManager.fetchRelayShutdownNotice()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    Toast.makeText(context, R.string.pallasync_relay_notice_unavailable, Toast.LENGTH_SHORT).show()
                                } finally {
                                    fetchingRelayNotice = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !fetchingRelayNotice,
                    ) {
                        Text(
                            stringResource(
                                if (fetchingRelayNotice) {
                                    R.string.pallasync_relay_notice_loading
                                } else {
                                    R.string.pallasync_relay_notice_fetch
                                },
                            ),
                        )
                    }
                }
            }

            relayShutdownNotice?.let { notice ->
                item {
                    Section(stringResource(R.string.pallasync_relay_shutdown_title)) {
                        ElevatedPanel(contentPadding = PaddingValues(16.dp)) {
                            if (notice.writesStopped) {
                                Text(stringResource(R.string.pallasync_relay_shutdown_writes_stopped))
                            } else if (notice.shutdownAtMs == null) {
                                Text(stringResource(R.string.pallasync_relay_shutdown_unscheduled))
                            } else {
                                Text(stringResource(R.string.pallasync_relay_shutdown_at, notice.shutdownAtMs))
                            }
                            notice.noticeId?.let { noticeId ->
                                Text(stringResource(R.string.pallasync_relay_notice_id, noticeId))
                            }
                            notice.migrationOrigin?.let { suggestedOrigin ->
                                Text(stringResource(R.string.pallasync_relay_shutdown_suggestion, suggestedOrigin))
                                Text(stringResource(R.string.pallasync_relay_shutdown_suggestion_limit))
                                Button(
                                    onClick = {
                                        // This fills a user-reviewed target field; it does not contact the candidate.
                                        reseedImportTargetUrl = suggestedOrigin
                                        showReseedImportTarget = true
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !stagingReseedImport && !committingReseedImport,
                                ) {
                                    Text(stringResource(R.string.pallasync_relay_shutdown_review_migration))
                                }
                            }
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
                                    status.downloadedSeq,
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
                            Text(stringResource(R.string.pallasync_progress_author_gaps, status.authorGaps))
                            Text(stringResource(R.string.pallasync_progress_source_completeness))
                            PallaSyncIndependentCheckpointComparisonSummary(status)
                            Text(
                                stringResource(
                                    R.string.pallasync_progress_blockers,
                                    status.pendingOutbox,
                                    status.unresolvedQuarantine,
                                ),
                            )
                            if (status.retryAfterMs > 0) {
                                Text(stringResource(R.string.pallasync_retry_wait, (status.retryAfterMs + 999) / 1000))
                            }
                            status.originBlockedRequestCodes.forEach { code ->
                                Text(stringResource(R.string.pallasync_origin_request_blocked, code))
                                PallaSyncRecoveryHintText(code)
                            }
                            val blockedCodes =
                                status.blockedRequestCodes.ifEmpty {
                                    listOfNotNull(status.blockedRequestCode)
                                }
                            blockedCodes.forEach { code ->
                                Text(stringResource(R.string.pallasync_request_blocked, code))
                                PallaSyncRecoveryHintText(code)
                                if (status.capacityIssue?.code != code && PallaSyncRetryGate.canRetryAfterResolution(code)) {
                                    PallaSyncActionButton(
                                        text = stringResource(R.string.pallasync_blocked_retry_action),
                                        enabled = retryingBlockedRequestCode == null,
                                        onClick = { pendingBlockedRetryCode = code },
                                    )
                                }
                            }
                            if (status.favoriteOrderReindexRequired) {
                                DividerLine()
                                Text(stringResource(R.string.pallasync_favorite_order_reindex_required))
                                PallaSyncActionButton(
                                    text = stringResource(R.string.pallasync_favorite_order_reindex_action),
                                    enabled = !reindexingFavoriteOrder,
                                    onClick = { showFavoriteOrderReindexConfirm = true },
                                )
                            }
                            status.capacityIssue?.let { issue ->
                                DividerLine()
                                val used = issue.usedBytes
                                val maximum = issue.maxBytes
                                if (used == null || maximum == null) {
                                    Text(stringResource(R.string.pallasync_relay_capacity_unknown))
                                }
                                Text(
                                    stringResource(
                                        R.string.pallasync_relay_capacity_hint,
                                        issue.code,
                                        used?.toString() ?: "—",
                                        maximum?.toString() ?: "—",
                                    ),
                                )
                                PallaSyncActionButton(
                                    text = stringResource(R.string.pallasync_relay_capacity_resume),
                                    enabled = !resolvingCapacityRecovery,
                                    onClick = { showCapacityRecoveryConfirm = true },
                                )
                            }
                            DividerLine()
                            Text(
                                stringResource(
                                    R.string.pallasync_capacity_details,
                                    stringResource(R.string.pallasync_capacity_search_scope),
                                    status.searchCount,
                                    status.searchProjectionCount,
                                    status.searchColdCount,
                                    status.searchCeiling,
                                ),
                            )
                            Text(
                                stringResource(
                                    R.string.pallasync_capacity_details,
                                    stringResource(R.string.pallasync_capacity_viewed_scope),
                                    status.viewedCount,
                                    status.viewedProjectionCount,
                                    status.viewedColdCount,
                                    status.viewedCeiling,
                                ),
                            )
                            Text(
                                stringResource(
                                    R.string.pallasync_capacity_details,
                                    stringResource(R.string.pallasync_capacity_seen_scope),
                                    status.seenCount,
                                    status.seenProjectionCount,
                                    status.seenColdCount,
                                    status.seenCeiling,
                                ),
                            )
                            status.availableStorageBytes?.let {
                                Text(
                                    stringResource(
                                        R.string.pallasync_capacity_available_storage,
                                        Formatter.formatFileSize(context, it),
                                    ),
                                )
                            }
                            if (status.safetyLimitReached) {
                                DividerLine()
                                Text(stringResource(R.string.pallasync_safety_limit_reached))
                            }
                            if (!status.initialPullCompleted && status.pendingOutbox == 0L && status.unresolvedQuarantine == 0L) {
                                DividerLine()
                                PallaSyncActionButton(
                                    text =
                                        stringResource(
                                            if (loadingTrustedSnapshots) {
                                                R.string.pallasync_snapshot_bootstrap_running
                                            } else {
                                                R.string.pallasync_snapshot_bootstrap
                                            },
                                        ),
                                    enabled = !loadingTrustedSnapshots && !installingTrustedSnapshot,
                                    onClick = {
                                        loadingTrustedSnapshots = true
                                        trustedSnapshotFailure = null
                                        scope.launch {
                                            try {
                                                trustedSnapshotCandidates =
                                                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                        syncManager.trustedSnapshotCandidates()
                                                    }
                                                showTrustedSnapshotDialog = true
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (failure: Exception) {
                                                trustedSnapshotFailure =
                                                    failure.message?.takeIf { it.startsWith("C_") } ?: "C_SNAPSHOT_INVALID"
                                                trustedSnapshotCandidates = emptyList()
                                                showTrustedSnapshotDialog = true
                                            } finally {
                                                loadingTrustedSnapshots = false
                                            }
                                        }
                                    },
                                )
                                Text(stringResource(R.string.pallasync_snapshot_bootstrap_desc))
                                trustedSnapshotFailure?.let { Text(it) }
                            }
                        }
                    }
                }
            }

            item {
                if (unresolvedQuarantine.isNotEmpty()) {
                    Section(stringResource(R.string.pallasync_quarantine_title)) {
                        ElevatedPanel(contentPadding = PaddingValues(16.dp)) {
                            Text(stringResource(R.string.pallasync_quarantine_explanation))
                            unresolvedQuarantine.forEach { entry ->
                                DividerLine()
                                val resolutionLabel =
                                    when (entry.resolutionState) {
                                        "excluded" -> stringResource(R.string.pallasync_quarantine_state_excluded)
                                        "pending_resolution" -> stringResource(R.string.pallasync_quarantine_state_pending)
                                        else -> stringResource(R.string.pallasync_quarantine_state_unresolved)
                                    }
                                Text(stringResource(R.string.pallasync_quarantine_item, entry.reason, entry.objectHash, resolutionLabel))
                                if (entry.reason in setOf("C_MISSING_DEPENDENCY", "C_ACCEPTANCE_UNPROVEN")) {
                                    PallaSyncActionButton(
                                        text =
                                            stringResource(
                                                if (retryingQuarantineHash == entry.objectHash) {
                                                    R.string.pallasync_quarantine_retry_running
                                                } else {
                                                    R.string.pallasync_quarantine_retry
                                                },
                                            ),
                                        enabled = retryingQuarantineHash == null,
                                        onClick = {
                                            retryingQuarantineHash = entry.objectHash
                                            scope.launch {
                                                val resolved =
                                                    try {
                                                        syncManager.retryQuarantinedRecordEvidence(entry.objectHash)
                                                    } catch (cancelled: CancellationException) {
                                                        throw cancelled
                                                    } catch (_: Exception) {
                                                        false
                                                    } finally {
                                                        retryingQuarantineHash = null
                                                    }
                                                if (resolved) {
                                                    unresolvedQuarantine =
                                                        unresolvedQuarantine.filterNot { it.objectHash == entry.objectHash }
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            R.string.pallasync_quarantine_retry_succeeded,
                                                            Toast.LENGTH_LONG,
                                                        ).show()
                                                } else {
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            R.string.pallasync_quarantine_retry_failed,
                                                            Toast.LENGTH_LONG,
                                                        ).show()
                                                }
                                            }
                                        },
                                    )
                                }
                                when (entry.reason) {
                                    "C_COMMITMENT_MISMATCH" -> {
                                        Text(stringResource(R.string.pallasync_quarantine_commitment_repair_guidance))
                                        PallaSyncActionButton(
                                            text = stringResource(R.string.pallasync_quarantine_commitment_repair_action),
                                            enabled = !repairingCommitmentEnvelope,
                                            onClick = {
                                                pendingCommitmentRepairHash = entry.objectHash
                                                showCommitmentRepairRootInput = true
                                            },
                                        )
                                    }

                                    in PallaSyncQuarantineDisposition.trustedBranchReviewReasons -> {
                                        Text(stringResource(R.string.pallasync_quarantine_reseed_guidance))
                                        PallaSyncActionButton(
                                            text = stringResource(R.string.pallasync_quarantine_reseed),
                                            enabled = !stagingReseedImport && !committingReseedImport,
                                            onClick = { showReseedImportTarget = true },
                                        )
                                    }

                                    "C_LOG_HASH_MISMATCH" -> {
                                        Text(stringResource(R.string.pallasync_quarantine_repair_guidance))
                                        PallaSyncActionButton(
                                            text = stringResource(R.string.pallasync_quarantine_repair),
                                            enabled = !repairingRelayHistory,
                                            onClick = { showRepairRootInput = true },
                                        )
                                    }

                                    "C_SNAPSHOT_INVALID" -> {
                                        Text(stringResource(R.string.pallasync_quarantine_snapshot_guidance))
                                        PallaSyncActionButton(
                                            text = stringResource(R.string.pallasync_quarantine_snapshot),
                                            enabled = trustedSnapshotCandidates.isNotEmpty() && !installingTrustedSnapshot,
                                            onClick = {
                                                trustedSnapshotFailure = null
                                                showTrustedSnapshotDialog = true
                                            },
                                        )
                                    }

                                    "C_RECORD_ID_CONFLICT" -> {
                                        Text(stringResource(R.string.pallasync_quarantine_record_identity_guidance))
                                    }

                                    in setOf("C_RECEIPT_INVALID", "C_TRANSPARENCY_INVALID") -> {
                                        Text(stringResource(R.string.pallasync_quarantine_independent_evidence_guidance))
                                        if (PallaSyncQuarantineDisposition.requiresIndependentCheckpointReview(entry.reason)) {
                                            Text(stringResource(R.string.pallasync_checkpoint_import_summary))
                                            PallaSyncActionButton(
                                                text = stringResource(R.string.pallasync_checkpoint_import_title),
                                                onClick = { checkpointImporter.launch(arrayOf("application/json")) },
                                            )
                                        }
                                    }
                                }
                                if (entry.resolutionState != "excluded" &&
                                    PallaSyncQuarantineDisposition.canExplicitlyExclude(entry.reason)
                                ) {
                                    PallaSyncActionButton(
                                        text = stringResource(R.string.pallasync_quarantine_exclude_action),
                                        enabled = pendingQuarantineExclusion == null,
                                        onClick = { pendingQuarantineExclusion = entry },
                                    )
                                }
                            }
                            quarantineCursor?.let { cursor ->
                                if (!loadingQuarantinePage) {
                                    DividerLine()
                                    PallaSyncActionButton(
                                        text = stringResource(R.string.pallasync_legacy_migration_more),
                                        onClick = {
                                            loadingQuarantinePage = true
                                            scope.launch {
                                                try {
                                                    val page = syncManager.getUnresolvedQuarantinePage(cursor, 50)
                                                    unresolvedQuarantine = unresolvedQuarantine + page.items
                                                    quarantineCursor = page.nextHash
                                                } catch (_: Exception) {
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            R.string.pallasync_legacy_migration_report_failed,
                                                            Toast.LENGTH_LONG,
                                                        ).show()
                                                } finally {
                                                    loadingQuarantinePage = false
                                                }
                                            }
                                        },
                                    )
                                }
                            }
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

    if (showLegacySourceDialog) {
        PallaSyncLegacyMigrationSourceDialog(
            busy = inspectingLegacySource,
            onDismiss = {
                legacySourceJob?.cancel()
                showLegacySourceDialog = false
            },
            onInspect = { input ->
                inspectingLegacySource = true
                legacySourceJob =
                    scope.launch {
                        try {
                            val normalizedSource = syncManager.normalizeServerUrl(input.sourceUrl)
                            check(normalizedSource == input.sourceUrl) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                            val chainState =
                                com.yunfie.illustia.pallasync.data.PallaSyncDatabase
                                    .getDatabase(context)
                                    .pallaSyncDao()
                                    .getActiveChainState()
                            val targetChain = checkNotNull(chainState?.chainId) { "C_MISSING_DEPENDENCY" }
                            PallaSyncRootSession
                                .beginText(
                                    input.mnemonic,
                                    input.passphrase,
                                    PallaSyncRootSession.Operation.NEW_ROOT_MIGRATION,
                                    expectedChain = targetChain,
                                ).use { root ->
                                    check(root.identity.chainId == targetChain) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                                }
                            var migrationId: String? = null
                            var afterMigrationId = ""
                            do {
                                val page = syncManager.legacyMigrationProgressPage(afterMigrationId, 20)
                                migrationId =
                                    page.items
                                        .firstOrNull {
                                            it.sourceProtocol == input.sourceProtocol && it.sourceChain == input.sourceChainId &&
                                                it.targetChain == targetChain
                                        }?.migrationId
                                afterMigrationId = page.nextMigrationId.orEmpty()
                            } while (migrationId == null && afterMigrationId.isNotEmpty())
                            val selectedMigrationId = migrationId ?: "legacy:${UUID.randomUUID()}"
                            val result =
                                if (input.sourceProtocol == "2.0") {
                                    syncManager.beginLegacyV20Migration(
                                        selectedMigrationId,
                                        normalizedSource,
                                        input.mnemonic,
                                        input.passphrase,
                                        input.sourceChainId,
                                        targetChain,
                                        input.writersStopped,
                                    )
                                } else {
                                    val parameters =
                                        when (
                                            val fetched =
                                                syncManager.fetchLegacyV21Parameters(
                                                    normalizedSource,
                                                    input.sourceChainId,
                                                    input.mnemonic,
                                                    input.passphrase,
                                                )
                                        ) {
                                            is PallaSyncHttpResult.Success -> fetched.value
                                            is PallaSyncHttpResult.ProtocolError -> error(fetched.message)
                                            is PallaSyncHttpResult.Retryable -> error(fetched.message)
                                            PallaSyncHttpResult.Gone -> error("C_MISSING_DEPENDENCY")
                                        }
                                    val devices =
                                        when (
                                            val fetched =
                                                syncManager.fetchLegacyV21Devices(
                                                    normalizedSource,
                                                    parameters,
                                                    input.sourceDeviceId,
                                                    input.sourceDeviceSeed,
                                                    input.sourceDevicePublicKey,
                                                )
                                        ) {
                                            is PallaSyncHttpResult.Success -> fetched.value
                                            is PallaSyncHttpResult.ProtocolError -> error(fetched.message)
                                            is PallaSyncHttpResult.Retryable -> error(fetched.message)
                                            PallaSyncHttpResult.Gone -> error("C_MISSING_DEPENDENCY")
                                        }
                                    syncManager.beginLegacyV21Migration(
                                        selectedMigrationId,
                                        normalizedSource,
                                        input.mnemonic,
                                        input.passphrase,
                                        parameters,
                                        devices,
                                        input.sourceDeviceId,
                                        input.sourceDeviceSeed,
                                        input.sourceDevicePublicKey,
                                        targetChain,
                                        input.writersStopped,
                                    )
                                }
                            when (result) {
                                is PallaSyncHttpResult.Success -> {
                                    val progress =
                                        checkNotNull(syncManager.legacyMigrationProgress(selectedMigrationId)) { "C_SCHEMA_VIOLATION" }
                                    legacyMigrations =
                                        (legacyMigrations + progress).distinctBy { it.migrationId }
                                    showLegacySourceDialog = false
                                    Toast
                                        .makeText(
                                            context,
                                            R.string.pallasync_legacy_source_verified_journal_started,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                }

                                is PallaSyncHttpResult.ProtocolError -> {
                                    error(result.message)
                                }

                                is PallaSyncHttpResult.Retryable -> {
                                    error(result.message)
                                }

                                PallaSyncHttpResult.Gone -> {
                                    error("C_MISSING_DEPENDENCY")
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            Toast.makeText(context, R.string.pallasync_legacy_source_failed, Toast.LENGTH_LONG).show()
                        } finally {
                            inspectingLegacySource = false
                            legacySourceJob = null
                        }
                    }
            },
        )
    }

    selectedLegacyMigration?.let { migration ->
        OverlayDialog(
            show = true,
            title = stringResource(R.string.pallasync_legacy_migration_report_title),
            onDismissRequest = { selectedLegacyMigration = null },
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        R.string.pallasync_legacy_migration_report_progress,
                        migration.stagedThrough,
                        migration.sourceHighWater,
                        migration.decisions,
                        migration.candidates,
                        migration.pending,
                        migration.constructed,
                        migration.quarantined,
                    ),
                )
                Text(
                    stringResource(
                        R.string.pallasync_legacy_migration_source_evidence,
                        migration.sourceProtocol,
                        migration.sourceChain,
                        migration.sourceHighWater,
                        migration.exportHash,
                    ),
                )
                Text(
                    stringResource(
                        R.string.pallasync_legacy_migration_receipts,
                        migration.accepted,
                        migration.candidates - migration.excluded,
                        migration.state,
                    ),
                )
                if (migration.state == "legacy_receipts_complete") {
                    Text(stringResource(R.string.pallasync_legacy_migration_replay_required))
                    PallaSyncActionButton(
                        text = stringResource(R.string.pallasync_legacy_migration_verify_snapshot),
                        enabled = !loadingLegacyDecisions,
                        onClick = {
                            loadingLegacyDecisions = true
                            scope.launch {
                                try {
                                    val updated = syncManager.verifyLegacyMigrationSnapshot(migration.migrationId)
                                    selectedLegacyMigration = updated
                                    legacyMigrations = legacyMigrations.map { if (it.migrationId == updated.migrationId) updated else it }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    Toast
                                        .makeText(
                                            context,
                                            R.string.pallasync_legacy_migration_report_failed,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                } finally {
                                    loadingLegacyDecisions = false
                                }
                            }
                        },
                    )
                }
                if (migration.state == "legacy_completion_audit_pending") {
                    Text(
                        stringResource(
                            R.string.pallasync_legacy_migration_audit_pending,
                            migration.completionCompactionId.orEmpty(),
                            migration.completionCoveredThroughSeq ?: 0L,
                        ),
                    )
                    PallaSyncActionButton(
                        text = stringResource(R.string.pallasync_legacy_migration_retry_audit),
                        enabled = !loadingLegacyDecisions,
                        onClick = {
                            loadingLegacyDecisions = true
                            scope.launch {
                                try {
                                    val updated = syncManager.verifyLegacyMigrationSnapshot(migration.migrationId)
                                    selectedLegacyMigration = updated
                                    legacyMigrations = legacyMigrations.map { if (it.migrationId == updated.migrationId) updated else it }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    Toast
                                        .makeText(
                                            context,
                                            R.string.pallasync_legacy_migration_report_failed,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                } finally {
                                    loadingLegacyDecisions = false
                                }
                            }
                        },
                    )
                }
                if (migration.state == "legacy_complete") {
                    Text(
                        stringResource(
                            R.string.pallasync_legacy_migration_complete,
                            migration.sourceHighWater,
                            migration.excluded,
                        ),
                    )
                }
                if (migration.sourceHighWater == 0L && migration.stagedThrough == 0L && migration.decisions == 0L) {
                    Text(stringResource(R.string.pallasync_legacy_source_not_staged))
                }
                migration.schemaCounts.forEach { (schema, count) -> Text("$schema: $count") }
                Text(stringResource(R.string.pallasync_legacy_migration_candidates_title, migration.candidates))
                PallaSyncActionButton(
                    text = stringResource(R.string.pallasync_legacy_migration_export_report),
                    onClick = {
                        pendingLegacyReport = migration
                        legacyReportExporter.launch("pallasync-migration-${migration.migrationId}.json")
                    },
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (loadingLegacyDecisions) Text(stringResource(R.string.pallasync_legacy_migration_loading))
                    legacyCandidates.forEach { candidate ->
                        Text(
                            stringResource(
                                R.string.pallasync_legacy_migration_candidate,
                                candidate.sourceSequence,
                                candidate.candidateIndex,
                                candidate.event.schema,
                                candidate.event.entityId,
                                candidate.event.operation,
                                candidate.event.body,
                            ),
                        )
                        if (candidate.requiresChoice && candidate.legacyEntityId != null) {
                            Text(stringResource(R.string.pallasync_legacy_migration_original_identifier, candidate.legacyEntityId))
                        }
                        if (candidate.requiresChoice) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PallaSyncActionButton(
                                    text =
                                        stringResource(
                                            when {
                                                candidate.excluded -> R.string.pallasync_legacy_migration_candidate_excluded
                                                candidate.selectedByUser -> R.string.pallasync_legacy_migration_candidate_selected
                                                else -> R.string.pallasync_legacy_migration_candidate_choose
                                            },
                                        ),
                                    enabled =
                                        migration.state == "legacy_candidates_staged" &&
                                            !candidate.excluded && !candidate.selectedByUser &&
                                            !loadingLegacyDecisions && migration.sourceComplete,
                                    onClick = {
                                        loadingLegacyDecisions = true
                                        scope.launch {
                                            try {
                                                syncManager.chooseLegacyMigrationCandidate(
                                                    migration.migrationId,
                                                    candidate.groupKey,
                                                    candidate.migrationEntryIndex,
                                                )
                                                val refreshed = syncManager.legacyMigrationCandidatePage(migration.migrationId, 0)
                                                legacyCandidates = refreshed.items
                                                legacyCandidateCursor = refreshed.nextIndex
                                            } catch (_: Exception) {
                                                Toast
                                                    .makeText(
                                                        context,
                                                        R.string.pallasync_legacy_migration_report_failed,
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                            } finally {
                                                loadingLegacyDecisions = false
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        DividerLine()
                    }
                    if (legacyCandidateCursor != null && !loadingLegacyDecisions) {
                        PallaSyncActionButton(
                            text = stringResource(R.string.pallasync_legacy_migration_more_candidates),
                            enabled = !loadingLegacyDecisions,
                            onClick = {
                                val cursor = legacyCandidateCursor ?: return@PallaSyncActionButton
                                loadingLegacyDecisions = true
                                scope.launch {
                                    try {
                                        val page = syncManager.legacyMigrationCandidatePage(migration.migrationId, cursor)
                                        legacyCandidates = page.items
                                        legacyCandidateCursor = page.nextIndex
                                    } catch (_: Exception) {
                                        Toast.makeText(context, R.string.pallasync_legacy_migration_report_failed, Toast.LENGTH_LONG).show()
                                    } finally {
                                        loadingLegacyDecisions = false
                                    }
                                }
                            },
                        )
                    }
                    if (migration.sourceComplete) {
                        PallaSyncActionButton(
                            text = stringResource(R.string.pallasync_legacy_migration_finalize_choices),
                            enabled = !loadingLegacyDecisions,
                            onClick = {
                                loadingLegacyDecisions = true
                                scope.launch {
                                    try {
                                        syncManager.finalizeLegacyMigrationChoices(migration.migrationId)
                                        Toast.makeText(context, R.string.pallasync_legacy_migration_choices_saved, Toast.LENGTH_LONG).show()
                                    } catch (error: Exception) {
                                        Toast
                                            .makeText(
                                                context,
                                                error.message ?: context.getString(R.string.pallasync_legacy_migration_report_failed),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                    } finally {
                                        loadingLegacyDecisions = false
                                    }
                                }
                            },
                        )
                    }
                    legacyDecisions.forEach { decision ->
                        Text(
                            stringResource(
                                R.string.pallasync_legacy_migration_decision,
                                decision.sourceSequence,
                                decision.sourceRecordId,
                                decision.disposition,
                                decision.reasonCode ?: "—",
                                decision.sourceObjectHash,
                            ),
                        )
                        DividerLine()
                    }
                    if (legacyDecisionCursor != null && !loadingLegacyDecisions) {
                        PallaSyncActionButton(
                            text = stringResource(R.string.pallasync_legacy_migration_more),
                            enabled = !loadingLegacyDecisions,
                            onClick = {
                                val cursor = legacyDecisionCursor ?: return@PallaSyncActionButton
                                loadingLegacyDecisions = true
                                scope.launch {
                                    try {
                                        val page = syncManager.legacyMigrationDecisionPage(migration.migrationId, cursor)
                                        legacyDecisions = page.items
                                        legacyDecisionCursor = page.nextIndex
                                    } catch (_: Exception) {
                                        Toast.makeText(context, R.string.pallasync_legacy_migration_report_failed, Toast.LENGTH_LONG).show()
                                    } finally {
                                        loadingLegacyDecisions = false
                                    }
                                }
                            },
                        )
                    }
                }
                PallaSyncActionButton(text = stringResource(R.string.action_cancel), onClick = { selectedLegacyMigration = null })
            }
        }
    }
}
