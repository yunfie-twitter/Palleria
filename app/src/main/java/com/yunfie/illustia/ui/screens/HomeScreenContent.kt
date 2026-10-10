package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.isMutedByTags
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.ui.components.AutoLoadMoreEffect
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.IllustCardSkeleton
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.LocalScrollHeaderInset
import com.yunfie.illustia.ui.components.OfflineCachedChip
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PrefetchIllustGridImages
import com.yunfie.illustia.ui.components.StateBanner
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.adaptiveMainNavigationContentPadding
import com.yunfie.illustia.ui.components.animatedGridPlacement
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import com.yunfie.illustia.ui.components.pinchToChangeColumns
import com.yunfie.illustia.ui.components.rememberIllustSkeletonShimmer
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
internal fun HomeAccountAvatar(account: UserProfile?) {
    Box(
        modifier =
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (account != null && !account.profileImageUrl.isNullOrBlank()) {
            PixivImage(
                url = account.profileImageUrl,
                contentDescription = account.name,
                contentScale = ContentScale.Crop,
                thumbnail = true,
                maxDecodeDimensionPx = 128,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = MiuixIcons.Contacts,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
    isRefreshing: Boolean = false,
    isPaginating: Boolean = false,
    isOfflineCached: Boolean = false,
    pendingHomeItemsCount: Int = 0,
    scrollBehavior: ScrollBehavior = MiuixScrollBehavior(),
) {
    val feedHighQuality = settings.useHighQualityFeedImages
    val showAiBadge = remember(settings.showAiBadge) { settings.showAiBadge }
    val mutedTagsSet = remember(settings.mutedTags) { settings.mutedTags.toHashSet() }
    val gridState = viewModel.homeFeedGridState
    PrefetchIllustGridImages(
        items = items,
        gridState = gridState,
        enabled = settings.prefetchImages,
        highQualityImages = feedHighQuality,
        limit = 6,
    )
    val showInitialSkeletons = items.isEmpty() && loadState == LoadState.Loading
    val shimmer = if (showInitialSkeletons) rememberIllustSkeletonShimmer() else null
    AutoLoadMoreEffect(
        gridState = gridState,
        enabled = settings.autoLoadMore,
        nextUrl = nextUrl,
        isLoading = isPaginating || loadState == LoadState.Loading,
        onLoadMore = viewModel::loadMoreHome,
    )

    val columns = adaptiveIllustColumns(settings)
    val scope = rememberCoroutineScope()
    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshHome(forceRefresh = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        val pinchEnabled = settings.gridPinchToZoom
        Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier =
                Modifier
                    .fillMaxSize()
                    .pinchToChangeColumns(
                        enabled = pinchEnabled,
                        currentColumns = columns,
                        onColumnsChange = viewModel::updateVerticalColumnCount,
                    ).nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding =
                PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = LocalScrollHeaderInset.current + 12.dp,
                    bottom = adaptiveMainNavigationContentPadding(),
                ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showInitialSkeletons) {
                items(6, key = { "home_feed_skeleton_$it" }, contentType = { "illust_skeleton" }) {
                    IllustCardSkeleton(shimmerValue = shimmer)
                }
            }

            val isStaleCacheActive =
                settings.isFeatureEnabled(FeatureFlag.OfflineStaleCache) &&
                    items.isNotEmpty() &&
                    (loadState is LoadState.Error || isOfflineCached)

            if (isStaleCacheActive) {
                item(key = "home_feed_offline_chip", span = { GridItemSpan(maxLineSpan) }) {
                    OfflineCachedChip(
                        onRetry = { viewModel.refreshHome(forceRefresh = true) },
                    )
                }
            } else if (loadState is LoadState.Error) {
                item(key = "home_feed_error_banner", span = { GridItemSpan(maxLineSpan) }) {
                    StateBanner(loadState, onRetry = { viewModel.refreshHome(forceRefresh = true) })
                }
            }

            if (items.isEmpty() && loadState != LoadState.Loading && loadState !is LoadState.Error) {
                item(key = "home_feed_empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.home_feed_loading))
                }
            }

            gridItems(items, key = { it.id }, contentType = { "illust_card" }) { illust ->
                val illustId = illust.id
                val onBookmark = remember(illust) { { viewModel.toggleBookmark(illust) } }
                val onBookmarkLongClick =
                    remember(illust) { { viewModel.toggleBookmark(illust, com.yunfie.illustia.models.Restrict.Private) } }
                val onClick = remember(illust) { { viewModel.openIllust(illust) } }
                val onLongClick = remember(illustId) { { viewModel.onIllustLongPress(illustId) } }

                IllustCard(
                    modifier = animatedGridPlacement(),
                    illust = illust,
                    onBookmark = onBookmark,
                    onBookmarkLongClick = onBookmarkLongClick,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    highQualityImages = feedHighQuality,
                    showAiBadge = showAiBadge,
                    isMutedByTag = illust.isMutedByTags(mutedTagsSet),
                )
            }

            if (settings.autoLoadMore && nextUrl != null) {
                item(key = "home_paginating_indicator", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = if (isPaginating) 16.dp else 0.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isPaginating) {
                            LoadingIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            } else if (!settings.autoLoadMore && nextUrl != null) {
                item(key = "home_load_more_button", span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = viewModel::loadMoreHome,
                        enabled = !isPaginating,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        colors = overlayActionButtonColors(),
                    ) {
                        if (isPaginating) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LoadingIndicator(modifier = Modifier.size(16.dp))
                                Text(stringResource(R.string.action_load_more))
                            }
                        } else {
                            Text(stringResource(R.string.action_load_more))
                        }
                    }
                }
            }
        }
            if (pendingHomeItemsCount > 0) {
                Button(
                    onClick = {
                        viewModel.applyPendingHomeItems()
                        scope.launch { gridState.animateScrollToItem(0) }
                    },
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                    colors = overlayActionButtonColors(),
                ) {
                    Text(stringResource(R.string.home_new_items_available, pendingHomeItemsCount))
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
    isRefreshing: Boolean = false,
    isPaginating: Boolean = false,
    isOfflineCached: Boolean = false,
    scrollBehavior: ScrollBehavior = MiuixScrollBehavior(),
) {
    val feedHighQuality = settings.useHighQualityFeedImages
    val showAiBadge = remember(settings.showAiBadge) { settings.showAiBadge }
    val mutedTagsSet = remember(settings.mutedTags) { settings.mutedTags.toHashSet() }
    val gridState = viewModel.homeTimelineGridState
    PrefetchIllustGridImages(
        items = items,
        gridState = gridState,
        enabled = settings.prefetchImages,
        highQualityImages = feedHighQuality,
        keyPrefix = "tl_",
        limit = 6,
    )
    val showInitialSkeletons = items.isEmpty() && loadState == LoadState.Loading
    val shimmer = if (showInitialSkeletons) rememberIllustSkeletonShimmer() else null
    AutoLoadMoreEffect(
        gridState = gridState,
        enabled = settings.autoLoadMore,
        nextUrl = nextUrl,
        isLoading = isPaginating || loadState == LoadState.Loading,
        onLoadMore = viewModel::loadMoreTimeline,
    )

    val columns = adaptiveIllustColumns(settings)
    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshTimeline(forceRefresh = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        val pinchEnabled = settings.gridPinchToZoom
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier =
                Modifier
                    .fillMaxSize()
                    .pinchToChangeColumns(
                        enabled = pinchEnabled,
                        currentColumns = columns,
                        onColumnsChange = viewModel::updateVerticalColumnCount,
                    ).nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding =
                PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = LocalScrollHeaderInset.current + 12.dp,
                    bottom = adaptiveMainNavigationContentPadding(),
                ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showInitialSkeletons) {
                items(6, key = { "home_following_skeleton_$it" }, contentType = { "illust_skeleton" }) {
                    IllustCardSkeleton(shimmerValue = shimmer)
                }
            }

            val isStaleCacheActive =
                settings.isFeatureEnabled(FeatureFlag.OfflineStaleCache) &&
                    items.isNotEmpty() &&
                    (loadState is LoadState.Error || isOfflineCached)

            if (isStaleCacheActive) {
                item(key = "home_following_offline_chip", span = { GridItemSpan(maxLineSpan) }) {
                    OfflineCachedChip(
                        onRetry = { viewModel.refreshTimeline(forceRefresh = true) },
                    )
                }
            } else if (loadState is LoadState.Error) {
                item(key = "home_following_error_banner", span = { GridItemSpan(maxLineSpan) }) {
                    StateBanner(loadState, onRetry = { viewModel.refreshTimeline(forceRefresh = true) })
                }
            }

            if (items.isEmpty() && loadState != LoadState.Loading && loadState !is LoadState.Error) {
                item(key = "home_following_empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.home_following_empty))
                }
            }

            gridItems(items, key = { "tl_${it.id}" }, contentType = { "illust_card" }) { illust ->
                val illustId = illust.id
                val onBookmark = remember(illust) { { viewModel.toggleBookmark(illust) } }
                val onBookmarkLongClick =
                    remember(illust) { { viewModel.toggleBookmark(illust, com.yunfie.illustia.models.Restrict.Private) } }
                val onClick = remember(illust) { { viewModel.openIllust(illust) } }
                val onLongClick = remember(illustId) { { viewModel.onIllustLongPress(illustId) } }

                IllustCard(
                    modifier = animatedGridPlacement(),
                    illust = illust,
                    onBookmark = onBookmark,
                    onBookmarkLongClick = onBookmarkLongClick,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    highQualityImages = feedHighQuality,
                    showAiBadge = showAiBadge,
                    isMutedByTag = illust.isMutedByTags(mutedTagsSet),
                )
            }

            if (settings.autoLoadMore && nextUrl != null) {
                item(key = "timeline_paginating_indicator", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = if (isPaginating) 16.dp else 0.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isPaginating) {
                            LoadingIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            } else if (!settings.autoLoadMore && nextUrl != null) {
                item(key = "timeline_load_more_button", span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = viewModel::loadMoreTimeline,
                        enabled = !isPaginating,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        colors = overlayActionButtonColors(),
                    ) {
                        if (isPaginating) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LoadingIndicator(modifier = Modifier.size(16.dp))
                                Text(stringResource(R.string.action_load_more))
                            }
                        } else {
                            Text(stringResource(R.string.action_load_more))
                        }
                    }
                }
            }
        }
    }
}
