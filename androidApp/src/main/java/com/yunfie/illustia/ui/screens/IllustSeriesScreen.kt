@file:JvmName("AndroidIllustSeriesScreen")

package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.data.pixiv.IllustSeriesStore
import com.yunfie.illustia.uiRepository
import com.yunfie.illustia.ui.screens.IllustSeriesScreen as SharedIllustSeriesScreen

@Composable
fun IllustSeriesScreen(
    seriesId: Long,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { context.uiRepository() }
    val store = remember(repository, seriesId) { IllustSeriesStore(repository, seriesId) }
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    SharedIllustSeriesScreen(
        store = store,
        settings = settings,
        platform = androidUserProfilePlatform(),
        onBack = onBack,
        onOpenIllust = onOpenIllust,
        onBookmark = { illustId, illust -> viewModel.toggleBookmark(illustId, illust) },
        onIllustLongPress = { illustId, illust -> viewModel.onIllustLongPress(illustId, illust) },
        onMessage = { viewModel.showMessage(it) },
    )
}
