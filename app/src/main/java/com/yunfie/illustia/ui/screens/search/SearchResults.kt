package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.isMutedByTags
import com.yunfie.illustia.ui.components.AvatarImage
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.AutoLoadMoreEffect
import com.yunfie.illustia.ui.components.FollowPill
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.MainNavigationContentPadding
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PrefetchPixivImages
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
internal fun SearchResultGrid(
    page: Int,
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onIllustSelected: ((Illust) -> Unit)? = null,
) {
    val feedHighQuality = remember(state.settings.highQualityImages, state.settings.feedPreviewQuality) {
        state.settings.highQualityImages && state.settings.feedPreviewQuality != "low"
    }
    val showAiBadge = remember(state.settings.showAiBadge) { state.settings.showAiBadge }
    val prefetchUrls = remember(page, state.searchItems, feedHighQuality) {
        if (page == 0) {
            state.searchItems.asSequence()
                .take(16)
                .map { if (feedHighQuality) it.previewUrl else it.thumbnailUrl }
                .toList()
        } else {
            emptyList()
        }
    }
    PrefetchPixivImages(prefetchUrls, enabled = state.settings.prefetchImages)
    AutoLoadMoreEffect(
        enabled = state.settings.autoLoadMore,
        nextUrl = if (page == 0) state.searchNextUrl else state.userSearchNextUrl,
        isLoading = state.loadState == LoadState.Loading,
        onLoadMore = if (page == 0) viewModel::loadMoreSearch else viewModel::loadMoreUserSearch,
    )

    val gridState = viewModel.searchResultGridState
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(if (page == 0) adaptiveIllustColumns(state.settings) else 1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = MainNavigationContentPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (page == 0) {
            gridItems(state.searchItems, key = { it.id }, contentType = { "illust_card" }) { illust ->
                IllustCard(
                    illust = illust,
                    onBookmark = { viewModel.toggleBookmark(illust) },
                    onClick = { onIllustSelected?.invoke(illust) ?: viewModel.openIllust(illust) },
                    highQualityImages = feedHighQuality,
                    showAiBadge = showAiBadge,
                    isMutedByTag = illust.isMutedByTags(state.settings),
                )
            }
            if (!state.settings.autoLoadMore && state.searchNextUrl != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = viewModel::loadMoreSearch,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = overlayActionButtonColors(),
                    ) {
                        Text(stringResource(R.string.action_load_more))
                    }
                }
            }
            if (state.searchItems.isEmpty() && state.loadState != LoadState.Loading && state.loadState !is LoadState.Error) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.search_empty_illust))
                }
            }
        } else {
            gridItems(state.userSearchItems, key = { it.id }, contentType = { "user_card" }) { user ->
                UserResultCard(user = user, onClick = { viewModel.openUserPage(user) })
            }
            if (state.userSearchItems.isEmpty() && state.loadState != LoadState.Loading && state.loadState !is LoadState.Error) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.search_empty_user))
                }
            }
            if (!state.settings.autoLoadMore && state.userSearchNextUrl != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = viewModel::loadMoreUserSearch,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = overlayActionButtonColors(),
                    ) {
                        Text(stringResource(R.string.action_load_more))
                    }
                }
            }
        }
    }
}

@Composable
internal fun UserResultCard(user: UserPreview, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (user.previewIllusts.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    user.previewIllusts.take(3).forEach { illust ->
                        PixivImage(
                            url = illust.squareImageUrl.ifBlank { illust.mediumImageUrl.ifBlank { illust.imageUrl } },
                            contentDescription = illust.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).height(118.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AvatarImage(url = user.profileImageUrl, name = user.name, size = 62.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(user.name, color = MiuixTheme.colorScheme.onBackground, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("@${user.account}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FollowPill(isFollowed = user.isFollowed)
            }
        }
    }
}
