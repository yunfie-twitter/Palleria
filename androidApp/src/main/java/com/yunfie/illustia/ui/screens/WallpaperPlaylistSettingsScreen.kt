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
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.nativebridge.NativeImageStore
import com.yunfie.illustia.platform.AppStrings
import com.yunfie.illustia.platform.StringKey
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.live_wallpaper_background
import com.yunfie.illustia.resources.live_wallpaper_background_black
import com.yunfie.illustia.resources.live_wallpaper_background_blur
import com.yunfie.illustia.resources.live_wallpaper_background_dominant
import com.yunfie.illustia.resources.live_wallpaper_background_white
import com.yunfie.illustia.resources.live_wallpaper_change_double_tap
import com.yunfie.illustia.resources.live_wallpaper_change_home
import com.yunfie.illustia.resources.live_wallpaper_change_interval
import com.yunfie.illustia.resources.live_wallpaper_change_mode
import com.yunfie.illustia.resources.live_wallpaper_change_screen
import com.yunfie.illustia.resources.live_wallpaper_crossfade
import com.yunfie.illustia.resources.live_wallpaper_crossfade_desc
import com.yunfie.illustia.resources.live_wallpaper_exclude_sensitive
import com.yunfie.illustia.resources.live_wallpaper_exclude_sensitive_desc
import com.yunfie.illustia.resources.live_wallpaper_folder_name
import com.yunfie.illustia.resources.live_wallpaper_folder_name_desc
import com.yunfie.illustia.resources.live_wallpaper_interval
import com.yunfie.illustia.resources.live_wallpaper_minutes
import com.yunfie.illustia.resources.live_wallpaper_open_preview
import com.yunfie.illustia.resources.live_wallpaper_open_preview_desc
import com.yunfie.illustia.resources.live_wallpaper_order
import com.yunfie.illustia.resources.live_wallpaper_order_newest
import com.yunfie.illustia.resources.live_wallpaper_order_oldest
import com.yunfie.illustia.resources.live_wallpaper_order_random
import com.yunfie.illustia.resources.live_wallpaper_power_note
import com.yunfie.illustia.resources.live_wallpaper_scale
import com.yunfie.illustia.resources.live_wallpaper_scale_contain
import com.yunfie.illustia.resources.live_wallpaper_scale_cover
import com.yunfie.illustia.resources.live_wallpaper_scale_height
import com.yunfie.illustia.resources.live_wallpaper_scale_width
import com.yunfie.illustia.resources.live_wallpaper_source
import com.yunfie.illustia.resources.live_wallpaper_source_all
import com.yunfie.illustia.resources.live_wallpaper_source_folder
import com.yunfie.illustia.resources.live_wallpaper_unsupported_hyperos
import com.yunfie.illustia.resources.wallpaper_playlist
import com.yunfie.illustia.resources.wallpaper_playlist_desc
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import com.yunfie.illustia.wallpaper.LiveWallpaperSupport
import com.yunfie.illustia.wallpaper.PalleriaLiveWallpaperService
import org.jetbrains.compose.resources.stringResource
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
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val imageStore = remember(context) { NativeImageStore(context.applicationContext) }
    val liveWallpaperSupported = remember(context) { LiveWallpaperSupport.isSupported(context) }

    LaunchedEffect(liveWallpaperSupported, settings.wallpaperPlaylistEnabled) {
        if (!liveWallpaperSupported && settings.wallpaperPlaylistEnabled) {
            viewModel.updateWallpaperPlaylistEnabled(false)
        }
    }

    val liveWallpaperSource =
        if (
            settings.liveWallpaperSource == "folder" ||
            settings.liveWallpaperSource == "selected_folder"
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
                    viewModel.showMessage(AppStrings.get(StringKey.LiveWallpaperFolderPermissionFailed))
                }
            }
        }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.wallpaper_playlist),
                largeTitle = stringResource(Res.string.wallpaper_playlist),
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
                Section(stringResource(Res.string.wallpaper_playlist)) {
                    if (!liveWallpaperSupported) {
                        ElevatedPanel {
                            Text(
                                text = stringResource(Res.string.live_wallpaper_unsupported_hyperos),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                    } else {
                        ElevatedPanel {
                            SettingSwitchRow(
                                title = stringResource(Res.string.wallpaper_playlist),
                                checked = settings.wallpaperPlaylistEnabled,
                                onCheckedChange = viewModel::updateWallpaperPlaylistEnabled,
                                summary = stringResource(Res.string.wallpaper_playlist_desc),
                            )
                            DividerLine()
                            ArrowPreference(
                                title = stringResource(Res.string.live_wallpaper_open_preview),
                                summary = stringResource(Res.string.live_wallpaper_open_preview_desc),
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
                                title = stringResource(Res.string.live_wallpaper_source),
                                values = listOf("saved_images", "selected_folder"),
                                selected = liveWallpaperSource,
                                label = {
                                    if (it == "selected_folder") {
                                        stringResource(Res.string.live_wallpaper_source_folder)
                                    } else {
                                        stringResource(Res.string.live_wallpaper_source_all)
                                    }
                                },
                                onSelect = viewModel::updateLiveWallpaperSource,
                            )
                            if (liveWallpaperSource == "selected_folder") {
                                DividerLine()
                                ArrowPreference(
                                    title = stringResource(Res.string.live_wallpaper_folder_name),
                                    summary =
                                        imageStore
                                            .folderLabel(settings.liveWallpaperSourceFolder)
                                            .ifBlank { stringResource(Res.string.live_wallpaper_folder_name_desc) },
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { liveWallpaperFolderPicker.launch(null) },
                                )
                            }
                            DividerLine()
                            SettingDropdownRow(
                                title = stringResource(Res.string.live_wallpaper_change_mode),
                                values = listOf("screen", "home", "interval", "double_tap"),
                                selected = settings.liveWallpaperChangeMode,
                                label = { liveWallpaperChangeModeLabel(it) },
                                onSelect = viewModel::updateLiveWallpaperChangeMode,
                            )
                            if (settings.liveWallpaperChangeMode == "interval") {
                                DividerLine()
                                SettingDropdownRow(
                                    title = stringResource(Res.string.live_wallpaper_interval),
                                    values = listOf(15, 30, 60, 180, 360, 720, 1440),
                                    selected = settings.liveWallpaperIntervalMinutes,
                                    label = { stringResource(Res.string.live_wallpaper_minutes, it) },
                                    onSelect = viewModel::updateLiveWallpaperIntervalMinutes,
                                )
                            }
                            DividerLine()
                            SettingDropdownRow(
                                title = stringResource(Res.string.live_wallpaper_order),
                                values = listOf("random", "newest", "oldest"),
                                selected = settings.liveWallpaperOrder,
                                label = { liveWallpaperOrderLabel(it) },
                                onSelect = viewModel::updateLiveWallpaperOrder,
                            )
                            DividerLine()
                            SettingDropdownRow(
                                title = stringResource(Res.string.live_wallpaper_scale),
                                values = listOf("cover", "contain", "fit_width", "fit_height"),
                                selected = settings.liveWallpaperScaleMode,
                                label = { liveWallpaperScaleLabel(it) },
                                onSelect = viewModel::updateLiveWallpaperScaleMode,
                            )
                            DividerLine()
                            SettingDropdownRow(
                                title = stringResource(Res.string.live_wallpaper_background),
                                values = listOf("black", "white", "dominant", "blur"),
                                selected = settings.liveWallpaperBackground,
                                label = { liveWallpaperBackgroundLabel(it) },
                                onSelect = viewModel::updateLiveWallpaperBackground,
                            )
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(Res.string.live_wallpaper_crossfade),
                                checked = settings.liveWallpaperCrossfade,
                                onCheckedChange = viewModel::updateLiveWallpaperCrossfade,
                                summary = stringResource(Res.string.live_wallpaper_crossfade_desc),
                            )
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(Res.string.live_wallpaper_exclude_sensitive),
                                checked = settings.liveWallpaperExcludeSensitive,
                                onCheckedChange = viewModel::updateLiveWallpaperExcludeSensitive,
                                summary = stringResource(Res.string.live_wallpaper_exclude_sensitive_desc),
                            )
                            DividerLine()
                            Text(
                                text = stringResource(Res.string.live_wallpaper_power_note),
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
        "home" -> stringResource(Res.string.live_wallpaper_change_home)
        "interval" -> stringResource(Res.string.live_wallpaper_change_interval)
        "double_tap" -> stringResource(Res.string.live_wallpaper_change_double_tap)
        else -> stringResource(Res.string.live_wallpaper_change_screen)
    }

@Composable
private fun liveWallpaperOrderLabel(value: String): String =
    when (value) {
        "newest" -> stringResource(Res.string.live_wallpaper_order_newest)
        "oldest" -> stringResource(Res.string.live_wallpaper_order_oldest)
        else -> stringResource(Res.string.live_wallpaper_order_random)
    }

@Composable
private fun liveWallpaperScaleLabel(value: String): String =
    when (value) {
        "contain" -> stringResource(Res.string.live_wallpaper_scale_contain)
        "fit_width" -> stringResource(Res.string.live_wallpaper_scale_width)
        "fit_height" -> stringResource(Res.string.live_wallpaper_scale_height)
        else -> stringResource(Res.string.live_wallpaper_scale_cover)
    }

@Composable
private fun liveWallpaperBackgroundLabel(value: String): String =
    when (value) {
        "white" -> stringResource(Res.string.live_wallpaper_background_white)
        "dominant" -> stringResource(Res.string.live_wallpaper_background_dominant)
        "blur" -> stringResource(Res.string.live_wallpaper_background_blur)
        else -> stringResource(Res.string.live_wallpaper_background_black)
    }
