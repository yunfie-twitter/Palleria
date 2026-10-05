package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.isMutedByTags
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.AutoLoadMoreEffect
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.IllustCardSkeleton
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.LocalScrollHeaderInset
import com.yunfie.illustia.ui.components.PrefetchIllustGridImages
import com.yunfie.illustia.ui.components.ScrollBlurScaffold
import com.yunfie.illustia.ui.components.StateBanner
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.adaptiveMainNavigationContentPadding
import com.yunfie.illustia.ui.components.animatedGridPlacement
import com.yunfie.illustia.ui.components.onTopBarTap
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import com.yunfie.illustia.ui.components.rememberIllustSkeletonShimmer
import com.yunfie.illustia.ui.components.smoothScrollToTop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.absoluteValue
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
fun RankingScreen(
    items: List<Illust>,
    loadState: LoadState,
    nextUrl: String?,
    mode: String,
    settings: AppSettings,
    viewModel: IllustiaViewModel,
) {
    val modes =
        remember {
            listOf("day", "day_male", "day_female", "week", "month", "week_rookie", "day_ai")
        }
    val coroutineScope = rememberCoroutineScope()
    val performHaptic = rememberHapticFeedbackAction()
    val currentIndex = modes.indexOf(mode).coerceAtLeast(0)
    val pagerState =
        rememberPagerState(
            initialPage = currentIndex,
            pageCount = { modes.size },
        )
    val latestMode by rememberUpdatedState(mode)
    val rankingState by viewModel.rankingState.collectAsStateWithLifecycle()

    // Commit a ranking change only after the swipe/scroll animation settles. This
    // avoids loading every intermediate tab when jumping across several modes.
    LaunchedEffect(pagerState) {
        snapshotFlow { if (pagerState.isScrollInProgress) -1 else pagerState.settledPage }
            .filter { it >= 0 }
            .distinctUntilChanged()
            .collect { page ->
                val newMode = modes.getOrNull(page) ?: return@collect
                if (newMode != latestMode) viewModel.selectRankingMode(newMode)
            }
    }

    // Keep the pager aligned if the mode is restored or changed externally.
    LaunchedEffect(mode) {
        val modeIndex = modes.indexOf(mode).coerceAtLeast(0)
        if (!pagerState.isScrollInProgress && pagerState.settledPage != modeIndex) {
            pagerState.animateScrollToPage(modeIndex)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadRankingModeIfNeeded(mode)
    }

    val scheme = MiuixTheme.colorScheme
    val scrollBehavior = MiuixScrollBehavior()
    ScrollBlurScaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .background(scheme.surface),
        enabled = settings.isFeatureEnabled(FeatureFlag.TopScrollBlur),
        scrollBehavior = scrollBehavior,
        header = {
            TopAppBar(
                color = if (settings.isFeatureEnabled(FeatureFlag.TopScrollBlur)) Color.Transparent else MiuixTheme.colorScheme.surface,
                title = stringResource(R.string.nav_ranking),
                largeTitle = stringResource(R.string.nav_ranking),
                scrollBehavior = scrollBehavior,
                modifier =
                    Modifier.onTopBarTap(
                        navIconWidth = 0.dp,
                        actionsWidth = 56.dp,
                        hasBottomContent = true,
                    ) {
                        performHaptic(AppHapticEffect.Click)
                        coroutineScope.launch {
                            val currentMode = modes.getOrNull(pagerState.currentPage)
                            if (currentMode != null) {
                                viewModel.rankingGridState(currentMode).smoothScrollToTop(scrollBehavior)
                            }
                        }
                    },
                actions = {
                    IconButton(
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        onClick = { viewModel.refreshRanking(modes[pagerState.targetPage]) },
                    ) {
                        Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.dialog_reload))
                    }
                },
                bottomContent = {
                    val tabs =
                        remember {
                            listOf(
                                R.string.ranking_day,
                                R.string.ranking_day_male,
                                R.string.ranking_day_female,
                                R.string.ranking_week,
                                R.string.ranking_month,
                                R.string.ranking_week_rookie,
                                R.string.ranking_day_ai,
                            )
                        }
                    val tabTitles = tabs.map { stringResource(it) }
                    val onTabSelected: (Int) -> Unit =
                        remember(modes) {
                            { index ->
                                if (modes.getOrNull(index) != null) {
                                    performHaptic(com.yunfie.illustia.ui.components.AppHapticEffect.Toggle)
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                }
                            }
                        }
                    RankingTabRow(
                        pagerState = pagerState,
                        tabs = tabTitles,
                        onTabSelected = onTabSelected,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    )
                },
            )
        },
    ) {
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = scheme.surface,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val pageMode = modes[page]
                val pageItems = rankingState.rankingModeItems[pageMode] ?: if (pageMode == mode) items else emptyList()
                val pageLoadState = rankingState.rankingModeLoadStates[pageMode] ?: if (pageMode == mode) loadState else LoadState.Idle
                val pageNextUrl = rankingState.rankingModeNextUrls[pageMode] ?: if (pageMode == mode) nextUrl else null
                val isModeRefreshing = rankingState.isRankingRefreshing[pageMode] == true
                val isModePaginating = rankingState.isRankingPaginating[pageMode] == true

                LaunchedEffect(pageMode) {
                    viewModel.loadRankingModeIfNeeded(pageMode)
                }

                RankingGridContent(
                    items = pageItems,
                    loadState = pageLoadState,
                    nextUrl = pageNextUrl,
                    mode = pageMode,
                    settings = settings,
                    viewModel = viewModel,
                    isRefreshing = isModeRefreshing,
                    isPaginating = isModePaginating,
                    scrollBehavior = scrollBehavior,
                    modifier =
                        Modifier.graphicsLayer {
                            val pageOffset =
                                (
                                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                ).absoluteValue.coerceIn(0f, 1f)
                            alpha = 1f - (pageOffset * 0.22f)
                            scaleX = 1f - (pageOffset * 0.035f)
                            scaleY = 1f - (pageOffset * 0.035f)
                        },
                )
            }
        }
    }
}

@Composable
private fun RankingGridContent(
    items: List<com.yunfie.illustia.models.Illust>,
    loadState: com.yunfie.illustia.models.LoadState,
    nextUrl: String?,
    mode: String,
    settings: com.yunfie.illustia.settings.AppSettings,
    viewModel: IllustiaViewModel,
    isRefreshing: Boolean = false,
    isPaginating: Boolean = false,
    scrollBehavior: ScrollBehavior = MiuixScrollBehavior(),
    modifier: Modifier = Modifier,
) {
    val isModeRefreshing = isRefreshing
    val isModePaginating = isPaginating
    val feedHighQuality = settings.useHighQualityFeedImages
    val showAiBadge = remember(settings.showAiBadge) { settings.showAiBadge }
    val gridState = viewModel.rankingGridState(mode)
    PrefetchIllustGridImages(
        items = items,
        gridState = gridState,
        enabled = settings.prefetchImages,
        highQualityImages = feedHighQuality,
        keyPrefix = "ranking_${mode}_",
    )
    val showInitialSkeletons = items.isEmpty() && loadState == LoadState.Loading
    val showPaginationSkeletons = settings.autoLoadMore && isModePaginating
    val shimmer =
        if (showInitialSkeletons || showPaginationSkeletons) {
            rememberIllustSkeletonShimmer()
        } else {
            null
        }

    AutoLoadMoreEffect(
        gridState = gridState,
        enabled = settings.autoLoadMore,
        nextUrl = nextUrl,
        isLoading = isModePaginating || loadState == LoadState.Loading,
        onLoadMore = { viewModel.loadMoreRanking(mode) },
    )

    val columns = adaptiveIllustColumns(settings)
    PullToRefresh(
        isRefreshing = isModeRefreshing,
        onRefresh = { viewModel.refreshRanking(mode, forceRefresh = true) },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding =
                PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = LocalScrollHeaderInset.current + 2.dp,
                    bottom = adaptiveMainNavigationContentPadding(),
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showInitialSkeletons) {
                items(6, key = { "ranking_${mode}_skeleton_$it" }, contentType = { "illust_skeleton" }) {
                    IllustCardSkeleton(shimmerValue = shimmer)
                }
            }

            if (loadState is LoadState.Error) {
                item(key = "ranking_${mode}_error_banner", span = { GridItemSpan(maxLineSpan) }) {
                    StateBanner(loadState)
                }
            }

            gridItems(items, key = { "ranking_${it.id}" }, contentType = { "illust_card" }) { illust ->
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
                    isMutedByTag = illust.isMutedByTags(settings),
                )
            }

            if (showPaginationSkeletons) {
                items(
                    count = columns,
                    key = { "ranking_${mode}_paginating_skeleton_$it" },
                    contentType = { "illust_skeleton" },
                ) {
                    IllustCardSkeleton(shimmerValue = shimmer)
                }
            } else if (!settings.autoLoadMore && nextUrl != null) {
                item(key = "ranking_${mode}_load_more_button", span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = { viewModel.loadMoreRanking(mode) },
                        enabled = !isModePaginating,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                        colors = overlayActionButtonColors(),
                    ) {
                        if (isModePaginating) {
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
private fun RankingTabRow(
    pagerState: androidx.compose.foundation.pager.PagerState,
    tabs: List<String>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        tabs = tabs,
        selectedTabIndex = pagerState.targetPage.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
        onTabSelected = onTabSelected,
        modifier = modifier.pointerHoverIcon(PointerIcon.Hand),
        minWidth = 92.dp,
        maxWidth = 148.dp,
    )
}
