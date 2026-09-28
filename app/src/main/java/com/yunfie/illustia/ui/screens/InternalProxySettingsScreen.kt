package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DOH_PROVIDERS = listOf("system", "cloudflare", "google", "quad9", "adguard", "custom")
private val PROXY_TYPES = listOf("HTTP", "SOCKS")

@Composable
fun InternalProxySettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()

    var showCustomDohDialog by remember { mutableStateOf(false) }
    var customDohInput by remember { mutableStateOf(state.settings.dohCustomUrl) }

    var showProxyHostDialog by remember { mutableStateOf(false) }
    var proxyHostInput by remember { mutableStateOf(state.settings.internalProxyHost) }

    var showProxyPortDialog by remember { mutableStateOf(false) }
    var proxyPortInput by remember { mutableStateOf(state.settings.internalProxyPort.toString()) }

    var showBypassHostsDialog by remember { mutableStateOf(false) }
    var bypassHostsInput by remember { mutableStateOf(state.settings.internalProxyBypassHosts) }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.internal_proxy_settings_title),
                largeTitle = stringResource(R.string.internal_proxy_settings_title),
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
                Section(stringResource(R.string.internal_proxy_section_doh)) {
                    ElevatedPanel {
                        SettingDropdownRow(
                            title = stringResource(R.string.internal_proxy_doh_provider),
                            summary = stringResource(R.string.internal_proxy_doh_provider_desc),
                            values = DOH_PROVIDERS,
                            selected = state.settings.dohProvider,
                            label = { dohProviderLabel(it) },
                            onSelect = { selected ->
                                viewModel.updateDohProvider(selected)
                                if (selected == "custom") {
                                    customDohInput = state.settings.dohCustomUrl
                                    showCustomDohDialog = true
                                }
                            },
                        )
                        if (state.settings.dohProvider == "custom") {
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.internal_proxy_doh_custom_url),
                                summary =
                                    state.settings.dohCustomUrl.ifBlank {
                                        stringResource(
                                            R.string.internal_proxy_doh_custom_url_hint,
                                        )
                                    },
                                onClick = {
                                    customDohInput = state.settings.dohCustomUrl
                                    showCustomDohDialog = true
                                },
                            )
                        }
                    }
                }
            }

            item {
                Section(stringResource(R.string.internal_proxy_section_proxy)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(R.string.internal_proxy_enable),
                            summary = stringResource(R.string.internal_proxy_enable_desc),
                            checked = state.settings.internalProxyEnabled,
                            onCheckedChange = viewModel::updateInternalProxyEnabled,
                        )
                        if (state.settings.internalProxyEnabled) {
                            DividerLine()
                            SettingDropdownRow(
                                title = stringResource(R.string.internal_proxy_type),
                                values = PROXY_TYPES,
                                selected = state.settings.internalProxyType,
                                label = { it },
                                onSelect = viewModel::updateInternalProxyType,
                            )
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.internal_proxy_host),
                                summary = state.settings.internalProxyHost.ifBlank { "127.0.0.1" },
                                onClick = {
                                    proxyHostInput = state.settings.internalProxyHost
                                    showProxyHostDialog = true
                                },
                            )
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.internal_proxy_port),
                                summary = state.settings.internalProxyPort.toString(),
                                onClick = {
                                    proxyPortInput = state.settings.internalProxyPort.toString()
                                    showProxyPortDialog = true
                                },
                            )
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(R.string.internal_proxy_bypass_hosts),
                                summary = state.settings.internalProxyBypassHosts.ifBlank { "localhost, 127.0.0.1" },
                                onClick = {
                                    bypassHostsInput = state.settings.internalProxyBypassHosts
                                    showBypassHostsDialog = true
                                },
                            )
                        }
                    }
                }
            }
        }

        if (showCustomDohDialog) {
            OverlayDialog(
                show = showCustomDohDialog,
                title = stringResource(R.string.internal_proxy_doh_custom_url),
                onDismissRequest = { showCustomDohDialog = false },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextField(
                        value = customDohInput,
                        onValueChange = { customDohInput = it },
                        label = stringResource(R.string.field_url),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { showCustomDohDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = overlayActionButtonColors(),
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Button(
                            onClick = {
                                viewModel.updateDohCustomUrl(customDohInput.trim())
                                showCustomDohDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = overlayActionButtonColors(),
                        ) {
                            Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showProxyHostDialog) {
            OverlayDialog(
                show = showProxyHostDialog,
                title = stringResource(R.string.internal_proxy_host),
                onDismissRequest = { showProxyHostDialog = false },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextField(
                        value = proxyHostInput,
                        onValueChange = { proxyHostInput = it },
                        label = stringResource(R.string.internal_proxy_host),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { showProxyHostDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = overlayActionButtonColors(),
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Button(
                            onClick = {
                                viewModel.updateInternalProxyHost(proxyHostInput.trim())
                                showProxyHostDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = overlayActionButtonColors(),
                        ) {
                            Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showProxyPortDialog) {
            OverlayDialog(
                show = showProxyPortDialog,
                title = stringResource(R.string.internal_proxy_port),
                onDismissRequest = { showProxyPortDialog = false },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextField(
                        value = proxyPortInput,
                        onValueChange = { proxyPortInput = it.filter { char -> char.isDigit() } },
                        label = stringResource(R.string.internal_proxy_port),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { showProxyPortDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = overlayActionButtonColors(),
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Button(
                            onClick = {
                                val port = proxyPortInput.toIntOrNull()?.coerceIn(1, 65535) ?: 8080
                                viewModel.updateInternalProxyPort(port)
                                showProxyPortDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = overlayActionButtonColors(),
                        ) {
                            Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showBypassHostsDialog) {
            OverlayDialog(
                show = showBypassHostsDialog,
                title = stringResource(R.string.internal_proxy_bypass_hosts),
                summary = stringResource(R.string.internal_proxy_bypass_hosts_desc),
                onDismissRequest = { showBypassHostsDialog = false },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextField(
                        value = bypassHostsInput,
                        onValueChange = { bypassHostsInput = it },
                        label = stringResource(R.string.internal_proxy_bypass_hosts),
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { showBypassHostsDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = overlayActionButtonColors(),
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Button(
                            onClick = {
                                viewModel.updateInternalProxyBypassHosts(bypassHostsInput.trim())
                                showBypassHostsDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = overlayActionButtonColors(),
                        ) {
                            Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun dohProviderLabel(value: String): String =
    when (value) {
        "system" -> stringResource(R.string.internal_proxy_doh_system)
        "cloudflare" -> stringResource(R.string.internal_proxy_doh_cloudflare)
        "google" -> stringResource(R.string.internal_proxy_doh_google)
        "quad9" -> stringResource(R.string.internal_proxy_doh_quad9)
        "adguard" -> stringResource(R.string.internal_proxy_doh_adguard)
        "custom" -> stringResource(R.string.internal_proxy_doh_custom)
        else -> value
    }
