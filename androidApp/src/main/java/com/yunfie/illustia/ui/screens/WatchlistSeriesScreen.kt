package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.data.pixiv.WatchlistStore
import com.yunfie.illustia.uiRepository
import com.yunfie.illustia.ui.screens.WatchlistSeriesScreen as SharedWatchlistSeriesScreen

@Composable
fun WatchlistSeriesScreen(
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { context.uiRepository() }
    val store = remember(repository) { WatchlistStore(repository) }
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    SharedWatchlistSeriesScreen(
        store = store,
        settings = settings,
        platform = androidUserProfilePlatform(),
        onBack = onBack,
        onOpenSeries = onOpenSeries,
        onMessage = { viewModel.showMessage(it) },
        onColumnsChange = viewModel::updateVerticalColumnCount,
    )
}
