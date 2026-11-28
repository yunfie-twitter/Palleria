@file:JvmName("AndroidImageSettingsScreen")

package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.screens.ImageSettingsScreen as SharedImageSettingsScreen

@Composable
fun ImageSettingsScreen(
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onOpenWallpaperPlaylistSettings: () -> Unit = { viewModel.openWallpaperPlaylistSettings() },
    onOpenCardCustomizationSettings: () -> Unit = { viewModel.openCardCustomizationSettings() },
    onOpenDetailSectionSettings: () -> Unit = { viewModel.openDetailSectionSettings() },
    onOpenDownloadSettings: () -> Unit = { viewModel.openDownloadSettings() },
    onOpenNetworkSettings: () -> Unit = { viewModel.openNetworkSettings() },
) {
    val actions =
        remember(viewModel) {
            object : ImageSettingsActions {
                override fun updateHighQuality(value: Boolean) = viewModel.updateHighQuality(value)

                override fun updateWideColorGamut(value: Boolean) = viewModel.updateWideColorGamut(value)

                override fun updatePrefetchImages(value: Boolean) = viewModel.updatePrefetchImages(value)

                override fun updateFeedPreviewQuality(value: String) = viewModel.updateFeedPreviewQuality(value)

                override fun updateIllustDetailQuality(value: String) = viewModel.updateIllustDetailQuality(value)

                override fun updateFullscreenQuality(value: String) = viewModel.updateFullscreenQuality(value)

                override fun updateMangaReaderMode(value: String) = viewModel.updateMangaReaderMode(value)

                override fun updateDynamicAmbientViewerEnabled(value: Boolean) = viewModel.updateDynamicAmbientViewerEnabled(value)

                override fun updateImageViewerSwipeToDismissEnabled(value: Boolean) =
                    viewModel.updateImageViewerSwipeToDismissEnabled(value)

                override fun updateVerticalColumnCount(count: Int) = viewModel.updateVerticalColumnCount(count)

                override fun updateHorizontalColumnCount(count: Int) = viewModel.updateHorizontalColumnCount(count)

                override fun updateRelatedIllustColumnCount(count: Int) = viewModel.updateRelatedIllustColumnCount(count)

                override fun updateGridPinchToZoom(enabled: Boolean) = viewModel.updateGridPinchToZoom(enabled)

                override fun updateQuickPeekEnabled(value: Boolean) = viewModel.updateQuickPeekEnabled(value)

                override fun updateQuickPeekSharedElementTransition(enabled: Boolean) =
                    viewModel.updateQuickPeekSharedElementTransition(enabled)

                override fun updateUgoiraAutoPlay(enabled: Boolean) = viewModel.updateUgoiraAutoPlay(enabled)

                override fun updateSmartCacheEnabled(value: Boolean) = viewModel.updateSmartCacheEnabled(value)

                override fun updateSmartCacheWifiOnly(value: Boolean) = viewModel.updateSmartCacheWifiOnly(value)

                override fun updateSmartCacheItemCount(count: Int) = viewModel.updateSmartCacheItemCount(count)

                override fun updateImageCacheSizeMb(value: Int) = viewModel.updateImageCacheSizeMb(value)

                override fun openWallpaperPlaylistSettings() = viewModel.openWallpaperPlaylistSettings()

                override fun openCardCustomizationSettings() = viewModel.openCardCustomizationSettings()

                override fun openDetailSectionSettings() = viewModel.openDetailSectionSettings()

                override fun openDownloadSettings() = viewModel.openDownloadSettings()

                override fun openNetworkSettings() = viewModel.openNetworkSettings()
            }
        }
    SharedImageSettingsScreen(
        settings = settings,
        actions = actions,
        onBack = onBack,
        onOpenWallpaperPlaylistSettings = onOpenWallpaperPlaylistSettings,
        onOpenCardCustomizationSettings = onOpenCardCustomizationSettings,
        onOpenDetailSectionSettings = onOpenDetailSectionSettings,
        onOpenDownloadSettings = onOpenDownloadSettings,
        onOpenNetworkSettings = onOpenNetworkSettings,
    )
}
