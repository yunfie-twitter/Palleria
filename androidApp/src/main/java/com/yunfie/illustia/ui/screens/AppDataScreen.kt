package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.platform.AppServices
import com.yunfie.illustia.ui.screens.AppDataScreen as SharedAppDataScreen

@Composable
fun AppDataScreen(
    state: IllustiaUiState,
    services: AppServices,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    val actions =
        remember(viewModel) {
            object : AppDataActions {
                override fun clearAppCache() = viewModel.clearAppCache()

                override fun clearViewHistory() = viewModel.clearViewHistory()

                override fun clearSearchHistory() = viewModel.clearSearchHistory()

                override fun clearFavoriteTags() = viewModel.clearFavoriteTags()

                override fun clearMuteData() = viewModel.clearMuteData()
            }
        }
    SharedAppDataScreen(
        state = state,
        services = services,
        actions = actions,
        onBack = onBack,
    )
}
