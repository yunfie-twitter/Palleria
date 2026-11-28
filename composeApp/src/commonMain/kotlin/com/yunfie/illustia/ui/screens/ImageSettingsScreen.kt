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
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.ambient_light_viewer_desc
import com.yunfie.illustia.resources.ambient_light_viewer_title
import com.yunfie.illustia.resources.card_customization_settings_summary
import com.yunfie.illustia.resources.card_customization_settings_title
import com.yunfie.illustia.resources.data_columns_count
import com.yunfie.illustia.resources.data_columns_dynamic
import com.yunfie.illustia.resources.data_items_count
import com.yunfie.illustia.resources.detail_section_settings_summary
import com.yunfie.illustia.resources.detail_section_settings_title
import com.yunfie.illustia.resources.download_settings_summary
import com.yunfie.illustia.resources.download_settings_title
import com.yunfie.illustia.resources.flag_ugoira_auto_play_desc
import com.yunfie.illustia.resources.flag_ugoira_auto_play_title
import com.yunfie.illustia.resources.image_cache_capacity
import com.yunfie.illustia.resources.image_cache_capacity_desc
import com.yunfie.illustia.resources.image_cache_capacity_unlimited
import com.yunfie.illustia.resources.image_columns
import com.yunfie.illustia.resources.image_columns_landscape
import com.yunfie.illustia.resources.image_detail_quality
import com.yunfie.illustia.resources.image_fullscreen_quality
import com.yunfie.illustia.resources.image_high_quality
import com.yunfie.illustia.resources.image_high_quality_desc
import com.yunfie.illustia.resources.image_manga_reader_mode
import com.yunfie.illustia.resources.image_manga_reader_mode_desc
import com.yunfie.illustia.resources.image_prefetch
import com.yunfie.illustia.resources.image_prefetch_desc
import com.yunfie.illustia.resources.image_preview_quality
import com.yunfie.illustia.resources.image_quality_high
import com.yunfie.illustia.resources.image_quality_low
import com.yunfie.illustia.resources.image_quality_medium
import com.yunfie.illustia.resources.image_related_columns
import com.yunfie.illustia.resources.image_section_cache
import com.yunfie.illustia.resources.image_section_customization
import com.yunfie.illustia.resources.image_section_layout
import com.yunfie.illustia.resources.image_section_quality
import com.yunfie.illustia.resources.image_settings_title
import com.yunfie.illustia.resources.image_smart_cache
import com.yunfie.illustia.resources.image_smart_cache_count
import com.yunfie.illustia.resources.image_smart_cache_desc
import com.yunfie.illustia.resources.image_smart_cache_wifi
import com.yunfie.illustia.resources.image_smart_cache_wifi_desc
import com.yunfie.illustia.resources.network_settings_summary
import com.yunfie.illustia.resources.network_settings_title
import com.yunfie.illustia.resources.setting_grid_pinch_to_zoom_desc
import com.yunfie.illustia.resources.setting_grid_pinch_to_zoom_title
import com.yunfie.illustia.resources.setting_image_viewer_swipe_to_dismiss_desc
import com.yunfie.illustia.resources.setting_image_viewer_swipe_to_dismiss_title
import com.yunfie.illustia.resources.setting_quick_peek_desc
import com.yunfie.illustia.resources.setting_quick_peek_shared_element_transition_desc
import com.yunfie.illustia.resources.setting_quick_peek_shared_element_transition_title
import com.yunfie.illustia.resources.setting_quick_peek_title
import com.yunfie.illustia.resources.settings_wide_color_gamut_desc
import com.yunfie.illustia.resources.settings_wide_color_gamut_title
import com.yunfie.illustia.resources.settings_wide_color_gamut_unsupported
import com.yunfie.illustia.resources.viewer_comic_mode
import com.yunfie.illustia.resources.viewer_page_mode
import com.yunfie.illustia.resources.wallpaper_playlist
import com.yunfie.illustia.resources.wallpaper_playlist_desc
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.BackNavigationHandler
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import com.yunfie.illustia.ui.components.isRunningOnDesktop
import com.yunfie.illustia.ui.components.isWideColorGamutDisplaySupported
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import org.jetbrains.compose.resources.stringResource as resString

@Composable
fun ImageSettingsScreen(
    settings: AppSettings,
    actions: ImageSettingsActions,
    onBack: () -> Unit,
    onOpenWallpaperPlaylistSettings: () -> Unit = { actions.openWallpaperPlaylistSettings() },
    onOpenCardCustomizationSettings: () -> Unit = { actions.openCardCustomizationSettings() },
    onOpenDetailSectionSettings: () -> Unit = { actions.openDetailSectionSettings() },
    onOpenDownloadSettings: () -> Unit = { actions.openDownloadSettings() },
    onOpenNetworkSettings: () -> Unit = { actions.openNetworkSettings() },
) {
    BackNavigationHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val supportsWcg = isWideColorGamutDisplaySupported()
    val isDesktop = isRunningOnDesktop()

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.image_settings_title),
                largeTitle = stringResource(Res.string.image_settings_title),
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
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
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
                Section(stringResource(Res.string.image_section_quality)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            stringResource(Res.string.image_high_quality),
                            settings.highQualityImages,
                            actions::updateHighQuality,
                            stringResource(Res.string.image_high_quality_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.settings_wide_color_gamut_title),
                            checked = settings.wideColorGamutEnabled && supportsWcg,
                            onCheckedChange = { if (supportsWcg) actions.updateWideColorGamut(it) },
                            summary =
                                if (supportsWcg) {
                                    stringResource(Res.string.settings_wide_color_gamut_desc)
                                } else {
                                    stringResource(Res.string.settings_wide_color_gamut_unsupported)
                                },
                            enabled = supportsWcg,
                        )
                        DividerLine()
                        SettingSwitchRow(
                            stringResource(Res.string.image_prefetch),
                            settings.prefetchImages,
                            actions::updatePrefetchImages,
                            stringResource(Res.string.image_prefetch_desc),
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(Res.string.image_preview_quality),
                            values = listOf("low", "medium", "high"),
                            selected = settings.feedPreviewQuality,
                            label = { qualityLabel(it) },
                            onSelect = actions::updateFeedPreviewQuality,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(Res.string.image_detail_quality),
                            values = listOf("low", "medium", "high"),
                            selected = settings.illustDetailQuality,
                            label = { qualityLabel(it) },
                            onSelect = actions::updateIllustDetailQuality,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(Res.string.image_fullscreen_quality),
                            values = listOf("low", "medium", "high"),
                            selected = settings.fullscreenQuality,
                            label = { qualityLabel(it) },
                            onSelect = actions::updateFullscreenQuality,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(Res.string.image_manga_reader_mode),
                            summary = stringResource(Res.string.image_manga_reader_mode_desc),
                            values = listOf("paged", "vertical"),
                            selected = settings.mangaReaderMode,
                            label = {
                                if (it == "vertical") {
                                    stringResource(Res.string.viewer_comic_mode)
                                } else {
                                    stringResource(Res.string.viewer_page_mode)
                                }
                            },
                            onSelect = actions::updateMangaReaderMode,
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.ambient_light_viewer_title),
                            checked = settings.dynamicAmbientViewerEnabled,
                            onCheckedChange = actions::updateDynamicAmbientViewerEnabled,
                            summary = stringResource(Res.string.ambient_light_viewer_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.setting_image_viewer_swipe_to_dismiss_title),
                            checked = settings.imageViewerSwipeToDismissEnabled,
                            onCheckedChange = actions::updateImageViewerSwipeToDismissEnabled,
                            summary = stringResource(Res.string.setting_image_viewer_swipe_to_dismiss_desc),
                        )
                    }
                }
            }

            item {
                Section(stringResource(Res.string.image_section_layout)) {
                    ElevatedPanel {
                        SettingDropdownRow(
                            title = stringResource(Res.string.image_columns),
                            values = listOf(1, 2, 3, 4),
                            selected = settings.verticalColumnCount.coerceIn(1, 4),
                            label = { stringResource(Res.string.data_columns_count, it) },
                            onSelect = actions::updateVerticalColumnCount,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(Res.string.image_columns_landscape),
                            values = listOf(3, 4, 5, 6),
                            selected = settings.horizontalColumnCount.coerceIn(3, 6),
                            label = { stringResource(Res.string.data_columns_count, it) },
                            onSelect = actions::updateHorizontalColumnCount,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(Res.string.image_related_columns),
                            values = listOf(0, 2, 3, 4, 5),
                            selected = if (settings.relatedIllustColumnCount in 2..5) settings.relatedIllustColumnCount else 0,
                            label = {
                                if (it == 0) {
                                    stringResource(Res.string.data_columns_dynamic)
                                } else {
                                    stringResource(Res.string.data_columns_count, it)
                                }
                            },
                            onSelect = actions::updateRelatedIllustColumnCount,
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.setting_grid_pinch_to_zoom_title),
                            checked = settings.gridPinchToZoom,
                            onCheckedChange = actions::updateGridPinchToZoom,
                            summary = stringResource(Res.string.setting_grid_pinch_to_zoom_desc),
                        )
                    }
                }
            }

            item {
                Section(stringResource(Res.string.image_section_customization)) {
                    ElevatedPanel {
                        SettingLinkRow(
                            title = stringResource(Res.string.card_customization_settings_title),
                            summary = stringResource(Res.string.card_customization_settings_summary),
                            onClick = onOpenCardCustomizationSettings,
                        )
                        DividerLine()
                        SettingLinkRow(
                            title = resString(Res.string.detail_section_settings_title),
                            summary = resString(Res.string.detail_section_settings_summary),
                            onClick = onOpenDetailSectionSettings,
                        )
                        if (!isDesktop) {
                            DividerLine()
                            SettingLinkRow(
                                title = stringResource(Res.string.wallpaper_playlist),
                                summary = stringResource(Res.string.wallpaper_playlist_desc),
                                onClick = onOpenWallpaperPlaylistSettings,
                            )
                        }
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.setting_quick_peek_title),
                            checked = settings.quickPeekEnabled,
                            onCheckedChange = actions::updateQuickPeekEnabled,
                            summary = stringResource(Res.string.setting_quick_peek_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.setting_quick_peek_shared_element_transition_title),
                            checked = settings.quickPeekSharedElementTransition,
                            onCheckedChange = actions::updateQuickPeekSharedElementTransition,
                            summary = stringResource(Res.string.setting_quick_peek_shared_element_transition_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = resString(Res.string.flag_ugoira_auto_play_title),
                            checked = settings.ugoiraAutoPlay,
                            onCheckedChange = actions::updateUgoiraAutoPlay,
                            summary = resString(Res.string.flag_ugoira_auto_play_desc),
                        )
                    }
                }
            }

            item {
                Section(stringResource(Res.string.download_settings_title)) {
                    ElevatedPanel {
                        SettingLinkRow(
                            title = stringResource(Res.string.download_settings_title),
                            summary = stringResource(Res.string.download_settings_summary),
                            onClick = onOpenDownloadSettings,
                        )
                    }
                }
            }

            item {
                Section(stringResource(Res.string.image_section_cache)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(Res.string.image_smart_cache),
                            checked = settings.smartCacheEnabled,
                            onCheckedChange = actions::updateSmartCacheEnabled,
                            summary = stringResource(Res.string.image_smart_cache_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.image_smart_cache_wifi),
                            checked = settings.smartCacheWifiOnly,
                            onCheckedChange = actions::updateSmartCacheWifiOnly,
                            summary = stringResource(Res.string.image_smart_cache_wifi_desc),
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(Res.string.image_smart_cache_count),
                            values = listOf(6, 12, 20, 30),
                            selected = settings.smartCacheItemCount,
                            label = { stringResource(Res.string.data_items_count, it) },
                            onSelect = actions::updateSmartCacheItemCount,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(Res.string.image_cache_capacity),
                            summary = stringResource(Res.string.image_cache_capacity_desc),
                            values = listOf(0, 100, 300, 500, 1000),
                            selected = settings.imageCacheSizeMb,
                            label = {
                                if (it == 0) {
                                    stringResource(Res.string.image_cache_capacity_unlimited)
                                } else {
                                    "$it MB"
                                }
                            },
                            onSelect = actions::updateImageCacheSizeMb,
                        )
                    }
                }
            }

            item {
                Section(stringResource(Res.string.network_settings_title)) {
                    ElevatedPanel {
                        SettingLinkRow(
                            title = stringResource(Res.string.network_settings_title),
                            summary = stringResource(Res.string.network_settings_summary),
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
        "high" -> stringResource(Res.string.image_quality_high)
        "medium" -> stringResource(Res.string.image_quality_medium)
        else -> stringResource(Res.string.image_quality_low)
    }
