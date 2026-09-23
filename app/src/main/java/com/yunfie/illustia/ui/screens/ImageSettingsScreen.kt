package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ImageSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onOpenWallpaperPlaylistSettings: () -> Unit = { viewModel.openWallpaperPlaylistSettings() },
    onOpenCardCustomizationSettings: () -> Unit = { viewModel.openCardCustomizationSettings() },
    onOpenDetailSectionSettings: () -> Unit = { viewModel.openDetailSectionSettings() },
    onOpenDownloadSettings: () -> Unit = { viewModel.openDownloadSettings() },
    onOpenNetworkSettings: () -> Unit = { viewModel.openNetworkSettings() },
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val isDesktop =
        remember(context) {
            com.yunfie.illustia.platform.DesktopEnvironment
                .isDesktop(context)
        }

    val cardCustomizationFlag = state.settings.isFeatureEnabled(FeatureFlag.CardCustomization)

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

            item {
                Section(stringResource(R.string.image_section_layout)) {
                    ElevatedPanel {
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
                Section(stringResource(R.string.image_section_customization)) {
                    ElevatedPanel {
                        var hasItemAbove = false
                        if (cardCustomizationFlag) {
                            SettingLinkRow(
                                title = stringResource(R.string.card_customization_settings_title),
                                summary = stringResource(R.string.card_customization_settings_summary),
                                onClick = onOpenCardCustomizationSettings,
                            )
                            hasItemAbove = true
                        }
                        if (hasItemAbove) DividerLine()
                        SettingLinkRow(
                            title = stringResource(R.string.detail_section_settings_title),
                            summary = stringResource(R.string.detail_section_settings_summary),
                            onClick = onOpenDetailSectionSettings,
                        )
                        if (!isDesktop) {
                            DividerLine()
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
                Section(stringResource(R.string.download_settings_title)) {
                    ElevatedPanel {
                        SettingLinkRow(
                            title = stringResource(R.string.download_settings_title),
                            summary = stringResource(R.string.download_settings_summary),
                            onClick = onOpenDownloadSettings,
                        )
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
                Section(stringResource(R.string.network_settings_title)) {
                    ElevatedPanel {
                        SettingLinkRow(
                            title = stringResource(R.string.network_settings_title),
                            summary = stringResource(R.string.network_settings_summary),
                            onClick = onOpenNetworkSettings,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun qualityLabel(value: String): String =
    when (value) {
        "high" -> stringResource(R.string.image_quality_high)
        "medium" -> stringResource(R.string.image_quality_medium)
        else -> stringResource(R.string.image_quality_low)
    }
