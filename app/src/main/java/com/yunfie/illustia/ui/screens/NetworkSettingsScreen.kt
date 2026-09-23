package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.PixivImageProxyOptions
import com.yunfie.illustia.settings.pixivNetworkModeLabel
import com.yunfie.illustia.settings.pixivNetworkModeOptions
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
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

@Composable
fun NetworkSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()

    val currentProxy = state.settings.pixivImageProxyBaseUrl
    val isCustomActive = currentProxy.isNotBlank() && PixivImageProxyOptions.none { it.baseUrl == currentProxy }
    val proxyOptions =
        remember(currentProxy) {
            val list = mutableListOf("", "custom")
            list.addAll(PixivImageProxyOptions.map { it.baseUrl })
            if (isCustomActive) {
                list.add(currentProxy)
            }
            list
        }

    var showCustomDialog by remember { mutableStateOf(false) }
    var customUrlInput by remember { mutableStateOf(currentProxy) }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.network_settings_title),
                largeTitle = stringResource(R.string.network_settings_title),
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
                Section(stringResource(R.string.image_section_proxy)) {
                    ElevatedPanel {
                        SettingDropdownRow(
                            title = stringResource(R.string.image_pixiv_network_mode),
                            summary = stringResource(R.string.image_pixiv_network_mode_desc),
                            values = pixivNetworkModeOptions(),
                            selected = state.settings.pixivNetworkMode,
                            label = { pixivNetworkModeLabel(it) },
                            onSelect = viewModel::updatePixivNetworkMode,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(R.string.image_proxy_title),
                            summary = stringResource(R.string.image_proxy_desc),
                            values = proxyOptions,
                            selected = if (isCustomActive) currentProxy else currentProxy,
                            label = { pixivImageProxyLabel(it) },
                            onSelect = { selectedValue ->
                                if (selectedValue == "custom") {
                                    customUrlInput = if (isCustomActive) currentProxy else ""
                                    showCustomDialog = true
                                } else {
                                    viewModel.updatePixivImageProxyBaseUrl(selectedValue)
                                }
                            },
                        )
                    }
                }
            }
        }

        if (showCustomDialog) {
            OverlayDialog(
                show = showCustomDialog,
                title = stringResource(R.string.image_proxy_custom_dialog_title),
                summary = stringResource(R.string.image_proxy_custom_dialog_summary),
                onDismissRequest = { showCustomDialog = false },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextField(
                        value = customUrlInput,
                        onValueChange = { customUrlInput = it },
                        label = stringResource(R.string.field_url),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { showCustomDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = overlayActionButtonColors(),
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Button(
                            onClick = {
                                viewModel.updatePixivImageProxyBaseUrl(customUrlInput)
                                showCustomDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = overlayActionButtonColors(),
                        ) {
                            Text(stringResource(R.string.action_add), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun pixivImageProxyLabel(value: String): String =
    when {
        value.isBlank() -> stringResource(R.string.image_proxy_none)
        value == "custom" -> stringResource(R.string.image_proxy_custom)
        else -> PixivImageProxyOptions.firstOrNull { it.baseUrl == value }?.name ?: value
    }
