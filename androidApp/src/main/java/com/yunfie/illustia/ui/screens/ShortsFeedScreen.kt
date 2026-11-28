@file:JvmName("AndroidShortsFeedScreen")

package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.ui.screens.ShortsFeedScreen as SharedShortsFeedScreen

@Composable
fun ShortsFeedScreen(
    items: List<Illust>,
    loadState: LoadState,
    currentIllustId: Long?,
    viewModel: IllustiaViewModel,
    onOpenComments: (Long) -> Unit,
) {
    val actions =
        remember(viewModel) {
            object : ShortsFeedActions {
                override fun refreshShortsFeed(forceRefresh: Boolean) = viewModel.refreshShortsFeed(forceRefresh)

                override fun loadMoreShortsFeed() = viewModel.loadMoreShortsFeed()

                override fun updateShortsFeedCurrentIllust(illustId: Long) = viewModel.updateShortsFeedCurrentIllust(illustId)

                override fun openIllust(illust: Illust) = viewModel.openIllust(illust)

                override fun onIllustLongPress(illust: Illust) = viewModel.onIllustLongPress(illust)

                override fun toggleBookmark(illust: Illust) = viewModel.toggleBookmark(illust)
            }
        }
    SharedShortsFeedScreen(
        items = items,
        loadState = loadState,
        currentIllustId = currentIllustId,
        actions = actions,
        onOpenComments = onOpenComments,
    )
}
