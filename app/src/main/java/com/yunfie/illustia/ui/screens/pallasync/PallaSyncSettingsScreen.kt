package com.yunfie.illustia.ui.screens.pallasync

import android.widget.Toast
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
import androidx.compose.runtime.collectAsState
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
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

@Composable
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
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var isCreatingChain by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<com.yunfie.illustia.pallasync.data.PallaSyncDeviceEntity>>(emptyList()) }

    LaunchedEffect(state.settings.pallaSyncEnabled) {
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
                val chainState = dao.getAllChainStates().firstOrNull()
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
                scope.launch {
                    if (syncManager.deleteChain()) {
                        viewModel.updatePallaSyncEnabled(false)
                        Toast.makeText(context, R.string.msg_pallasync_chain_deleted, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, R.string.error_pallasync_delete_chain_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showDeleteConfirm = false },
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
                                        isCreatingChain = true
                                        val seedPhrase =
                                            try {
                                                syncManager.initializeGenesis(
                                                    state.settings.pallaSyncServerUrl,
                                                )
                                            } finally {
                                                isCreatingChain = false
                                            }
                                        if (seedPhrase.isBlank()) {
                                            Toast.makeText(context, R.string.error_pallasync_create_chain_failed, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else if (!enabled && state.settings.pallaSyncEnabled) {
                                    showLeaveConfirm = true
                                }
                            },
                            enabled = !isCreatingChain,
                            summary = stringResource(R.string.pallasync_enable_desc),
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
                        DividerLine()
                        SettingLinkRow(
                            title = stringResource(R.string.pallasync_pair_device),
                            summary = stringResource(R.string.pallasync_pair_device_desc),
                            onClick = onPairDevice,
                        )
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

                item {
                    val logs by PalleriaSyncManager.syncLogs.collectAsState()
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
}
