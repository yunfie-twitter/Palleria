package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.BookmarkChromeState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.pixiv.WatchlistState
import com.yunfie.illustia.data.pixiv.WatchlistStore
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.models.pixiv.MangaSeriesModel
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.ui.components.AutoLoadMoreEffect
import com.yunfie.illustia.ui.components.AvatarImage
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.IllustCardSkeleton
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.LocalScrollHeaderInset
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PrefetchIllustGridImages
import com.yunfie.illustia.ui.components.ProfileGridHorizontalSpacing
import com.yunfie.illustia.ui.components.ProfileGridVerticalSpacing
import com.yunfie.illustia.ui.components.StateBanner
import com.yunfie.illustia.ui.components.UserResultCardSkeleton
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.adaptiveMainNavigationContentPadding
import com.yunfie.illustia.ui.components.adaptiveProfileGridColumns
import com.yunfie.illustia.ui.components.animatedGridPlacement
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import com.yunfie.illustia.ui.components.pinchToChangeColumns
import com.yunfie.illustia.ui.components.profileGridContentPadding
import com.yunfie.illustia.ui.components.rememberIllustSkeletonShimmer
import com.yunfie.illustia.ui.components.rememberSkeletonShimmer
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
internal fun BookmarkWatchlistTab(
    settings: AppSettings,
    watchlistState: WatchlistState,
    watchlistStore: WatchlistStore,
    onOpenWatchlistSeries: (Long) -> Unit,
    scrollBehavior: ScrollBehavior? = null,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val scope = rememberCoroutineScope()

    PullToRefresh(
        isRefreshing = watchlistState.isRefreshing,
        onRefresh = { scope.launch { watchlistStore.fetch(forceRefresh = true) } },
        modifier = Modifier.fillMaxSize(),
    ) {
        AutoLoadMoreEffect(
            gridState = gridState,
            enabled = settings.autoLoadMore,
            nextUrl = watchlistState.model?.nextUrl,
            isLoading = watchlistState.isPaginating || watchlistState.isLoading,
            onLoadMore = { scope.launch { watchlistStore.loadMore() } },
        )
        val shimmer = if (watchlistState.isLoading && watchlistState.mangaSeries.isEmpty()) rememberIllustSkeletonShimmer() else null
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(adaptiveProfileGridColumns()),
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier),
            contentPadding =
                profileGridContentPadding(
                    top = LocalScrollHeaderInset.current + 8.dp,
                    bottom = adaptiveMainNavigationContentPadding(),
                ),
            horizontalArrangement = Arrangement.spacedBy(ProfileGridHorizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(ProfileGridVerticalSpacing),
        ) {
            if (watchlistState.isLoading && watchlistState.mangaSeries.isEmpty()) {
                gridItems(List(6) { it }, key = { "watchlist_skeleton_$it" }, contentType = { "watchlist_series_skeleton" }) {
                    WatchlistSeriesCardSkeleton(shimmerValue = shimmer)
                }
            }
            if (watchlistState.errorMessage != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = watchlistState.errorMessage ?: "",
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }
            if (watchlistState.mangaSeries.isEmpty() && !watchlistState.isLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(stringResource(R.string.watchlist_series_empty)) }
            }
            gridItems(watchlistState.mangaSeries, key = { it.id }, contentType = { "watchlist_series_card" }) { series ->
                WatchlistSeriesCard(
                    series = series,
                    onClick = { onOpenWatchlistSeries(series.id) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (settings.autoLoadMore && watchlistState.isPaginating) {
                item(key = "watchlist_series_paginating_footer", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else if (!settings.autoLoadMore && watchlistState.model?.nextUrl != null) {
                item(key = "watchlist_series_load_more_button", span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = { scope.launch { watchlistStore.loadMore() } },
                        enabled = !watchlistState.isPaginating,
                        modifier = Modifier.fillMaxWidth(),
                        colors = overlayActionButtonColors(),
                    ) {
                        if (watchlistState.isPaginating) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LoadingIndicator(modifier = Modifier.size(16.dp))
                                Text(stringResource(R.string.watchlist_series_load_more))
                            }
                        } else {
                            Text(stringResource(R.string.watchlist_series_load_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistSeriesCard(
    series: MangaSeriesModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors =
            CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
            ),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.15f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                val thumbnailUrl = series.thumbnailUrl
                if (!thumbnailUrl.isNullOrBlank()) {
                    PixivImage(
                        url = thumbnailUrl,
                        contentDescription = series.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        thumbnail = true,
                        maxDecodeDimensionPx = 384,
                    )
                } else {
                    Icon(
                        imageVector = MiuixIcons.FavoritesFill,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                }
                if (series.publishedContentCount > 0) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "${series.publishedContentCount}隧ｱ",
                            color = MiuixTheme.colorScheme.onBackground,
                            style = MiuixTheme.textStyles.footnote2,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = series.title,
                    style = MiuixTheme.textStyles.subtitle,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                if (series.user != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AvatarImage(
                            url = series.user.profileImageUrls?.medium,
                            name = series.user.name,
                            size = 18.dp,
                        )
                        Text(
                            text = series.user.name,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.footnote1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistSeriesCardSkeleton(
    modifier: Modifier = Modifier,
    shimmerValue: State<Float>? = null,
) {
    val shimmerModifier = rememberSkeletonShimmer(shimmerValue)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .clip(RoundedCornerShape(18.dp))
                    .then(shimmerModifier),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.82f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .then(shimmerModifier),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.58f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .then(shimmerModifier),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.34f)
                        .height(9.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .then(shimmerModifier),
            )
        }
    }
}

@Composable
internal fun BookmarkMainTab(
    bookmarkItems: List<Illust>,
    loadState: LoadState,
    settings: AppSettings,
    feedHighQuality: Boolean,
    showAiBadge: Boolean,
    viewModel: IllustiaViewModel,
    chrome: BookmarkChromeState,
    scrollBehavior: ScrollBehavior,
) {
    val gridState = viewModel.bookmarkMainGridState
    PrefetchIllustGridImages(
        items = bookmarkItems,
        gridState = gridState,
        enabled = settings.prefetchImages,
        highQualityImages = feedHighQuality,
    )
    val showInitialSkeletons = bookmarkItems.isEmpty() && loadState == LoadState.Loading
    val showPaginationSkeletons = settings.autoLoadMore && chrome.isBookmarkPaginating
    val shimmer =
        if (showInitialSkeletons || showPaginationSkeletons) {
            rememberIllustSkeletonShimmer()
        } else {
            null
        }
    val columns = adaptiveIllustColumns(settings)
    PullToRefresh(
        isRefreshing = chrome.isBookmarkRefreshing,
        onRefresh = { viewModel.refreshBookmarks(forceRefresh = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        AutoLoadMoreEffect(
            gridState = gridState,
            enabled = settings.autoLoadMore,
            nextUrl = chrome.bookmarkNextUrl,
            isLoading = chrome.isBookmarkPaginating || loadState == LoadState.Loading,
            onLoadMore = viewModel::loadMoreBookmarks,
        )
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
                    start = 14.dp,
                    end = 14.dp,
                    top = LocalScrollHeaderInset.current + 8.dp,
                    bottom = adaptiveMainNavigationContentPadding(),
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showInitialSkeletons) {
                items(6, contentType = { "illust_skeleton" }) { IllustCardSkeleton(shimmerValue = shimmer) }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) { StateBanner(loadState) }
            }
            if (bookmarkItems.isEmpty() && loadState != LoadState.Loading && loadState !is LoadState.Error) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.bookmark_empty))
                }
            }
            gridItems(bookmarkItems, key = { it.id }, contentType = { "illust_card" }) { illust ->
                val illustId = illust.id
                val onBookmark = remember(illustId) { { viewModel.toggleBookmark(illustId) } }
                val onBookmarkLongClick =
                    remember(
                        illustId,
                    ) { { viewModel.toggleBookmark(illustId, restrict = com.yunfie.illustia.models.Restrict.Private) } }
                val onClick = remember(illustId) { { viewModel.openIllust(illustId) } }
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
                )
            }
            if (showPaginationSkeletons) {
                items(
                    count = columns,
                    key = { "bookmark_paginating_skeleton_$it" },
                    contentType = { "illust_skeleton" },
                ) {
                    IllustCardSkeleton(shimmerValue = shimmer)
                }
            } else if (!settings.autoLoadMore && chrome.bookmarkNextUrl != null) {
                item(key = "bookmark_load_more_button", span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = viewModel::loadMoreBookmarks,
                        enabled = !chrome.isBookmarkPaginating,
                        modifier = Modifier.fillMaxWidth(),
                        colors = overlayActionButtonColors(),
                    ) {
                        if (chrome.isBookmarkPaginating) {
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
internal fun BookmarkTimelineTab(
    timelineItems: List<Illust>,
    loadState: LoadState,
    settings: AppSettings,
    feedHighQuality: Boolean,
    showAiBadge: Boolean,
    viewModel: IllustiaViewModel,
    chrome: BookmarkChromeState,
    scrollBehavior: ScrollBehavior,
) {
    val gridState = viewModel.bookmarkTimelineGridState
    PrefetchIllustGridImages(
        items = timelineItems,
        gridState = gridState,
        enabled = settings.prefetchImages,
        highQualityImages = feedHighQuality,
        keyPrefix = "timeline_",
    )
    val showInitialSkeletons = timelineItems.isEmpty() && loadState == LoadState.Loading
    val showPaginationSkeletons = settings.autoLoadMore && chrome.isTimelinePaginating
    val shimmer =
        if (showInitialSkeletons || showPaginationSkeletons) {
            rememberIllustSkeletonShimmer()
        } else {
            null
        }
    val columns = adaptiveIllustColumns(settings)
    PullToRefresh(
        isRefreshing = chrome.isTimelineRefreshing,
        onRefresh = { viewModel.refreshTimeline(forceRefresh = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        AutoLoadMoreEffect(
            gridState = gridState,
            enabled = settings.autoLoadMore,
            nextUrl = chrome.timelineNextUrl,
            isLoading = chrome.isTimelinePaginating || loadState == LoadState.Loading,
            onLoadMore = viewModel::loadMoreTimeline,
        )
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
                    start = 14.dp,
                    end = 14.dp,
                    top = LocalScrollHeaderInset.current + 8.dp,
                    bottom = adaptiveMainNavigationContentPadding(),
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showInitialSkeletons) {
                items(6, contentType = { "illust_skeleton" }) { IllustCardSkeleton(shimmerValue = shimmer) }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) { StateBanner(loadState) }
            }
            if (timelineItems.isEmpty() && loadState != LoadState.Loading && loadState !is LoadState.Error) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.bookmark_following_empty))
                }
            }
            gridItems(timelineItems, key = { "timeline_${it.id}" }, contentType = { "illust_card" }) { illust ->
                val illustId = illust.id
                val onBookmark = remember(illustId) { { viewModel.toggleBookmark(illustId) } }
                val onBookmarkLongClick =
                    remember(
                        illustId,
                    ) { { viewModel.toggleBookmark(illustId, restrict = com.yunfie.illustia.models.Restrict.Private) } }
                val onClick = remember(illustId) { { viewModel.openIllust(illustId) } }
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
                )
            }
            if (showPaginationSkeletons) {
                items(
                    count = columns,
                    key = { "timeline_paginating_skeleton_$it" },
                    contentType = { "illust_skeleton" },
                ) {
                    IllustCardSkeleton(shimmerValue = shimmer)
                }
            } else if (!settings.autoLoadMore && chrome.timelineNextUrl != null) {
                item(key = "timeline_load_more_button", span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = viewModel::loadMoreTimeline,
                        enabled = !chrome.isTimelinePaginating,
                        modifier = Modifier.fillMaxWidth(),
                        colors = overlayActionButtonColors(),
                    ) {
                        if (chrome.isTimelinePaginating) {
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
internal fun BookmarkFollowingTab(
    settings: AppSettings,
    followingUsers: List<UserPreview>,
    sort: FollowingUserSort,
    loadState: LoadState,
    viewModel: IllustiaViewModel,
    chrome: BookmarkChromeState,
    scrollBehavior: ScrollBehavior? = null,
) {
    val gridState = viewModel.bookmarkFollowingGridState
    val sortedUsers =
        remember(followingUsers, sort) {
            when (sort) {
                FollowingUserSort.Newest -> {
                    followingUsers
                }

                FollowingUserSort.Oldest -> {
                    followingUsers.asReversed()
                }

                FollowingUserSort.Name -> {
                    followingUsers.sortedWith(
                        compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.account } },
                    )
                }
            }
        }
    val showInitialSkeletons = followingUsers.isEmpty() && loadState == LoadState.Loading
    val showPaginationSkeletons = settings.autoLoadMore && chrome.isFollowingPaginating
    val shimmer =
        if (showInitialSkeletons || showPaginationSkeletons) {
            rememberIllustSkeletonShimmer()
        } else {
            null
        }

    PullToRefresh(
        isRefreshing = chrome.isFollowingRefreshing,
        onRefresh = { viewModel.refreshFollowingUsers(forceRefresh = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        AutoLoadMoreEffect(
            gridState = gridState,
            enabled = settings.autoLoadMore,
            nextUrl = chrome.followingUsersNextUrl,
            isLoading = chrome.isFollowingPaginating || loadState == LoadState.Loading,
            onLoadMore = viewModel::loadMoreFollowingUsers,
        )
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(1),
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier),
            contentPadding =
                PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = LocalScrollHeaderInset.current + 8.dp,
                    bottom = adaptiveMainNavigationContentPadding(),
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showInitialSkeletons) {
                items(
                    count = 4,
                    key = { "following_user_skeleton_$it" },
                    contentType = { "user_skeleton" },
                ) {
                    UserResultCardSkeleton(shimmerValue = shimmer)
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) { StateBanner(loadState) }
            }
            if (followingUsers.isEmpty() && loadState != LoadState.Loading && loadState !is LoadState.Error) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.following_users_empty))
                }
            }
            gridItems(sortedUsers, key = { "follow_user_${it.id}" }, contentType = { "user_card" }) { user ->
                UserResultCard(user = user, onClick = { viewModel.openUserPage(user) })
            }
            if (showPaginationSkeletons) {
                items(
                    count = 3,
                    key = { "following_paginating_skeleton_$it" },
                    contentType = { "user_skeleton" },
                ) {
                    UserResultCardSkeleton(shimmerValue = shimmer)
                }
            } else if (!settings.autoLoadMore && chrome.followingUsersNextUrl != null) {
                item(key = "following_load_more_button", span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = viewModel::loadMoreFollowingUsers,
                        enabled = !chrome.isFollowingPaginating,
                        modifier = Modifier.fillMaxWidth(),
                        colors = overlayActionButtonColors(),
                    ) {
                        if (chrome.isFollowingPaginating) {
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
internal fun CompactBookmarkTabs(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    isBlurEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val colors =
        if (isBlurEnabled) {
            TabRowDefaults.tabRowColors(
                backgroundColor =
                    if (isDark) {
                        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f)
                    } else {
                        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
                    },
                contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                selectedBackgroundColor =
                    if (isDark) {
                        Color.White.copy(alpha = 0.18f)
                    } else {
                        Color.White.copy(alpha = 0.95f)
                    },
                selectedContentColor = MiuixTheme.colorScheme.onSurface,
            )
        } else {
            TabRowDefaults.tabRowColors(
                backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                selectedBackgroundColor =
                    if (isDark) {
                        Color.White.copy(alpha = 0.16f)
                    } else {
                        Color.White.copy(alpha = 0.92f)
                    },
                selectedContentColor = MiuixTheme.colorScheme.onSurface,
            )
        }
    val borderModifier =
        if (isBlurEnabled) {
            Modifier.squircleBorder(
                width = 0.75.dp,
                color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                cornerRadius = TabRowDefaults.TabRowWithContourCornerRadius,
            )
        } else {
            Modifier
        }
    TabRowWithContour(
        tabs =
            listOf(
                stringResource(R.string.bookmark_tab_timeline),
                stringResource(R.string.bookmark_tab_bookmarks),
                stringResource(R.string.bookmark_tab_watchlist),
                stringResource(R.string.bookmark_tab_following),
            ),
        selectedTabIndex = selectedTab,
        onTabSelected = onSelect,
        modifier = modifier.then(borderModifier).pointerHoverIcon(PointerIcon.Hand),
        colors = colors,
        minWidth = 86.dp,
        maxWidth = 116.dp,
    )
}
