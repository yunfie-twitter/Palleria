package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.PixivImageProxyOptions
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.action_add
import com.yunfie.illustia.resources.action_cancel
import com.yunfie.illustia.resources.field_url
import com.yunfie.illustia.resources.image_pixiv_network_mode
import com.yunfie.illustia.resources.image_pixiv_network_mode_desc
import com.yunfie.illustia.resources.image_proxy_custom
import com.yunfie.illustia.resources.image_proxy_custom_dialog_summary
import com.yunfie.illustia.resources.image_proxy_custom_dialog_title
import com.yunfie.illustia.resources.image_proxy_desc
import com.yunfie.illustia.resources.image_proxy_none
import com.yunfie.illustia.resources.image_proxy_title
import com.yunfie.illustia.resources.image_section_proxy
import com.yunfie.illustia.resources.internal_proxy_entry_desc
import com.yunfie.illustia.resources.internal_proxy_entry_title
import com.yunfie.illustia.resources.network_settings_title
import com.yunfie.illustia.resources.setup_proxy_bypass_label
import com.yunfie.illustia.resources.setup_proxy_official_label
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.pixivNetworkModeLabel
import com.yunfie.illustia.settings.pixivNetworkModeOptions
import com.yunfie.illustia.ui.components.AppDialog
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.dialogBottomInsets
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NetworkSettingsScreen(
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onOpenInternalProxySettings: () -> Unit = { viewModel.openInternalProxySettings() },
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()

    val currentProxy = settings.pixivImageProxyBaseUrl
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
                title = stringResource(Res.string.network_settings_title),
                largeTitle = stringResource(Res.string.network_settings_title),
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
                Section(stringResource(Res.string.image_section_proxy)) {
                    ElevatedPanel {
                        SettingDropdownRow(
                            title = stringResource(Res.string.image_pixiv_network_mode),
                            summary = stringResource(Res.string.image_pixiv_network_mode_desc),
                            values = pixivNetworkModeOptions(),
                            selected = settings.pixivNetworkMode,
                            label = { pixivNetworkModeLabel(it) },
                            onSelect = viewModel::updatePixivNetworkMode,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(Res.string.image_proxy_title),
                            summary = stringResource(Res.string.image_proxy_desc),
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
                        DividerLine()
                        SettingLinkRow(
                            title = stringResource(Res.string.internal_proxy_entry_title),
                            summary = stringResource(Res.string.internal_proxy_entry_desc),
                            onClick = onOpenInternalProxySettings,
                        )
                    }
                }
            }
        }

        if (showCustomDialog) {
            AppDialog(
                show = showCustomDialog,
                title = stringResource(Res.string.image_proxy_custom_dialog_title),
                summary = stringResource(Res.string.image_proxy_custom_dialog_summary),
                onDismissRequest = { showCustomDialog = false },
                // 既定では imePadding と navigationBarsPadding の両方が適用される。ルートが
                // navigationBars 分を消費済みのため、キーボード表示時に両方が積まれて
                // キーボードとの間に隙間ができる。内側で大きい方だけを確保する。
                defaultWindowInsetsPadding = false,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(dialogBottomInsets()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TextField(
                        value = customUrlInput,
                        onValueChange = { customUrlInput = it },
                        label = stringResource(Res.string.field_url),
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
                            Text(stringResource(Res.string.action_cancel))
                        }
                        Button(
                            onClick = {
                                viewModel.updatePixivImageProxyBaseUrl(customUrlInput)
                                showCustomDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = overlayActionButtonColors(),
                        ) {
                            Text(stringResource(Res.string.action_add), fontWeight = FontWeight.Bold)
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
        value.isBlank() -> {
            stringResource(Res.string.image_proxy_none) + " (" + stringResource(Res.string.setup_proxy_official_label) + ")"
        }

        value == "custom" -> {
            stringResource(Res.string.image_proxy_custom)
        }

        value.contains("pixiv.cat") -> {
            val name = PixivImageProxyOptions.firstOrNull { it.baseUrl == value }?.name ?: value
            name + " (" + stringResource(Res.string.setup_proxy_bypass_label) + ")"
        }

        else -> {
            PixivImageProxyOptions.firstOrNull { it.baseUrl == value }?.name ?: value
        }
    }
