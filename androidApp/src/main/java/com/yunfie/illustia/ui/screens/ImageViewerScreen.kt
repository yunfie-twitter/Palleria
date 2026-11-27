package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.ui.screens.ImageViewerScreen as SharedImageViewerScreen

@Composable
fun ImageViewerScreen(
    illust: Illust,
    startPage: Int,
    onSave: (String, String) -> Unit,
    onBack: () -> Unit,
    isBookmarked: Boolean,
    onBookmark: () -> Unit,
    onMessage: (String) -> Unit,
    fullscreenQuality: String,
    prefetchImages: Boolean,
    mangaReaderMode: String,
    onPageChanged: (Int) -> Unit,
    loadUgoiraPlayback: suspend (Long) -> UgoiraPlayback,
    ambientLightEnabled: Boolean = false,
    volumeKeyPageTurnerEnabled: Boolean = false,
    swipeToDismissEnabled: Boolean = true,
    ugoiraPowerSave: Boolean = true,
    mangaAdaptivePreload: Boolean = true,
    progressiveQuality: Boolean = false,
) {
    SharedImageViewerScreen(
        illust = illust,
        startPage = startPage,
        onSave = onSave,
        onBack = onBack,
        isBookmarked = isBookmarked,
        onBookmark = onBookmark,
        onMessage = onMessage,
        fullscreenQuality = fullscreenQuality,
        prefetchImages = prefetchImages,
        mangaReaderMode = mangaReaderMode,
        onPageChanged = onPageChanged,
        loadUgoiraPlayback = loadUgoiraPlayback,
        ambientLightEnabled = ambientLightEnabled,
        volumeKeyPageTurnerEnabled = volumeKeyPageTurnerEnabled,
        swipeToDismissEnabled = swipeToDismissEnabled,
        ugoiraPowerSave = ugoiraPowerSave,
        mangaAdaptivePreload = mangaAdaptivePreload,
        progressiveQuality = progressiveQuality,
        platform = androidUserProfilePlatform(),
    )
}
