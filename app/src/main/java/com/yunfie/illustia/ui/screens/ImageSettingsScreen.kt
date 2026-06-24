package com.yunfie.illustia.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.PixivImageProxyOptions
import com.yunfie.illustia.nativebridge.NativeImageStore
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ImageSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val imageStore = remember(context) { NativeImageStore(context.applicationContext) }
    var saveLocation by remember(imageStore) { mutableStateOf(imageStore.currentPathLabel()) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            imageStore.persistTreeUri(it)
            saveLocation = imageStore.currentPathLabel()
        }
    }
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.image_settings_title),
                largeTitle = stringResource(R.string.image_settings_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .background(MiuixTheme.colorScheme.surface),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = scaffoldPadding.calculateTopPadding() + 16.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Section(stringResource(R.string.image_section_quality)) {
                ElevatedPanel {
                    SettingSwitchRow(stringResource(R.string.image_high_quality), state.settings.highQualityImages, viewModel::updateHighQuality, stringResource(R.string.image_high_quality_desc))
                    DividerLine()
                    SettingSwitchRow(stringResource(R.string.image_prefetch), state.settings.prefetchImages, viewModel::updatePrefetchImages, stringResource(R.string.image_prefetch_desc))
                    DividerLine()
                    SettingDropdownRow(
                        title = stringResource(R.string.image_preview_quality),
                        values = listOf("low", "medium", "high"),
                        selected = state.settings.feedPreviewQuality,
                        label = { qualityLabel(it) },
                        onSelect = viewModel::updateFeedPreviewQuality,
                    )
                    DividerLine()
                    SettingDropdownRow(
                        title = stringResource(R.string.image_detail_quality),
                        values = listOf("low", "medium", "high"),
                        selected = state.settings.illustDetailQuality,
                        label = { qualityLabel(it) },
                        onSelect = viewModel::updateIllustDetailQuality,
                    )
                    DividerLine()
                    SettingDropdownRow(
                        title = stringResource(R.string.image_fullscreen_quality),
                        values = listOf("low", "medium", "high"),
                        selected = state.settings.fullscreenQuality,
                        label = { qualityLabel(it) },
                        onSelect = viewModel::updateFullscreenQuality,
                    )
                }
            }}

            item { Section(stringResource(R.string.image_section_layout)) {
                ElevatedPanel {
                    SettingSwitchRow(
                        title = stringResource(R.string.image_viewer_thumbnails_in_toolbar),
                        checked = state.settings.viewerThumbnailsInToolbar,
                        onCheckedChange = viewModel::updateViewerThumbnailsInToolbar,
                        summary = stringResource(R.string.image_viewer_thumbnails_in_toolbar_desc),
                    )
                    DividerLine()
                    SettingDropdownRow(
                        title = stringResource(R.string.image_simultaneous_downloads),
                        values = listOf(1, 2, 3, 4),
                        selected = state.settings.simultaneousDownloads.coerceIn(1, 4),
                        label = { stringResource(R.string.data_items_count, it) },
                        onSelect = viewModel::updateSimultaneousDownloads,
                    )
                    DividerLine()
                    SettingSwitchRow(
                        title = stringResource(R.string.offline_wifi_only),
                        checked = state.settings.offlineWifiOnly,
                        onCheckedChange = viewModel::updateOfflineWifiOnly,
                        summary = stringResource(R.string.offline_wifi_only_desc),
                    )
                    DividerLine()
                    SettingDropdownRow(
                        title = stringResource(R.string.offline_capacity_limit),
                        values = listOf(1024L * 1024 * 1024, 3L * 1024 * 1024 * 1024, 5L * 1024 * 1024 * 1024, 10L * 1024 * 1024 * 1024),
                        selected = state.settings.offlineStorageLimitBytes,
                        label = { limitLabel(it) },
                        onSelect = viewModel::updateOfflineStorageLimitBytes,
                    )
                    DividerLine()
                    SettingDropdownRow(
                        title = stringResource(R.string.image_columns),
                        values = listOf(2, 3, 4),
                        selected = state.settings.verticalColumnCount.coerceIn(2, 4),
                        label = { stringResource(R.string.data_columns_count, it) },
                        onSelect = viewModel::updateVerticalColumnCount,
                    )
                    DividerLine()
                    SettingDropdownRow(
                        title = stringResource(R.string.image_columns_landscape),
                        values = listOf(3, 4, 5, 6),
                        selected = state.settings.horizontalColumnCount.coerceIn(3, 6),
                        label = { stringResource(R.string.data_columns_count, it) },
                        onSelect = viewModel::updateHorizontalColumnCount,
                    )
                }
            }}

            item { Section(stringResource(R.string.image_section_storage)) {
                ElevatedPanel {
                    ArrowPreference(
                        title = stringResource(R.string.image_default_save_location),
                        summary = saveLocation.ifBlank { stringResource(R.string.image_default_save_location_fallback) },
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { folderPicker.launch(null) },
                    )
                }
            }}

            item { Section(stringResource(R.string.image_section_proxy)) {
                ElevatedPanel {
                    val currentProxy = state.settings.pixivImageProxyBaseUrl
                    val isCustomActive = currentProxy.isNotBlank() && PixivImageProxyOptions.none { it.baseUrl == currentProxy }
                    val proxyOptions = remember(currentProxy) {
                        val list = mutableListOf("", "custom")
                        list.addAll(PixivImageProxyOptions.map { it.baseUrl })
                        if (isCustomActive) {
                            list.add(currentProxy)
                        }
                        list
                    }

                    var showCustomDialog by remember { mutableStateOf(false) }
                    var customUrlInput by remember { mutableStateOf(currentProxy) }

                    SettingDropdownRow(
                        title = stringResource(R.string.image_proxy_title),
                        summary = stringResource(R.string.image_proxy_desc),
                        values = proxyOptions,
                        selected = if (isCustomActive) currentProxy else currentProxy, // just to trigger recomposition if needed
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

                    if (showCustomDialog) {
                        OverlayDialog(
                            show = showCustomDialog,
                            title = stringResource(R.string.image_proxy_custom_dialog_title),
                            summary = stringResource(R.string.image_proxy_custom_dialog_summary),
                            onDismissRequest = { showCustomDialog = false }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                TextField(
                                    value = customUrlInput,
                                    onValueChange = { customUrlInput = it },
                                    label = "URL",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = { showCustomDialog = false },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.action_cancel))
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.updatePixivImageProxyBaseUrl(customUrlInput)
                                            showCustomDialog = false
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.action_add), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }}
        }
    }
}

@Composable
private fun qualityLabel(value: String): String {
    return when (value) {
        "high" -> stringResource(R.string.image_quality_high)
        "medium" -> stringResource(R.string.image_quality_medium)
        else -> stringResource(R.string.image_quality_low)
    }
}

@Composable
private fun pixivImageProxyLabel(value: String): String {
    if (value.isBlank()) return stringResource(R.string.image_proxy_none)
    if (value == "custom") return stringResource(R.string.image_proxy_custom)
    return PixivImageProxyOptions.firstOrNull { it.baseUrl == value }?.name ?: value
}

@Composable
private fun limitLabel(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return String.format(java.util.Locale.US, "%.0f GB", gb)
}
