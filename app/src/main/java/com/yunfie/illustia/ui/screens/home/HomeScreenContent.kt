package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.AutoLoadMoreEffect
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.IllustCardSkeleton
import com.yunfie.illustia.ui.components.PrefetchPixivImages
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.StateBanner
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HomeAccountAvatar(account: UserProfile?) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val avatarUrl = account?.profileImageUrl
        if (!avatarUrl.isNullOrBlank()) {
            PixivImage(
                url = avatarUrl,
                contentDescription = account.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                thumbnail = true,
            )
        } else {
            Icon(
                imageVector = MiuixIcons.Contacts,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun FeedTabContent(
    items: List<Illust>,
    loadState: LoadState,
    nextUrl: String?,
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    scrollBehavior: ScrollBehavior = MiuixScrollBehavior(),
) {
    val feedHighQuality = remember(settings.highQualityImages, settings.feedPreviewQuality) {
        settings.highQualityImages && settings.feedPreviewQuality != "low"
    }
    val showAiBadge = remember(settings.showAiBadge) { settings.showAiBadge }
    val gridState = viewModel.homeFeedGridState
    val prefetchUrls = remember(items, feedHighQuality) {
        items.asSequence()
            .take(16)
            .map { if (feedHighQuality) it.previewUrl else it.thumbnailUrl }
            .toList()
    }
    PrefetchPixivImages(prefetchUrls, enabled = settings.prefetchImages)
    AutoLoadMoreEffect(
        enabled = settings.autoLoadMore,
        nextUrl = nextUrl,
        isLoading = loadState == LoadState.Loading,
        onLoadMore = viewModel::loadMoreHome,
    )

    PullToRefresh(
        isRefreshing = loadState == LoadState.Loading && items.isNotEmpty(),
        onRefresh = { viewModel.refreshHome() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .background(MiuixTheme.colorScheme.surface),
            contentPadding = PaddingValues(
                start = 14.dp, end = 14.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (items.isEmpty() && loadState == LoadState.Loading) {
                items(6, contentType = { "illust_skeleton" }) { IllustCardSkeleton() }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) { StateBanner(loadState) }
            }

            if (items.isEmpty() && loadState == LoadState.Idle) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.home_feed_loading))
                }
            }

            gridItems(items, key = { it.id }, contentType = { "illust_card" }) { illust ->
                val illustId = illust.id
                val onBookmark = remember(illustId) { { viewModel.toggleBookmark(illustId) } }
                val onClick = remember(illustId) { { viewModel.openIllust(illustId) } }
                val onLongClick = remember(illustId) { { viewModel.onIllustLongPress(illustId) } }

                IllustCard(
                    illust = illust,
                    onBookmark = onBookmark,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    highQualityImages = feedHighQuality,
                    showAiBadge = showAiBadge,
                )
            }

            if (!settings.autoLoadMore && nextUrl != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = viewModel::loadMoreHome,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.action_load_more))
                    }
                }
            }
        }
    }
}

@Composable
internal fun FollowingTabContent(
    items: List<Illust>,
    loadState: LoadState,
    nextUrl: String?,
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    scrollBehavior: ScrollBehavior = MiuixScrollBehavior(),
) {
    val feedHighQuality = remember(settings.highQualityImages, settings.feedPreviewQuality) {
        settings.highQualityImages && settings.feedPreviewQuality != "low"
    }
    val showAiBadge = remember(settings.showAiBadge) { settings.showAiBadge }
    val gridState = viewModel.homeTimelineGridState
    val prefetchUrls = remember(items, feedHighQuality) {
        items.asSequence()
            .take(16)
            .map { if (feedHighQuality) it.previewUrl else it.thumbnailUrl }
            .toList()
    }
    PrefetchPixivImages(prefetchUrls, enabled = settings.prefetchImages)
    AutoLoadMoreEffect(
        enabled = settings.autoLoadMore,
        nextUrl = nextUrl,
        isLoading = loadState == LoadState.Loading,
        onLoadMore = viewModel::loadMoreTimeline,
    )

    PullToRefresh(
        isRefreshing = loadState == LoadState.Loading && items.isNotEmpty(),
        onRefresh = { viewModel.refreshTimeline() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .background(MiuixTheme.colorScheme.surface),
            contentPadding = PaddingValues(
                start = 14.dp, end = 14.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (items.isEmpty() && loadState == LoadState.Loading) {
                items(6, contentType = { "illust_skeleton" }) { IllustCardSkeleton() }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) { StateBanner(loadState) }
            }

            if (items.isEmpty() && loadState != LoadState.Loading && loadState !is LoadState.Error) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.home_following_empty))
                }
            }

            gridItems(items, key = { "tl_${it.id}" }, contentType = { "illust_card" }) { illust ->
                val illustId = illust.id
                val onBookmark = remember(illustId) { { viewModel.toggleBookmark(illustId) } }
                val onClick = remember(illustId) { { viewModel.openIllust(illustId) } }
                val onLongClick = remember(illustId) { { viewModel.onIllustLongPress(illustId) } }

                IllustCard(
                    illust = illust,
                    onBookmark = onBookmark,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    highQualityImages = feedHighQuality,
                    showAiBadge = showAiBadge,
                )
            }

            if (!settings.autoLoadMore && nextUrl != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = viewModel::loadMoreTimeline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.action_load_more))
                    }
                }
            }
        }
    }
}
