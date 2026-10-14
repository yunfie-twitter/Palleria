package com.yunfie.illustia.ui.screens.pallasync

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<PallaSyncDeviceEntity>>(emptyList()) }
    var peerOffer by remember { mutableStateOf<String?>(null) }
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

    LaunchedEffect(state.settings.pallaSyncEnabled) {
        if (!state.settings.pallaSyncEnabled) return@LaunchedEffect

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
                TextButton(text = stringResource(R.string.action_cancel), onClick = { peerOffer = null })
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
                                            peerOffer = transcript.offer.toString()
                                        } catch (failure: IllegalStateException) {
                                            offerError = failure.message?.takeIf { it.startsWith("C_") } ?: "C_BOOTSTRAP_TRUST_REQUIRED"
                                        } finally {
                                            creatingOffer = false
                                        }
                                    }
                                }
                            },
                            enabled = state.settings.pallaSyncEnabled && !creatingOffer,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (creatingOffer) R.string.pallasync_peer_offer_creating else R.string.pallasync_peer_offer_create,
                                ),
                            )
                        }
                        offerError?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }
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
