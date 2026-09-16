package com.yunfie.illustia.ui.screens

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.nativebridge.NativeImageStore
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import com.yunfie.illustia.wallpaper.LiveWallpaperSupport
import com.yunfie.illustia.wallpaper.PalleriaLiveWallpaperService
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun WallpaperPlaylistSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val imageStore = remember(context) { NativeImageStore(context.applicationContext) }
    val liveWallpaperSupported = remember(context) { LiveWallpaperSupport.isSupported(context) }

    LaunchedEffect(liveWallpaperSupported, state.settings.wallpaperPlaylistEnabled) {
        if (!liveWallpaperSupported && state.settings.wallpaperPlaylistEnabled) {
            viewModel.updateWallpaperPlaylistEnabled(false)
        }
    }

    val liveWallpaperSource =
        if (
            state.settings.liveWallpaperSource == "folder" ||
            state.settings.liveWallpaperSource == "selected_folder"
        ) {
            "selected_folder"
        } else {
            "saved_images"
        }

    val liveWallpaperFolderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
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
                title = stringResource(R.string.wallpaper_playlist),
                largeTitle = stringResource(R.string.wallpaper_playlist),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .background(MiuixTheme.colorScheme.surface),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding() + 16.dp,
                    bottom = paddingValues.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Section(stringResource(R.string.wallpaper_playlist)) {
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
                                    val previewIntent =
                                        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
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
                                    if (it == "selected_folder") {
                                        stringResource(R.string.live_wallpaper_source_folder)
                                    } else {
                                        stringResource(R.string.live_wallpaper_source_all)
                                    }
                                },
                                onSelect = viewModel::updateLiveWallpaperSource,
                            )
                            if (liveWallpaperSource == "selected_folder") {
                                DividerLine()
                                ArrowPreference(
                                    title = stringResource(R.string.live_wallpaper_folder_name),
                                    summary =
                                        imageStore
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
                }
            }
        }
    }
}

@Composable
private fun liveWallpaperChangeModeLabel(value: String): String =
    when (value) {
        "home" -> stringResource(R.string.live_wallpaper_change_home)
        "interval" -> stringResource(R.string.live_wallpaper_change_interval)
        "double_tap" -> stringResource(R.string.live_wallpaper_change_double_tap)
        else -> stringResource(R.string.live_wallpaper_change_screen)
    }

@Composable
private fun liveWallpaperOrderLabel(value: String): String =
    when (value) {
        "newest" -> stringResource(R.string.live_wallpaper_order_newest)
        "oldest" -> stringResource(R.string.live_wallpaper_order_oldest)
        else -> stringResource(R.string.live_wallpaper_order_random)
    }

@Composable
private fun liveWallpaperScaleLabel(value: String): String =
    when (value) {
        "contain" -> stringResource(R.string.live_wallpaper_scale_contain)
        "fit_width" -> stringResource(R.string.live_wallpaper_scale_width)
        "fit_height" -> stringResource(R.string.live_wallpaper_scale_height)
        else -> stringResource(R.string.live_wallpaper_scale_cover)
    }

@Composable
private fun liveWallpaperBackgroundLabel(value: String): String =
    when (value) {
        "white" -> stringResource(R.string.live_wallpaper_background_white)
        "dominant" -> stringResource(R.string.live_wallpaper_background_dominant)
        "blur" -> stringResource(R.string.live_wallpaper_background_blur)
        else -> stringResource(R.string.live_wallpaper_background_black)
    }
