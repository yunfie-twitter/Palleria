@file:Suppress("MatchingDeclarationName")

package com.yunfie.illustia.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.yunfie.illustia.data.pixiv.IllustSeriesStore
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.pixiv.Illusts
import com.yunfie.illustia.ui.components.AutoLoadMoreEffect
import com.yunfie.illustia.ui.components.AvatarImage
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.HeaderOverlayIcon
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.IllustCardSkeleton
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.PrefetchPixivImages
import com.yunfie.illustia.ui.components.ProfileGridHorizontalSpacing
import com.yunfie.illustia.ui.components.ProfileGridVerticalSpacing
import com.yunfie.illustia.ui.components.WatchlistPill
import com.yunfie.illustia.ui.components.adaptiveMainNavigationContentPadding
import com.yunfie.illustia.ui.components.adaptiveProfileGridColumns
import com.yunfie.illustia.ui.components.miuixClickable
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import com.yunfie.illustia.ui.components.profileGridContentPadding
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.lazy.grid.items as gridItems

@Suppress("MatchingDeclarationName")
enum class IllustSeriesSortOrder {
    Default,
    Oldest,
}

@Composable
fun IllustSeriesScreen(
    seriesId: Long,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val context = LocalContext.current
    val activity = context as? Activity
    val repository = remember(viewModel) { viewModel.uiRepository() }
    val store = remember(repository, seriesId) { IllustSeriesStore(repository, seriesId) }
    val state by store.state.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val pullToRefreshState = rememberPullToRefreshState()

    var sortOrder by remember { mutableStateOf(IllustSeriesSortOrder.Default) }
    var isHeaderCollapsed by remember { mutableStateOf(false) }
    var watchlistAnimationTrigger by remember(seriesId) { mutableIntStateOf(0) }

    val processedIllusts =
        remember(state.illusts, sortOrder) {
            when (sortOrder) {
                IllustSeriesSortOrder.Default -> state.illusts
                IllustSeriesSortOrder.Oldest -> state.illusts.reversed()
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

    val seriesScrollConnection =
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

    val feedHighQuality = settings.useHighQualityFeedImages
    val showAiBadge = remember(settings.showAiBadge) { settings.showAiBadge }
    val prefetchUrls =
        remember(state.illusts, feedHighQuality) {
            state.illusts
                .asSequence()
                .take(16)
                .map { if (feedHighQuality) it.imageUrls.medium.ifBlank { it.imageUrls.large } else it.imageUrls.squareMedium }
                .toList()
        }

    PrefetchPixivImages(prefetchUrls, enabled = settings.prefetchImages)
    AutoLoadMoreEffect(
        enabled = settings.autoLoadMore,
        nextUrl = state.model?.nextUrl,
        isLoading = state.isLoading,
        onLoadMore = { scope.launch { store.loadMore() } },
    )

    LaunchedEffect(store) {
        store.fetch()
    }

    val scrollToTop: () -> Unit = {
        isHeaderCollapsed = false
        scope.launch { gridState.animateScrollToItem(0) }
    }

    val detail = state.model?.illustSeriesDetail
    val detailTitle = detail?.title.orEmpty()
    val coverUrl = detail?.coverImageUrls?.medium ?: processedIllusts.firstOrNull()?.imageUrls?.medium
    val userName = detail?.user?.name.orEmpty()
    val userAvatarUrl = detail?.user?.profileImageUrls?.medium
    val caption = detail?.caption.orEmpty()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(seriesScrollConnection)
                .background(backgroundColor),
    ) {
        // Blurred backdrop behind PullToRefresh (only active at the very top on pull-down)
        val rawPullProgress = pullToRefreshState.pullProgress
        val pullProgress =
            if (rawPullProgress > 0.06f) {
                ((rawPullProgress - 0.06f) / 0.94f).coerceIn(0f, 1f)
            } else {
                0f
            }
        if (pullProgress > 0.001f && !coverUrl.isNullOrBlank()) {
            PixivImage(
                url = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .blur(36.dp)
                        .graphicsLayer { alpha = pullProgress * 0.48f },
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(backgroundColor.copy(alpha = pullProgress * 0.65f)),
            )
        }

        PullToRefresh(
            isRefreshing = state.isLoading && state.illusts.isNotEmpty(),
            onRefresh = { scope.launch { store.fetch() } },
            pullToRefreshState = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
        ) {
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
                    SeriesProfileHeader(
                        detailTitle = detailTitle,
                        coverUrl = coverUrl,
                        userName = userName,
                        userAvatarUrl = userAvatarUrl,
                        caption = caption,
                        workCount = detail?.seriesWorkCount ?: processedIllusts.size,
                        watchlistAdded = state.watchlistAdded,
                        watchlistAnimationTrigger = watchlistAnimationTrigger,
                        backgroundColor = backgroundColor,
                        onToggleWatchlist = {
                            if (!state.watchlistAdded) {
                                watchlistAnimationTrigger += 1
                            }
                            scope.launch {
                                if (state.watchlistAdded) store.removeWatchlist() else store.addWatchlist()
                            }
                        },
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
                    if (state.isLoading && state.illusts.isEmpty()) {
                        gridItems(List(6) { it }, contentType = { "illust_skeleton" }) {
                            IllustCardSkeleton()
                        }
                    }
                    if (state.errorMessage != null && state.illusts.isEmpty()) {
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
                    if (processedIllusts.isEmpty() && !state.isLoading && state.errorMessage == null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(stringResource(R.string.detail_related))
                        }
                    }
                    gridItems(processedIllusts, key = { it.id }, contentType = { "illust_card" }) { illust ->
                        val illustId = illust.id
                        val cardIllust = remember(illustId) { illust.toIllust() }
                        val onBookmark = remember(illustId) { { viewModel.toggleBookmark(illustId, cardIllust) } }
                        val onClick = remember(illustId) { { onOpenIllust(illustId) } }
                        val onLongClick = remember(illustId) { { viewModel.onIllustLongPress(illustId, cardIllust) } }

                        IllustCard(
                            illust = cardIllust,
                            onBookmark = onBookmark,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            modifier = Modifier.animateItem(),
                            highQualityImages = feedHighQuality,
                            showAiBadge = showAiBadge,
                        )
                    }
                    if (!settings.autoLoadMore && state.model?.nextUrl != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Button(
                                onClick = { scope.launch { store.loadMore() } },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = overlayActionButtonColors(),
                            ) {
                                Text(stringResource(R.string.action_load_more))
                            }
                        }
                    }
                }
            }
        }

        SeriesSmallTopAppBar(
            title = detailTitle.ifBlank { stringResource(R.string.detail_series) },
            coverUrl = coverUrl,
            seriesId = seriesId,
            sortOrder = sortOrder,
            onSortOrderChange = { sortOrder = it },
            watchlistAdded = state.watchlistAdded,
            onToggleWatchlist = {
                scope.launch {
                    if (state.watchlistAdded) store.removeWatchlist() else store.addWatchlist()
                }
            },
            compact = isContentScrolled,
            onBack = onBack,
            onTitleClick = scrollToTop,
            onRefresh = { scope.launch { store.fetch() } },
            onMessage = { viewModel.showMessage(it) },
        )
    }
}

@Composable
private fun SeriesProfileHeader(
    detailTitle: String,
    coverUrl: String?,
    userName: String,
    userAvatarUrl: String?,
    caption: String,
    workCount: Int,
    watchlistAdded: Boolean,
    watchlistAnimationTrigger: Int,
    backgroundColor: Color,
    onToggleWatchlist: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(236.dp)
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (!coverUrl.isNullOrBlank()) {
                PixivImage(
                    url = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
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

        SeriesProfileInfo(
            detailTitle = detailTitle,
            userName = userName,
            userAvatarUrl = userAvatarUrl,
            caption = caption,
            workCount = workCount,
            watchlistAdded = watchlistAdded,
            watchlistAnimationTrigger = watchlistAnimationTrigger,
            backgroundColor = backgroundColor,
            onToggleWatchlist = onToggleWatchlist,
        )
    }
}

@Composable
private fun SeriesProfileInfo(
    detailTitle: String,
    userName: String,
    userAvatarUrl: String?,
    caption: String,
    workCount: Int,
    watchlistAdded: Boolean,
    watchlistAnimationTrigger: Int,
    backgroundColor: Color,
    onToggleWatchlist: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .offset(y = (-48).dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!userAvatarUrl.isNullOrBlank() || userName.isNotBlank()) {
                AvatarImage(
                    url = userAvatarUrl,
                    name = userName.ifBlank { detailTitle },
                    size = 104.dp,
                    modifier =
                        Modifier
                            .border(BorderStroke(4.dp, backgroundColor), CircleShape),
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(104.dp)
                            .border(BorderStroke(4.dp, backgroundColor), CircleShape)
                            .background(MiuixTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.FavoritesFill,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            WatchlistPill(
                isAdded = watchlistAdded,
                animationTrigger = watchlistAnimationTrigger,
                modifier = Modifier.miuixClickable(pressedScale = 0.94f, haptic = false, onClick = onToggleWatchlist),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = detailTitle.ifBlank { stringResource(R.string.detail_series) },
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (userName.isNotBlank()) {
                Text(
                    text = userName,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = stringResource(R.string.data_items_count, workCount),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Bold,
            )
            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SeriesSmallTopAppBar(
    title: String,
    coverUrl: String?,
    seriesId: Long,
    sortOrder: IllustSeriesSortOrder,
    onSortOrderChange: (IllustSeriesSortOrder) -> Unit,
    watchlistAdded: Boolean,
    onToggleWatchlist: () -> Unit,
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
    val sortLabel = stringResource(R.string.search_sort_order)
    val sortNewestLabel = stringResource(R.string.sort_date_desc)
    val sortOldestLabel = stringResource(R.string.sort_date_asc)
    val watchlistActionLabel = if (watchlistAdded) stringResource(R.string.action_remove_bookmark) else stringResource(R.string.action_add)

    val menuEntries =
        remember(sortOrder, sortLabel, sortNewestLabel, sortOldestLabel, watchlistAdded, watchlistActionLabel, reloadLabel, shareLabel) {
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
                                            selected = sortOrder == IllustSeriesSortOrder.Default,
                                            onClick = { onSortOrderChange(IllustSeriesSortOrder.Default) },
                                        ),
                                        DropdownItem(
                                            text = sortOldestLabel,
                                            selected = sortOrder == IllustSeriesSortOrder.Oldest,
                                            onClick = { onSortOrderChange(IllustSeriesSortOrder.Oldest) },
                                        ),
                                    ),
                            ),
                            DropdownItem(
                                text = watchlistActionLabel,
                                onClick = onToggleWatchlist,
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
                                                    "$title\nhttps://www.pixiv.net/user_series/$seriesId",
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
        label = "series-top-bar-color",
    )

    Box(Modifier.fillMaxWidth()) {
        if (compact && !coverUrl.isNullOrBlank()) {
            PixivImage(
                url = coverUrl,
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
                    text = title,
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

private fun Illusts.toIllust(): Illust =
    Illust(
        id = id,
        title = title,
        type = type,
        caption = caption,
        artistId = user.id,
        artistName = user.name,
        artistAvatarUrl = user.profileImageUrls.medium,
        squareImageUrl = imageUrls.squareMedium,
        mediumImageUrl = imageUrls.medium,
        imageUrl = imageUrls.large,
        originalImageUrl = metaSinglePage?.originalImageUrl,
        tags = tags.map { it.name },
        pageCount = pageCount,
        isBookmarked = isBookmarked,
        totalComments = totalComments,
        series = series,
    )
