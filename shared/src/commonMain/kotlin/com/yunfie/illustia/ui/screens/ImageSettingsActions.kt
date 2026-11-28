package com.yunfie.illustia.ui.screens

/** 画像設定画面が ViewModel に要求する操作。 */
interface ImageSettingsActions {
    fun updateHighQuality(value: Boolean)

    fun updateWideColorGamut(value: Boolean)

    fun updatePrefetchImages(value: Boolean)

    fun updateFeedPreviewQuality(value: String)

    fun updateIllustDetailQuality(value: String)

    fun updateFullscreenQuality(value: String)

    fun updateMangaReaderMode(value: String)

    fun updateDynamicAmbientViewerEnabled(value: Boolean)

    fun updateImageViewerSwipeToDismissEnabled(value: Boolean)

    fun updateVerticalColumnCount(count: Int)

    fun updateHorizontalColumnCount(count: Int)

    fun updateRelatedIllustColumnCount(count: Int)

    fun updateGridPinchToZoom(enabled: Boolean)

    fun updateQuickPeekEnabled(value: Boolean)

    fun updateQuickPeekSharedElementTransition(enabled: Boolean)

    fun updateUgoiraAutoPlay(enabled: Boolean)

    fun updateSmartCacheEnabled(value: Boolean)

    fun updateSmartCacheWifiOnly(value: Boolean)

    fun updateSmartCacheItemCount(count: Int)

    fun updateImageCacheSizeMb(value: Int)

    fun openWallpaperPlaylistSettings()

    fun openCardCustomizationSettings()

    fun openDetailSectionSettings()

    fun openDownloadSettings()

    fun openNetworkSettings()
}
