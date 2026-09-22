@file:Suppress("MatchingDeclarationName")

package com.yunfie.illustia.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.pixiv.WatchlistStore
import com.yunfie.illustia.models.pixiv.MangaSeriesModel
import com.yunfie.illustia.ui.components.AutoLoadMoreEffect
import com.yunfie.illustia.ui.components.AvatarImage
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.HeaderOverlayIcon
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.ProfileGridHorizontalSpacing
import com.yunfie.illustia.ui.components.ProfileGridVerticalSpacing
import com.yunfie.illustia.ui.components.adaptiveMainNavigationContentPadding
import com.yunfie.illustia.ui.components.adaptiveProfileGridColumns
import com.yunfie.illustia.ui.components.miuixClickable
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import com.yunfie.illustia.ui.components.profileGridContentPadding
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import androidx.compose.foundation.lazy.grid.items as gridItems

@Suppress("MatchingDeclarationName")
enum class WatchlistSortOrder {
    Newest,
    Oldest,
    MostEpisodes,
}

@Composable
fun WatchlistSeriesScreen(
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val context = LocalContext.current
    val activity = context as? Activity
    val repository = remember(viewModel) { viewModel.uiRepository() }
    val store = remember(repository) { WatchlistStore(repository) }
    val state by store.state.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    var sortOrder by remember { mutableStateOf(WatchlistSortOrder.Newest) }
    var isHeaderCollapsed by remember { mutableStateOf(false) }

    val processedSeries =
        remember(state.mangaSeries, sortOrder) {
            when (sortOrder) {
                WatchlistSortOrder.Newest -> state.mangaSeries.sortedByDescending { it.id }
                WatchlistSortOrder.Oldest -> state.mangaSeries.sortedBy { it.id }
                WatchlistSortOrder.MostEpisodes -> state.mangaSeries.sortedByDescending { it.publishedContentCount }
            }
        }

    val isAtTop by remember(gridState) {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset <= 0
        }
    }

    LaunchedEffect(isAtTop) {
        if (isAtTop) {
            isHeaderCollapsed = false
        }
    }

    val watchlistScrollConnection =
        remember(gridState) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y < -12f && (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 32)) {
                        isHeaderCollapsed = true
                    } else if (available.y > 8f && gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset <= 12) {
                        isHeaderCollapsed = false
                    }
                    return Offset.Zero
                }
            }
        }

    val isContentScrolled by remember(gridState, isHeaderCollapsed, isAtTop) {
        derivedStateOf {
            !isAtTop && (isHeaderCollapsed || gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 24)
        }
    }

    val backgroundColor = MiuixTheme.colorScheme.background
    val isDarkTheme = backgroundColor.luminance() < 0.5f

    DisposableEffect(isContentScrolled, isDarkTheme) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = if (isContentScrolled) !isDarkTheme else false
        }
        onDispose {
            val window = activity?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDarkTheme
            }
        }
    }

    val bannerCoverUrl = processedSeries.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl

    LaunchedEffect(store) {
        store.fetch()
    }

    val scrollToTop: () -> Unit = {
        isHeaderCollapsed = false
        scope.launch { gridState.animateScrollToItem(0) }
    }

    val pullToRefreshState = rememberPullToRefreshState()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(watchlistScrollConnection)
                .background(backgroundColor),
    ) {
        PullToRefresh(
            isRefreshing = state.isRefreshing,
            onRefresh = { scope.launch { store.fetch() } },
            pullToRefreshState = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
        ) {
            AutoLoadMoreEffect(
                gridState = gridState,
                enabled = settings.autoLoadMore,
                nextUrl = state.model?.nextUrl,
                isLoading = state.isLoading || state.isPaginating,
                onLoadMore = { scope.launch { store.loadMore() } },
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Transparent),
            ) {
                AnimatedVisibility(
                    visible = !isContentScrolled,
                    enter =
                        expandVertically(
                            animationSpec = tween(320),
                            expandFrom = Alignment.Top,
                        ) + fadeIn(animationSpec = tween(220, delayMillis = 60)),
                    exit =
                        shrinkVertically(
                            animationSpec = tween(280),
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(animationSpec = tween(180)),
                ) {
                    WatchlistHeader(
                        bannerUrl = bannerCoverUrl,
                        seriesCount = processedSeries.size,
                        backgroundColor = backgroundColor,
                        onRefresh = { scope.launch { store.fetch() } },
                    )
                }

                AnimatedVisibility(
                    visible = isContentScrolled,
                    enter =
                        expandVertically(
                            animationSpec = tween(280),
                            expandFrom = Alignment.Top,
                        ) + fadeIn(animationSpec = tween(200, delayMillis = 80)),
                    exit =
                        shrinkVertically(
                            animationSpec = tween(220),
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(animationSpec = tween(140)),
                ) {
                    Spacer(
                        Modifier
                            .statusBarsPadding()
                            .height(54.dp),
                    )
                }

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(adaptiveProfileGridColumns()),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Transparent),
                    contentPadding =
                        profileGridContentPadding(
                            top = if (!isContentScrolled) 8.dp else 12.dp,
                            bottom = adaptiveMainNavigationContentPadding(),
                        ),
                    horizontalArrangement = Arrangement.spacedBy(ProfileGridHorizontalSpacing),
                    verticalArrangement = Arrangement.spacedBy(ProfileGridVerticalSpacing),
                ) {
                    if (state.isLoading && state.mangaSeries.isEmpty()) {
                        gridItems(List(6) { it }, contentType = { "watchlist_series_skeleton" }) {
                            WatchlistSeriesCardSkeleton()
                        }
                    }
                    if (state.errorMessage != null && state.mangaSeries.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = state.errorMessage.orEmpty(),
                                    color = MiuixTheme.colorScheme.error,
                                    style = MiuixTheme.textStyles.body2,
                                )
                                Button(
                                    onClick = { scope.launch { store.fetch() } },
                                ) {
                                    Text(stringResource(R.string.action_load_more))
                                }
                            }
                        }
                    }
                    if (processedSeries.isEmpty() && !state.isLoading && state.errorMessage == null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(stringResource(R.string.watchlist_series_empty))
                        }
                    }
                    gridItems(processedSeries, key = { it.id }, contentType = { "watchlist_series_card" }) { series ->
                        WatchlistSeriesCard(
                            series = series,
                            onClick = { onOpenSeries(series.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (settings.autoLoadMore && state.isPaginating) {
                        item(
                            key = "watchlist_series_paginating_footer",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoadingIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    } else if (!settings.autoLoadMore && state.model?.nextUrl != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Button(
                                onClick = { scope.launch { store.loadMore() } },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = overlayActionButtonColors(),
                                enabled = !state.isPaginating,
                            ) {
                                if (state.isPaginating) {
                                    LoadingIndicator(modifier = Modifier.size(18.dp))
                                } else {
                                    Text(stringResource(R.string.watchlist_series_load_more))
                                }
                            }
                        }
                    }
                }
            }
        }

        WatchlistSmallTopAppBar(
            bannerUrl = bannerCoverUrl,
            sortOrder = sortOrder,
            onSortOrderChange = { sortOrder = it },
            compact = isContentScrolled,
            onBack = onBack,
            onTitleClick = scrollToTop,
            onRefresh = { scope.launch { store.fetch() } },
            onMessage = { viewModel.showMessage(it) },
        )
    }
}

@Composable
private fun WatchlistHeader(
    bannerUrl: String?,
    seriesCount: Int,
    backgroundColor: Color,
    onRefresh: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(236.dp)
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (!bannerUrl.isNullOrBlank()) {
                PixivImage(
                    url = bannerUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .blur(16.dp),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Black.copy(alpha = 0.25f),
                                            Color.Transparent,
                                            backgroundColor.copy(alpha = 0.85f),
                                        ),
                                ),
                            ),
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            MiuixTheme.colorScheme.primary.copy(alpha = 0.35f),
                                            MiuixTheme.colorScheme.surfaceContainerHighest,
                                        ),
                                ),
                            ),
                )
            }
        }

        WatchlistInfo(
            seriesCount = seriesCount,
            backgroundColor = backgroundColor,
            onRefresh = onRefresh,
        )
    }
}

@Composable
private fun WatchlistInfo(
    seriesCount: Int,
    backgroundColor: Color,
    onRefresh: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .offset(y = (-48).dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(104.dp)
                        .border(BorderStroke(4.dp, backgroundColor), CircleShape)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.FavoritesFill,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier =
                    Modifier
                        .squircleSurface(MiuixTheme.colorScheme.surfaceContainerHighest, 24.dp)
                        .miuixClickable(pressedScale = 0.94f, haptic = true, onClick = onRefresh)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.action_reload),
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.watchlist_series_title),
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.data_items_count, seriesCount),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.more_watchlist_series_summary),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun WatchlistSmallTopAppBar(
    bannerUrl: String?,
    sortOrder: WatchlistSortOrder,
    onSortOrderChange: (WatchlistSortOrder) -> Unit,
    compact: Boolean,
    onBack: () -> Unit,
    onTitleClick: () -> Unit,
    onRefresh: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var showMoreMenu by remember { mutableStateOf(false) }
    val reloadLabel = stringResource(R.string.action_reload)
    val shareLabel = stringResource(R.string.detail_share)
    val shareFailedMessage = stringResource(R.string.error_share_failed)
    val watchlistTitle = stringResource(R.string.watchlist_series_title)
    val sortLabel = stringResource(R.string.search_sort_order)
    val sortNewestLabel = stringResource(R.string.sort_date_desc)
    val sortOldestLabel = stringResource(R.string.sort_date_asc)
    val sortEpisodesLabel = stringResource(R.string.sort_popular_desc)

    val menuEntries =
        remember(sortOrder, sortLabel, sortNewestLabel, sortOldestLabel, sortEpisodesLabel, reloadLabel, shareLabel) {
            listOf(
                DropdownEntry(
                    items =
                        listOf(
                            DropdownItem(
                                text = sortLabel,
                                children =
                                    listOf(
                                        DropdownItem(
                                            text = sortNewestLabel,
                                            selected = sortOrder == WatchlistSortOrder.Newest,
                                            onClick = { onSortOrderChange(WatchlistSortOrder.Newest) },
                                        ),
                                        DropdownItem(
                                            text = sortOldestLabel,
                                            selected = sortOrder == WatchlistSortOrder.Oldest,
                                            onClick = { onSortOrderChange(WatchlistSortOrder.Oldest) },
                                        ),
                                        DropdownItem(
                                            text = sortEpisodesLabel,
                                            selected = sortOrder == WatchlistSortOrder.MostEpisodes,
                                            onClick = { onSortOrderChange(WatchlistSortOrder.MostEpisodes) },
                                        ),
                                    ),
                            ),
                        ),
                ),
                DropdownEntry(
                    items =
                        listOf(
                            DropdownItem(
                                text = reloadLabel,
                                onClick = onRefresh,
                            ),
                            DropdownItem(
                                text = shareLabel,
                                onClick = {
                                    runCatching {
                                        val intent =
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "$watchlistTitle\nhttps://www.pixiv.net/novel/series",
                                                )
                                            }
                                        context.startActivity(Intent.createChooser(intent, shareLabel))
                                    }.onFailure { onMessage(shareFailedMessage) }
                                },
                            ),
                        ),
                ),
            )
        }

    val barScrimColor by animateColorAsState(
        targetValue = if (compact) MiuixTheme.colorScheme.background.copy(alpha = 0.76f) else Color.Transparent,
        label = "watchlist-top-bar-color",
    )

    Box(Modifier.fillMaxWidth()) {
        if (compact && !bannerUrl.isNullOrBlank()) {
            PixivImage(
                url = bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .matchParentSize()
                        .blur(24.dp),
            )
        }
        Box(Modifier.matchParentSize().background(barScrimColor))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderOverlayIcon(
                icon = MiuixIcons.Back,
                onClick = onBack,
                backgroundColor = if (compact) Color.Transparent else Color.White.copy(alpha = 0.92f),
                contentColor = if (compact) MiuixTheme.colorScheme.onBackground else Color.Black,
            )
            if (compact) {
                Text(
                    text = watchlistTitle,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                            .miuixClickable(
                                onClick = onTitleClick,
                                haptic = true,
                                pressedScale = 0.96f,
                            ),
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Box {
                HeaderOverlayIcon(
                    icon = MiuixIcons.More,
                    onClick = { showMoreMenu = true },
                    backgroundColor = if (compact) Color.Transparent else Color.White.copy(alpha = 0.92f),
                    contentColor = if (compact) MiuixTheme.colorScheme.onBackground else Color.Black,
                )
                OverlayCascadingListPopup(
                    show = showMoreMenu,
                    entries = menuEntries,
                    onDismissRequest = { showMoreMenu = false },
                )
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
                            text = "${series.publishedContentCount}話",
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
private fun WatchlistSeriesCardSkeleton() {
    val transition = rememberInfiniteTransition(label = "watchlistSeriesSkeleton")
    val shimmer by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    tween(
                        durationMillis = 1250,
                        easing = FastOutSlowInEasing,
                    ),
                repeatMode = RepeatMode.Restart,
            ),
        label = "watchlistSeriesSkeletonShimmer",
    )
    val base = MiuixTheme.colorScheme.surfaceContainer
    val highlight = MiuixTheme.colorScheme.surfaceContainerHigh
    val shimmerBrush =
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(shimmer * 500f, 0f),
            end = Offset(shimmer * 500f + 260f, 500f),
        )

    Column(
        modifier =
            Modifier
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
                    .background(shimmerBrush),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.82f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(shimmerBrush),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.58f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(shimmerBrush),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.34f)
                        .height(9.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(shimmerBrush),
            )
        }
    }
}
