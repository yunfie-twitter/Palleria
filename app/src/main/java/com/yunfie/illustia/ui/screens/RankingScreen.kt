package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.isMutedByTags
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshotFlow
import kotlin.math.absoluteValue
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.icon.extended.*

@Composable
fun RankingScreen(
    items: List<com.yunfie.illustia.models.Illust>,
    loadState: com.yunfie.illustia.models.LoadState,
    nextUrl: String?,
    mode: String,
    settings: com.yunfie.illustia.settings.AppSettings,
    viewModel: IllustiaViewModel,
) {
    val modes = remember {
        listOf("day", "day_male", "day_female", "week", "month", "week_rookie", "day_ai")
    }
    val coroutineScope = rememberCoroutineScope()
    val currentIndex = modes.indexOf(mode).coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = currentIndex,
        pageCount = { modes.size },
    )

    // Commit a ranking change only after the swipe/scroll animation settles. This
    // avoids loading every intermediate tab when jumping across several modes.
    LaunchedEffect(pagerState, mode) {
        snapshotFlow { pagerState.isScrollInProgress to pagerState.settledPage }
            .filter { (isScrolling, _) -> !isScrolling }
            .map { (_, page) -> page }
            .distinctUntilChanged()
            .collect { page ->
                val newMode = modes[page]
                if (newMode != mode) viewModel.selectRankingMode(newMode)
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
        if (items.isEmpty()) {
            viewModel.refreshRanking()
        }
    }

    val scheme = MiuixTheme.colorScheme
    val scrollBehavior = MiuixScrollBehavior()
    AutoLoadMoreEffect(
        enabled = settings.autoLoadMore,
        nextUrl = nextUrl,
        isLoading = loadState == LoadState.Loading,
        onLoadMore = viewModel::loadMoreRanking,
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.surface),
    ) {
        TopAppBar(
            title = stringResource(R.string.nav_ranking),
            largeTitle = stringResource(R.string.nav_ranking),
            scrollBehavior = scrollBehavior,
            actions = {
                IconButton(onClick = viewModel::refreshRanking) {
                    Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.dialog_reload))
                }
            },
            bottomContent = {
                if (settings.amoledMode) {
                    RankingModeTabs(
                        currentMode = modes[pagerState.targetPage],
                        onSelectMode = { newMode ->
                            val index = modes.indexOf(newMode).coerceAtLeast(0)
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        },
                        modes = modes,
                    )
                } else {
                    TabRow(
                        tabs = listOf(
                            stringResource(R.string.ranking_day),
                            stringResource(R.string.ranking_day_male),
                            stringResource(R.string.ranking_day_female),
                            stringResource(R.string.ranking_week),
                            stringResource(R.string.ranking_month),
                            stringResource(R.string.ranking_week_rookie),
                            stringResource(R.string.ranking_day_ai),
                        ),
                        selectedTabIndex = modes.indexOf(modes[pagerState.targetPage]).coerceAtLeast(0),
                        onTabSelected = { index ->
                            val newMode = modes.getOrNull(index) ?: return@TabRow
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            if (newMode != mode) viewModel.selectRankingMode(newMode)
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        minWidth = 92.dp,
                        maxWidth = 148.dp,
                    )
                }
            },
        )
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = scheme.surface,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val pageOffset = (
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                ).absoluteValue.coerceIn(0f, 1f)

                RankingGridContent(
                    items = items,
                    loadState = loadState,
                    nextUrl = nextUrl,
                    mode = mode,
                    settings = settings,
                    viewModel = viewModel,
                    scrollBehavior = scrollBehavior,
                    modifier = Modifier.graphicsLayer {
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
private fun RankingModeTabs(
    currentMode: String,
    onSelectMode: (String) -> Unit,
    modes: List<String>,
) {
    val scheme = MiuixTheme.colorScheme
    val tabLabels = listOf(
        stringResource(R.string.ranking_day),
        stringResource(R.string.ranking_day_male),
        stringResource(R.string.ranking_day_female),
        stringResource(R.string.ranking_week),
        stringResource(R.string.ranking_month),
        stringResource(R.string.ranking_week_rookie),
        stringResource(R.string.ranking_day_ai),
    )
    TabRow(
        tabs = tabLabels,
        selectedTabIndex = modes.indexOf(currentMode).coerceAtLeast(0),
        onTabSelected = { index -> modes.getOrNull(index)?.let(onSelectMode) },
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = scheme.surfaceContainer.copy(alpha = 0.88f),
            contentColor = scheme.onSurfaceVariantSummary,
            selectedBackgroundColor = scheme.surfaceContainerHigh,
            selectedContentColor = scheme.onBackground,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        minWidth = 92.dp,
        maxWidth = 148.dp,
    )
}

@Composable
private fun RankingGridContent(
    items: List<com.yunfie.illustia.models.Illust>,
    loadState: com.yunfie.illustia.models.LoadState,
    nextUrl: String?,
    mode: String,
    settings: com.yunfie.illustia.settings.AppSettings,
    viewModel: IllustiaViewModel,
    scrollBehavior: ScrollBehavior = MiuixScrollBehavior(),
    modifier: Modifier = Modifier,
) {
    val feedHighQuality = remember(settings.highQualityImages, settings.feedPreviewQuality) {
        settings.highQualityImages && settings.feedPreviewQuality != "low"
    }
    val showAiBadge = remember(settings.showAiBadge) { settings.showAiBadge }
    val gridState = viewModel.rankingGridState
    val prefetchUrls = remember(items, feedHighQuality) {
        items.asSequence()
            .take(16)
            .map { if (feedHighQuality) it.previewUrl else it.thumbnailUrl }
            .toList()
    }
    PrefetchPixivImages(prefetchUrls, enabled = settings.prefetchImages)

    PullToRefresh(
        isRefreshing = loadState == com.yunfie.illustia.models.LoadState.Loading && items.isNotEmpty(),
        onRefresh = { viewModel.refreshRanking() },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 2.dp,
                    bottom = 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (items.isEmpty() && loadState == com.yunfie.illustia.models.LoadState.Loading) {
                    items(6, contentType = { "illust_skeleton" }) { IllustCardSkeleton() }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) { StateBanner(loadState) }
                }

                gridItems(items, key = { "ranking_${it.id}" }, contentType = { "illust_card" }) { illust ->
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
                        isMutedByTag = illust.isMutedByTags(settings),
                    )
                }

                if (!settings.autoLoadMore && nextUrl != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Button(
                            onClick = viewModel::loadMoreRanking,
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

