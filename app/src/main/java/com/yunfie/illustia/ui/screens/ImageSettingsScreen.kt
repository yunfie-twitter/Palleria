@file:Suppress("TooManyFunctions")

package com.yunfie.illustia.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.rotate
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
import com.yunfie.illustia.settings.DEFAULT_DETAIL_SECTION_ORDER
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.settings.pixivNetworkModeLabel
import com.yunfie.illustia.settings.pixivNetworkModeOptions
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ImageSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onOpenWallpaperPlaylistSettings: () -> Unit = { viewModel.openWallpaperPlaylistSettings() },
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val imageStore = remember(context) { NativeImageStore(context.applicationContext) }
    var saveLocation by remember(imageStore) { mutableStateOf(imageStore.currentPathLabel()) }
    val isDesktop =
        remember(context) {
            com.yunfie.illustia.platform.DesktopEnvironment
                .isDesktop(context)
        }
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                imageStore.persistTreeUri(it)
                saveLocation = imageStore.currentPathLabel()
            }
        }

    val cardCustomizationFlag = state.settings.isFeatureEnabled(FeatureFlag.CardCustomization)
    val detailSectionOrderFlag = state.settings.isFeatureEnabled(FeatureFlag.DetailSectionOrder)

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
                Section(stringResource(R.string.image_section_quality)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            stringResource(R.string.image_high_quality),
                            state.settings.highQualityImages,
                            viewModel::updateHighQuality,
                            stringResource(R.string.image_high_quality_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            stringResource(R.string.image_prefetch),
                            state.settings.prefetchImages,
                            viewModel::updatePrefetchImages,
                            stringResource(R.string.image_prefetch_desc),
                        )
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
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(R.string.image_manga_reader_mode),
                            summary = stringResource(R.string.image_manga_reader_mode_desc),
                            values = listOf("paged", "vertical"),
                            selected = state.settings.mangaReaderMode,
                            label = {
                                if (it == "vertical") {
                                    stringResource(R.string.viewer_comic_mode)
                                } else {
                                    stringResource(R.string.viewer_page_mode)
                                }
                            },
                            onSelect = viewModel::updateMangaReaderMode,
                        )
                    }
                }
            }

            if (cardCustomizationFlag) {
                item {
                    Section(stringResource(R.string.experimental_card_section)) {
                        ElevatedPanel {
                            SettingSwitchRow(
                                title = stringResource(R.string.experimental_card_title),
                                checked = state.settings.showCardTitle,
                                onCheckedChange = viewModel::updateShowCardTitle,
                            )
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(R.string.experimental_card_artist),
                                checked = state.settings.showCardArtist,
                                onCheckedChange = viewModel::updateShowCardArtist,
                            )
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(R.string.experimental_card_tags),
                                checked = state.settings.showCardTags,
                                onCheckedChange = viewModel::updateShowCardTags,
                            )
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(R.string.experimental_card_rating),
                                checked = state.settings.showCardBookmarkCount,
                                onCheckedChange = viewModel::updateShowCardBookmarkCount,
                                summary = stringResource(R.string.experimental_card_rating_desc),
                            )
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(R.string.experimental_card_ai),
                                checked = state.settings.showAiBadge,
                                onCheckedChange = viewModel::updateShowAiBadge,
                            )
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(R.string.experimental_card_r18),
                                checked = state.settings.showR18Badge,
                                onCheckedChange = viewModel::updateShowR18Badge,
                            )
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(R.string.experimental_card_bookmark_button),
                                checked = state.settings.showCardBookmarkButton,
                                onCheckedChange = viewModel::updateShowCardBookmarkButton,
                            )
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(R.string.experimental_card_double_tap_to_bookmark),
                                checked = state.settings.doubleTapToBookmark,
                                onCheckedChange = viewModel::updateDoubleTapToBookmark,
                                summary = stringResource(R.string.experimental_card_double_tap_to_bookmark_desc),
                            )
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(R.string.experimental_card_related_r18),
                                checked = state.settings.showRelatedR18,
                                onCheckedChange = viewModel::updateShowRelatedR18,
                                summary = stringResource(R.string.experimental_card_related_r18_desc),
                                enabled = state.settings.showR18Badge,
                            )
                        }
                    }
                }
            }

            if (detailSectionOrderFlag) {
                item {
                    val orderedSections = normalizeDetailOrder(state.settings.detailSectionOrder)
                    Section(stringResource(R.string.experimental_detail_section)) {
                        ElevatedPanel {
                            orderedSections.forEachIndexed { index, id ->
                                OrderEditorRow(
                                    title = detailSectionLabel(id),
                                    canMoveUp = index > 0,
                                    canMoveDown = index < orderedSections.lastIndex,
                                    onMoveUp = {
                                        viewModel.updateDetailSectionOrder(orderedSections.moved(index, index - 1))
                                    },
                                    onMoveDown = {
                                        viewModel.updateDetailSectionOrder(orderedSections.moved(index, index + 1))
                                    },
                                )
                                if (index < orderedSections.lastIndex) DividerLine()
                            }
                        }
                    }
                }
            }

            item {
                Section(stringResource(R.string.image_section_cache)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(R.string.image_smart_cache),
                            checked = state.settings.smartCacheEnabled,
                            onCheckedChange = viewModel::updateSmartCacheEnabled,
                            summary = stringResource(R.string.image_smart_cache_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(R.string.image_smart_cache_wifi),
                            checked = state.settings.smartCacheWifiOnly,
                            onCheckedChange = viewModel::updateSmartCacheWifiOnly,
                            summary = stringResource(R.string.image_smart_cache_wifi_desc),
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(R.string.image_smart_cache_count),
                            values = listOf(6, 12, 20, 30),
                            selected = state.settings.smartCacheItemCount,
                            label = { stringResource(R.string.data_items_count, it) },
                            onSelect = viewModel::updateSmartCacheItemCount,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(R.string.image_cache_capacity),
                            summary = stringResource(R.string.image_cache_capacity_desc),
                            values = listOf(100, 300, 500, 1000),
                            selected = state.settings.imageCacheSizeMb,
                            label = { "$it MB" },
                            onSelect = viewModel::updateImageCacheSizeMb,
                        )
                    }
                }
            }

            item {
                Section(stringResource(R.string.image_section_layout)) {
                    ElevatedPanel {
                        SettingDropdownRow(
                            title = stringResource(R.string.image_simultaneous_downloads),
                            values = listOf(1, 2, 3, 4),
                            selected = state.settings.simultaneousDownloads.coerceIn(1, 4),
                            label = { stringResource(R.string.data_items_count, it) },
                            onSelect = viewModel::updateSimultaneousDownloads,
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
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(R.string.image_related_columns),
                            values = listOf(0, 2, 3, 4, 5),
                            selected = if (state.settings.relatedIllustColumnCount in 2..5) state.settings.relatedIllustColumnCount else 0,
                            label = {
                                if (it == 0) {
                                    stringResource(R.string.data_columns_dynamic)
                                } else {
                                    stringResource(R.string.data_columns_count, it)
                                }
                            },
                            onSelect = viewModel::updateRelatedIllustColumnCount,
                        )
                    }
                }
            }

            item {
                Section(stringResource(R.string.image_section_storage)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(R.string.image_download_folder_by_artist),
                            checked = state.settings.downloadFolderByArtist,
                            onCheckedChange = viewModel::updateDownloadFolderByArtist,
                            summary = stringResource(R.string.image_download_folder_by_artist_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(R.string.image_download_folder_by_work),
                            checked = state.settings.downloadFolderByWork,
                            onCheckedChange = viewModel::updateDownloadFolderByWork,
                            summary = stringResource(R.string.image_download_folder_by_work_desc),
                        )
                        DividerLine()
                        ArrowPreference(
                            title = stringResource(R.string.image_default_save_location),
                            summary = saveLocation.ifBlank { stringResource(R.string.image_default_save_location_fallback) },
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { folderPicker.launch(null) },
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(R.string.image_duplicate_save_mode),
                            summary = stringResource(R.string.image_duplicate_save_mode_desc),
                            values = listOf("skip", "overwrite", "always"),
                            selected = state.settings.duplicateSaveMode,
                            label = { duplicateSaveModeLabel(it) },
                            onSelect = viewModel::updateDuplicateSaveMode,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(R.string.image_ugoira_save_format),
                            summary = stringResource(R.string.image_ugoira_save_format_desc),
                            values = listOf("mp4", "gif"),
                            selected = state.settings.ugoiraSaveFormat,
                            label = { ugoiraSaveFormatLabel(it) },
                            onSelect = viewModel::updateUgoiraSaveFormat,
                        )
                    }
                }
            }

            if (!isDesktop) {
                item {
                    Section(stringResource(R.string.wallpaper_playlist)) {
                        ElevatedPanel {
                            SettingLinkRow(
                                title = stringResource(R.string.wallpaper_playlist),
                                summary = stringResource(R.string.wallpaper_playlist_desc),
                                onClick = onOpenWallpaperPlaylistSettings,
                            )
                        }
                    }
                }
            }

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
            }
        }
    }
}

@Composable
private fun OrderEditorRow(
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    BasicComponent(
        title = title,
        modifier = Modifier.fillMaxWidth(),
        endActions = { MoveButtons(canMoveUp, canMoveDown, onMoveUp, onMoveDown) },
    )
}

@Composable
private fun MoveButtons(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = stringResource(R.string.action_move_up),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.rotate(-90f),
        )
    }
    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = stringResource(R.string.action_move_down),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.rotate(90f),
        )
    }
}

private fun normalizeDetailOrder(order: List<String>): List<String> =
    order.filter { it in DEFAULT_DETAIL_SECTION_ORDER }.distinct() +
        DEFAULT_DETAIL_SECTION_ORDER.filterNot { it in order }

private fun <T> List<T>.moved(
    from: Int,
    to: Int,
): List<T> =
    toMutableList().apply {
        add(to, removeAt(from))
    }

@Composable
private fun detailSectionLabel(id: String): String =
    when (id) {
        "tags" -> stringResource(R.string.experimental_detail_tags)
        "description" -> stringResource(R.string.experimental_detail_description)
        "related" -> stringResource(R.string.experimental_detail_related)
        else -> stringResource(R.string.experimental_detail_artist)
    }

@Composable
private fun qualityLabel(value: String): String =
    when (value) {
        "high" -> stringResource(R.string.image_quality_high)
        "medium" -> stringResource(R.string.image_quality_medium)
        else -> stringResource(R.string.image_quality_low)
    }

@Composable
private fun pixivImageProxyLabel(value: String): String {
    if (value.isBlank()) return stringResource(R.string.image_proxy_none)
    if (value == "custom") return stringResource(R.string.image_proxy_custom)
    return PixivImageProxyOptions.firstOrNull { it.baseUrl == value }?.name ?: value
}

@Composable
private fun duplicateSaveModeLabel(value: String): String =
    when (value) {
        "overwrite" -> stringResource(R.string.image_duplicate_mode_overwrite)
        "always" -> stringResource(R.string.image_duplicate_mode_always)
        else -> stringResource(R.string.image_duplicate_mode_skip)
    }

@Composable
private fun ugoiraSaveFormatLabel(value: String): String =
    when (value) {
        "gif" -> stringResource(R.string.image_ugoira_format_gif)
        else -> stringResource(R.string.image_ugoira_format_mp4)
    }
