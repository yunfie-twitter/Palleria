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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PrefetchPixivImages
import com.yunfie.illustia.ui.components.StateBanner
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
    scrollBehavior: ScrollBehavior = MiuixScrollBehavior(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val feedHighQuality = settings.useHighQualityFeedImages
    val showAiBadge = remember(settings.showAiBadge) { settings.showAiBadge }
    val gridState = viewModel.homeFeedGridState
    val prefetchUrls =
        remember(items, feedHighQuality) {
            items
                .asSequence()
                .take(8)
                .map { if (feedHighQuality) it.previewUrl else it.thumbnailUrl }
                .toList()
        }
    PrefetchPixivImages(prefetchUrls, enabled = settings.prefetchImages)
    AutoLoadMoreEffect(
        gridState = gridState,
        enabled = settings.autoLoadMore,
        nextUrl = nextUrl,
        isLoading = state.isHomePaginating || loadState == LoadState.Loading,
        buffer = 6,
        onLoadMore = viewModel::loadMoreHome,
    )

    PullToRefresh(
        isRefreshing = state.isHomeRefreshing,
        onRefresh = { viewModel.refreshHome(forceRefresh = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .background(MiuixTheme.colorScheme.surface),
            contentPadding =
                PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 12.dp,
                    bottom = 28.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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

            if (settings.autoLoadMore && state.isHomePaginating) {
                item(key = "home_paginating_footer", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else if (!settings.autoLoadMore && nextUrl != null) {
                item(key = "home_load_more_button", span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = viewModel::loadMoreHome,
                        enabled = !state.isHomePaginating,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        colors = overlayActionButtonColors(),
                    ) {
                        if (state.isHomePaginating) {
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

@Composable
internal fun FollowingTabContent(
    items: List<Illust>,
    loadState: LoadState,
    nextUrl: String?,
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    scrollBehavior: ScrollBehavior = MiuixScrollBehavior(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val feedHighQuality = settings.useHighQualityFeedImages
    val showAiBadge = remember(settings.showAiBadge) { settings.showAiBadge }
    val gridState = viewModel.homeTimelineGridState
    val prefetchUrls =
        remember(items, feedHighQuality) {
            items
                .asSequence()
                .take(8)
                .map { if (feedHighQuality) it.previewUrl else it.thumbnailUrl }
                .toList()
        }
    PrefetchPixivImages(prefetchUrls, enabled = settings.prefetchImages)
    AutoLoadMoreEffect(
        gridState = gridState,
        enabled = settings.autoLoadMore,
        nextUrl = nextUrl,
        isLoading = state.isTimelinePaginating || loadState == LoadState.Loading,
        buffer = 6,
        onLoadMore = viewModel::loadMoreTimeline,
    )

    PullToRefresh(
        isRefreshing = state.isTimelineRefreshing,
        onRefresh = { viewModel.refreshTimeline(forceRefresh = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .background(MiuixTheme.colorScheme.surface),
            contentPadding =
                PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 12.dp,
                    bottom = 28.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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

            if (settings.autoLoadMore && state.isTimelinePaginating) {
                item(key = "timeline_paginating_footer", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else if (!settings.autoLoadMore && nextUrl != null) {
                item(key = "timeline_load_more_button", span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = viewModel::loadMoreTimeline,
                        enabled = !state.isTimelinePaginating,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        colors = overlayActionButtonColors(),
                    ) {
                        if (state.isTimelinePaginating) {
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
