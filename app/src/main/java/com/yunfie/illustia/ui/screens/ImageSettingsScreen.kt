package com.yunfie.illustia.ui.screens

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.PixivImageProxyOptions
import com.yunfie.illustia.nativebridge.NativeImageStore
import com.yunfie.illustia.settings.pixivNetworkModeLabel
import com.yunfie.illustia.settings.pixivNetworkModeOptions
import com.yunfie.illustia.wallpaper.PalleriaLiveWallpaperService
import com.yunfie.illustia.wallpaper.LiveWallpaperSupport
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import com.yunfie.illustia.ui.components.overlayActionButtonColors
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
    val liveWallpaperSource = if (
        state.settings.liveWallpaperSource == "folder" ||
        state.settings.liveWallpaperSource == "selected_folder"
    ) "selected_folder" else "saved_images"
    val liveWallpaperSupported = remember { LiveWallpaperSupport.isSupported() }
    LaunchedEffect(liveWallpaperSupported, state.settings.wallpaperPlaylistEnabled) {
        if (!liveWallpaperSupported && state.settings.wallpaperPlaylistEnabled) {
            viewModel.updateWallpaperPlaylistEnabled(false)
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            imageStore.persistTreeUri(it)
            saveLocation = imageStore.currentPathLabel()
        }
    }
    val liveWallpaperFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            if (imageStore.persistReadOnlyTreeUri(it)) {
                viewModel.updateLiveWallpaperSourceFolder(it.toString())
                viewModel.updateLiveWallpaperSource("selected_folder")
            } else {
                viewModel.showMessage(context.getString(R.string.live_wallpaper_folder_permission_failed))
            }
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
                    DividerLine()
                    SettingDropdownRow(
                        title = stringResource(R.string.image_manga_reader_mode),
                        summary = stringResource(R.string.image_manga_reader_mode_desc),
                        values = listOf("paged", "vertical"),
                        selected = state.settings.mangaReaderMode,
                        label = { if (it == "vertical") stringResource(R.string.viewer_comic_mode) else stringResource(R.string.viewer_page_mode) },
                        onSelect = viewModel::updateMangaReaderMode,
                    )
                }
            }}

            item { Section(stringResource(R.string.image_section_cache)) {
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
            }}

            item { Section(stringResource(R.string.image_section_layout)) {
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
                }
            }}

            item { Section(stringResource(R.string.image_section_storage)) {
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
                }
            }}

            item { Section(stringResource(R.string.wallpaper_playlist)) {
                if (!liveWallpaperSupported) {
                    ElevatedPanel {
                        Text(
                            text = stringResource(R.string.live_wallpaper_unsupported_hyperos),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                } else {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(R.string.wallpaper_playlist),
                            checked = state.settings.wallpaperPlaylistEnabled,
                            onCheckedChange = viewModel::updateWallpaperPlaylistEnabled,
                            summary = stringResource(R.string.wallpaper_playlist_desc),
                        )
                        DividerLine()
                        ArrowPreference(
                            title = stringResource(R.string.live_wallpaper_open_preview),
                            summary = stringResource(R.string.live_wallpaper_open_preview_desc),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val previewIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                    putExtra(
                                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                        ComponentName(context, PalleriaLiveWallpaperService::class.java),
                                    )
                                }
                                runCatching { context.startActivity(previewIntent) }
                                    .onFailure {
                                        runCatching {
                                            context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                                        }
                                    }
                            },
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(R.string.live_wallpaper_source),
                            values = listOf("saved_images", "selected_folder"),
                            selected = liveWallpaperSource,
                            label = {
                                if (it == "selected_folder") stringResource(R.string.live_wallpaper_source_folder)
                                else stringResource(R.string.live_wallpaper_source_all)
                            },
                            onSelect = viewModel::updateLiveWallpaperSource,
                        )
                        if (liveWallpaperSource == "selected_folder") {
                            DividerLine()
                            ArrowPreference(
                                title = stringResource(R.string.live_wallpaper_folder_name),
                                summary = imageStore
                                    .folderLabel(state.settings.liveWallpaperSourceFolder)
                                    .ifBlank { stringResource(R.string.live_wallpaper_folder_name_desc) },
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { liveWallpaperFolderPicker.launch(null) },
                            )
                        }
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(R.string.live_wallpaper_change_mode),
                            values = listOf("screen", "home", "interval", "double_tap"),
                            selected = state.settings.liveWallpaperChangeMode,
                            label = { liveWallpaperChangeModeLabel(it) },
                            onSelect = viewModel::updateLiveWallpaperChangeMode,
                        )
                        if (state.settings.liveWallpaperChangeMode == "interval") {
                            DividerLine()
                            SettingDropdownRow(
                                title = stringResource(R.string.live_wallpaper_interval),
                                values = listOf(15, 30, 60, 180, 360, 720, 1440),
                                selected = state.settings.liveWallpaperIntervalMinutes,
                                label = { stringResource(R.string.live_wallpaper_minutes, it) },
                                onSelect = viewModel::updateLiveWallpaperIntervalMinutes,
                            )
                        }
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(R.string.live_wallpaper_order),
                            values = listOf("random", "newest", "oldest"),
                            selected = state.settings.liveWallpaperOrder,
                            label = { liveWallpaperOrderLabel(it) },
                            onSelect = viewModel::updateLiveWallpaperOrder,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(R.string.live_wallpaper_scale),
                            values = listOf("cover", "contain", "fit_width", "fit_height"),
                            selected = state.settings.liveWallpaperScaleMode,
                            label = { liveWallpaperScaleLabel(it) },
                            onSelect = viewModel::updateLiveWallpaperScaleMode,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(R.string.live_wallpaper_background),
                            values = listOf("black", "white", "dominant", "blur"),
                            selected = state.settings.liveWallpaperBackground,
                            label = { liveWallpaperBackgroundLabel(it) },
                            onSelect = viewModel::updateLiveWallpaperBackground,
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(R.string.live_wallpaper_crossfade),
                            checked = state.settings.liveWallpaperCrossfade,
                            onCheckedChange = viewModel::updateLiveWallpaperCrossfade,
                            summary = stringResource(R.string.live_wallpaper_crossfade_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(R.string.live_wallpaper_exclude_sensitive),
                            checked = state.settings.liveWallpaperExcludeSensitive,
                            onCheckedChange = viewModel::updateLiveWallpaperExcludeSensitive,
                            summary = stringResource(R.string.live_wallpaper_exclude_sensitive_desc),
                        )
                        DividerLine()
                        Text(
                            text = stringResource(R.string.live_wallpaper_power_note),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                }
            }}

            item { Section(stringResource(R.string.image_section_proxy)) {
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
private fun liveWallpaperChangeModeLabel(value: String): String = when (value) {
    "home" -> stringResource(R.string.live_wallpaper_change_home)
    "interval" -> stringResource(R.string.live_wallpaper_change_interval)
    "double_tap" -> stringResource(R.string.live_wallpaper_change_double_tap)
    else -> stringResource(R.string.live_wallpaper_change_screen)
}

@Composable
private fun liveWallpaperOrderLabel(value: String): String = when (value) {
    "newest" -> stringResource(R.string.live_wallpaper_order_newest)
    "oldest" -> stringResource(R.string.live_wallpaper_order_oldest)
    else -> stringResource(R.string.live_wallpaper_order_random)
}

@Composable
private fun liveWallpaperScaleLabel(value: String): String = when (value) {
    "contain" -> stringResource(R.string.live_wallpaper_scale_contain)
    "fit_width" -> stringResource(R.string.live_wallpaper_scale_width)
    "fit_height" -> stringResource(R.string.live_wallpaper_scale_height)
    else -> stringResource(R.string.live_wallpaper_scale_cover)
}

@Composable
private fun liveWallpaperBackgroundLabel(value: String): String = when (value) {
    "white" -> stringResource(R.string.live_wallpaper_background_white)
    "dominant" -> stringResource(R.string.live_wallpaper_background_dominant)
    "blur" -> stringResource(R.string.live_wallpaper_background_blur)
    else -> stringResource(R.string.live_wallpaper_background_black)
}
