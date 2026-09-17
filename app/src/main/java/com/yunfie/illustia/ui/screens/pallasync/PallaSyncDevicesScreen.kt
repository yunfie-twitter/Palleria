package com.yunfie.illustia.ui.screens.pallasync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.PallaSyncDeviceEntity
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PallaSyncDevicesScreen(
    state: IllustiaUiState,
    onBack: () -> Unit,
    onDeviceClick: (String, String) -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val context = LocalContext.current
    val syncManager = remember { PalleriaSyncManager(context = context) }
    val scrollBehavior = MiuixScrollBehavior()
    var devices by remember { mutableStateOf<List<PallaSyncDeviceEntity>>(emptyList()) }

    LaunchedEffect(state.settings.pallaSyncEnabled) {
        if (!state.settings.pallaSyncEnabled) return@LaunchedEffect

        val dao = PallaSyncDatabase.getDatabase(context).pallaSyncDao()
        devices = withContext(Dispatchers.IO) { dao.getAllDevices() }

        val chainState = withContext(Dispatchers.IO) { dao.getAllChainStates() }.firstOrNull()
        if (chainState != null) {
            syncManager.fetchDevices(syncManager.getServerUrl(), chainState.chainId)
            devices = withContext(Dispatchers.IO) { dao.getAllDevices() }
        }

        while (isActive) {
            delay(2_000)
            devices = withContext(Dispatchers.IO) { dao.getAllDevices() }
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
