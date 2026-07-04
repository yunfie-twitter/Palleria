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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.pixiv.WatchlistStore
import com.yunfie.illustia.models.pixiv.MangaSeriesModel
import com.yunfie.illustia.ui.components.AutoLoadMoreEffect
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun WatchlistSeriesScreen(
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    val repository = remember(viewModel) { viewModel.uiRepository() }
    val store = remember(repository) { WatchlistStore(repository) }
    val state by store.state.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(store) {
        store.fetch()
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.watchlist_series_title),
                largeTitle = stringResource(R.string.watchlist_series_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { store.fetch() } }) {
                        Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.action_load_more))
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        PullToRefresh(
            isRefreshing = state.isLoading && state.mangaSeries.isNotEmpty(),
            onRefresh = { scope.launch { store.fetch() } },
            modifier = Modifier.fillMaxSize(),
        ) {
            AutoLoadMoreEffect(
                enabled = settings.autoLoadMore,
                nextUrl = state.model?.nextUrl,
                isLoading = state.isLoading,
                onLoadMore = { scope.launch { store.loadMore() } },
            )
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .background(MiuixTheme.colorScheme.surface),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = scaffoldPadding.calculateTopPadding() + 8.dp,
                    bottom = 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.isLoading && state.mangaSeries.isEmpty()) {
                    gridItems(List(6) { it }, contentType = { "watchlist_series_skeleton" }) {
                        WatchlistSeriesCardSkeleton()
                    }
                }
                if (state.errorMessage != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = state.errorMessage ?: "",
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                }
                if (state.mangaSeries.isEmpty() && !state.isLoading && state.errorMessage == null) {
                    item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(stringResource(R.string.watchlist_series_empty)) }
                }
                gridItems(state.mangaSeries, key = { it.id }, contentType = { "watchlist_series_card" }) { series ->
                    WatchlistSeriesCard(
                        series = series,
                        onClick = { onOpenSeries(series.id) },
                    )
                }
                if (!settings.autoLoadMore && state.model?.nextUrl != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Button(
                            onClick = { scope.launch { store.loadMore() } },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
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
