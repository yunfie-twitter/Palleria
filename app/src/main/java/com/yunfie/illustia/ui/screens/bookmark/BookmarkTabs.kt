package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.yunfie.illustia.ui.components.AutoLoadMoreEffect
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.IllustCardSkeleton
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.MainNavigationContentPadding
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.StateBanner
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.miuixClickable
import com.yunfie.illustia.ui.components.overlayActionButtonColors
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
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BookmarkWatchlistTab(
    settings: AppSettings,
    watchlistState: WatchlistState,
    watchlistStore: WatchlistStore,
    onOpenWatchlistSeries: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    PullToRefresh(
        isRefreshing = watchlistState.isLoading && watchlistState.mangaSeries.isNotEmpty(),
        onRefresh = { scope.launch { watchlistStore.fetch() } },
        modifier = Modifier.fillMaxSize(),
    ) {
        AutoLoadMoreEffect(
            enabled = settings.autoLoadMore,
            nextUrl = watchlistState.model?.nextUrl,
            isLoading = watchlistState.isLoading,
            onLoadMore = { scope.launch { watchlistStore.loadMore() } },
        )
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (watchlistState.isLoading && watchlistState.mangaSeries.isEmpty()) {
                gridItems(List(6) { it }, contentType = { "watchlist_series_skeleton" }) {
                    WatchlistSeriesCardSkeleton()
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
                )
            }
            if (!settings.autoLoadMore && watchlistState.model?.nextUrl != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = { scope.launch { watchlistStore.loadMore() } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = overlayActionButtonColors(),
                    ) {
                        Text(stringResource(R.string.watchlist_series_load_more))
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
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = Color.Transparent,
            contentColor = MiuixTheme.colorScheme.onBackground,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .clip(RoundedCornerShape(18.dp))
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
                    )
                } else {
                    Icon(
                        imageVector = MiuixIcons.FavoritesFill,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${series.publishedContentCount}P",
                        color = MiuixTheme.colorScheme.onBackground,
                        style = MiuixTheme.textStyles.footnote2,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = series.title,
                    style = MiuixTheme.textStyles.subtitle,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = series.user?.name?.takeIf { it.isNotBlank() } ?: "@${series.user?.account.orEmpty()}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "ID ${series.id}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote2,
                )
            }
        }
    }
}

@Composable
private fun WatchlistSeriesCardSkeleton() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "watchlistSeriesSkeleton")
    val shimmer by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 1250,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "watchlistSeriesSkeletonShimmer",
    )
    val base = MiuixTheme.colorScheme.surfaceContainer
    val highlight = MiuixTheme.colorScheme.surfaceContainerHigh
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(shimmer * 500f, 0f),
        end = Offset(shimmer * 500f + 260f, 500f),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(18.dp))
                .background(shimmerBrush),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(shimmerBrush),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(shimmerBrush),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.34f)
                    .height(9.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(shimmerBrush),
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
    PullToRefresh(
        isRefreshing = loadState == LoadState.Loading && bookmarkItems.isNotEmpty(),
        onRefresh = { viewModel.refreshBookmarks() },
        modifier = Modifier.fillMaxSize(),
    ) {
        AutoLoadMoreEffect(
            enabled = settings.autoLoadMore,
            nextUrl = chrome.bookmarkNextUrl,
            isLoading = loadState == LoadState.Loading,
            onLoadMore = viewModel::loadMoreBookmarks,
        )
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (bookmarkItems.isEmpty() && loadState == LoadState.Loading) {
                items(6, contentType = { "illust_skeleton" }) { IllustCardSkeleton() }
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
                val onClick = remember(illustId) { { viewModel.openIllust(illustId) } }
                val onLongClick = remember(illustId) { { viewModel.onIllustLongPress(illustId) } }
                IllustCard(illust = illust, onBookmark = onBookmark, onClick = onClick, onLongClick = onLongClick, highQualityImages = feedHighQuality, showAiBadge = showAiBadge)
            }
            if (!settings.autoLoadMore && chrome.bookmarkNextUrl != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(onClick = viewModel::loadMoreBookmarks, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_load_more)) }
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
    PullToRefresh(
        isRefreshing = loadState == LoadState.Loading && timelineItems.isNotEmpty(),
        onRefresh = { viewModel.refreshTimeline() },
        modifier = Modifier.fillMaxSize(),
    ) {
        AutoLoadMoreEffect(
            enabled = settings.autoLoadMore,
            nextUrl = chrome.timelineNextUrl,
            isLoading = loadState == LoadState.Loading,
            onLoadMore = viewModel::loadMoreTimeline,
        )
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (timelineItems.isEmpty() && loadState == LoadState.Loading) {
                items(6, contentType = { "illust_skeleton" }) { IllustCardSkeleton() }
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
                val onClick = remember(illustId) { { viewModel.openIllust(illustId) } }
                val onLongClick = remember(illustId) { { viewModel.onIllustLongPress(illustId) } }
                IllustCard(illust = illust, onBookmark = onBookmark, onClick = onClick, onLongClick = onLongClick, highQualityImages = feedHighQuality, showAiBadge = showAiBadge)
            }
            if (!settings.autoLoadMore && chrome.timelineNextUrl != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(onClick = viewModel::loadMoreTimeline, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_load_more)) }
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
) {
    val gridState = viewModel.bookmarkFollowingGridState
    val sortedUsers = remember(followingUsers, sort) {
        when (sort) {
            FollowingUserSort.Newest -> followingUsers
            FollowingUserSort.Oldest -> followingUsers.asReversed()
            FollowingUserSort.Name -> followingUsers.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.account } },
            )
        }
    }
    PullToRefresh(
        isRefreshing = loadState == LoadState.Loading && followingUsers.isNotEmpty(),
        onRefresh = { viewModel.refreshFollowingUsers() },
        modifier = Modifier.fillMaxSize(),
    ) {
        AutoLoadMoreEffect(
            enabled = settings.autoLoadMore,
            nextUrl = chrome.followingUsersNextUrl,
            isLoading = loadState == LoadState.Loading,
            onLoadMore = viewModel::loadMoreFollowingUsers,
        )
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = MainNavigationContentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { StateBanner(loadState) }
            if (followingUsers.isEmpty() && loadState != LoadState.Loading && loadState !is LoadState.Error) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.following_users_empty))
                }
            }
            gridItems(sortedUsers, key = { "follow_user_${it.id}" }, contentType = { "user_card" }) { user ->
                UserResultCard(user = user, onClick = { viewModel.openUserPage(user) })
            }
            if (!settings.autoLoadMore && chrome.followingUsersNextUrl != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(onClick = viewModel::loadMoreFollowingUsers, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_load_more)) }
                }
            }
        }
    }
}

@Composable
internal fun CompactBookmarkTabs(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    TabRowWithContour(
        tabs = listOf(
            stringResource(R.string.bookmark_tab_timeline),
            stringResource(R.string.bookmark_tab_bookmarks),
            stringResource(R.string.bookmark_tab_watchlist),
            stringResource(R.string.bookmark_tab_following),
        ),
        selectedTabIndex = selectedTab,
        onTabSelected = onSelect,
        modifier = modifier,
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = scheme.surfaceContainer.copy(alpha = 0.44f),
            contentColor = scheme.onSurfaceVariantSummary,
            selectedBackgroundColor = scheme.surfaceContainerHigh,
            selectedContentColor = scheme.onBackground,
        ),
        minWidth = 86.dp,
        maxWidth = 116.dp,
        height = 45.dp,
    )
}

@Composable
internal fun RestrictPill(
    restrict: Restrict,
    onClick: () -> Unit,
) {
    val icon = if (restrict == Restrict.Public) MiuixIcons.Community else MiuixIcons.Lock
    Box(
        modifier = Modifier
            .height(32.dp)
            .squircleSurface(MiuixTheme.colorScheme.surfaceContainer, 16.dp)
            .miuixClickable(haptic = true, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MiuixTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(restrict.labelResId),
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
