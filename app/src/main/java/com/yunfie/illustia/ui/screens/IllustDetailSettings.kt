package com.yunfie.illustia.ui.screens

/**
 * [IllustDetailScreen] の読み込み・表示系フラグをひとまとめにしたもの。
 * 以前は 46 個の引数のうち約 15 個がこの種のフラグで、呼び出し側の可読性を損なっていた。
 */
class IllustDetailSettings(
    val highQualityImages: Boolean,
    val detailQuality: String,
    val prefetchImages: Boolean,
    val confirmOnLongPressSave: Boolean,
    val skipConfirmOnDetailSave: Boolean,
    val smartDownloadNaming: Boolean = true,
    val ugoiraPowerSave: Boolean = true,
    val mangaAdaptivePreload: Boolean = true,
    val detailSectionOrder: List<String>,
    val relatedIllustColumnCount: Int = 3,
    val liquidGlassButtons: Boolean = false,
    val liquidGlassFollowButton: Boolean = false,
    val progressiveQuality: Boolean = false,
    val relatedSectionInitiallyReady: Boolean = false,
    val initialPage: Int = 0,
)
