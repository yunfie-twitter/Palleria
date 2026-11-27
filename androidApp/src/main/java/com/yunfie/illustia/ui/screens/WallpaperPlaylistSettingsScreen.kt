package com.yunfie.illustia.ui.screens

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.nativebridge.NativeImageStore
import com.yunfie.illustia.platform.AppStrings
import com.yunfie.illustia.platform.StringKey
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.wallpaper.LiveWallpaperSupport
import com.yunfie.illustia.wallpaper.PalleriaLiveWallpaperService
import com.yunfie.illustia.ui.screens.WallpaperPlaylistSettingsScreen as SharedWallpaperPlaylistSettingsScreen

@Composable
fun WallpaperPlaylistSettingsScreen(
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val imageStore = remember(context) { NativeImageStore(context.applicationContext) }
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            if (imageStore.persistReadOnlyTreeUri(uri)) {
                viewModel.updateLiveWallpaperSourceFolder(uri.toString())
                viewModel.updateLiveWallpaperSource("selected_folder")
            } else {
                viewModel.showMessage(AppStrings.get(StringKey.LiveWallpaperFolderPermissionFailed))
            }
        }
    val actions =
        remember(viewModel, settings, imageStore, folderPicker, context) {
            object : WallpaperPlaylistActions {
                override val settings: AppSettings = settings

                override fun isLiveWallpaperSupported(): Boolean = LiveWallpaperSupport.isSupported(context)

                override fun updateWallpaperPlaylistEnabled(enabled: Boolean) = viewModel.updateWallpaperPlaylistEnabled(enabled)

                override fun updateLiveWallpaperSource(value: String) = viewModel.updateLiveWallpaperSource(value)

                override fun updateLiveWallpaperSourceFolder(folder: String) = viewModel.updateLiveWallpaperSourceFolder(folder)

                override fun updateLiveWallpaperChangeMode(value: String) = viewModel.updateLiveWallpaperChangeMode(value)

                override fun updateLiveWallpaperIntervalMinutes(minutes: Int) = viewModel.updateLiveWallpaperIntervalMinutes(minutes)

                override fun updateLiveWallpaperOrder(value: String) = viewModel.updateLiveWallpaperOrder(value)

                override fun updateLiveWallpaperScaleMode(value: String) = viewModel.updateLiveWallpaperScaleMode(value)

                override fun updateLiveWallpaperBackground(value: String) = viewModel.updateLiveWallpaperBackground(value)

                override fun updateLiveWallpaperCrossfade(enabled: Boolean) = viewModel.updateLiveWallpaperCrossfade(enabled)

                override fun updateLiveWallpaperExcludeSensitive(enabled: Boolean) = viewModel.updateLiveWallpaperExcludeSensitive(enabled)

                override fun folderLabel(folder: String): String = imageStore.folderLabel(folder)

                override fun pickSourceFolder() = folderPicker.launch(null)

                override fun showMessage(message: String) = viewModel.showMessage(message)

                override fun openLiveWallpaperPreview() {
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
                }
            }
        }
    SharedWallpaperPlaylistSettingsScreen(
        actions = actions,
        onBack = onBack,
    )
}
