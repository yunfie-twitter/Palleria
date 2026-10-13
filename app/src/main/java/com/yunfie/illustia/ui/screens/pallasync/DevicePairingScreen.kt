package com.yunfie.illustia.ui.screens.pallasync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** §§5.5/10.4/11.6: root enrollment has explicit trust and transient protected input. */
@Composable
fun DevicePairingScreen(
    serverUrl: String,
    onBack: () -> Unit,
    onPairSuccess: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val context = LocalContext.current
    val manager = remember { PalleriaSyncManager(context = context) }
    var showTrust by remember { mutableStateOf(false) }
    var showRoot by remember { mutableStateOf(false) }
    var joined by remember { mutableStateOf(false) }
    if (showTrust) {
        PallaSyncTrustDialog(
            origin = serverUrl,
            sourceKind = "relay_full_replay",
            sourceLabel = stringResource(R.string.pallasync_v3_relay_replay_source),
            onDismiss = { showTrust = false },
            onAccepted = {
                showTrust = false
                joined = false
                showRoot = true
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
            manager.joinChain(words, serverUrl = serverUrl, passphrase = passphrase, cancellation = cancellation).also { joined = it }
        }
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
            Button(onClick = { showTrust = true }, enabled = !showTrust && !showRoot, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pallasync_v3_root_enroll))
            }
        }
    }
}
