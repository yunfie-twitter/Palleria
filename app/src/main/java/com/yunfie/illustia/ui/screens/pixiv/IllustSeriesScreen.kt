package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.yunfie.illustia.ui.components.PrefetchPixivImages
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun IllustSeriesScreen(
    seriesId: Long,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
) {
    val repository = remember(viewModel) { viewModel.uiRepository() }
    val store = remember(repository, seriesId) { IllustSeriesStore(repository, seriesId) }
    val state by store.state.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val feedHighQuality = remember(settings.highQualityImages, settings.feedPreviewQuality) {
        settings.highQualityImages && settings.feedPreviewQuality != "low"
    }
    val showAiBadge = remember(settings.showAiBadge) { settings.showAiBadge }
    val prefetchUrls = remember(state.illusts, feedHighQuality) {
        state.illusts.asSequence()
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

    PullToRefresh(
        isRefreshing = state.isLoading && state.illusts.isNotEmpty(),
        onRefresh = { scope.launch { store.fetch() } },
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = 0.dp,
                bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SeriesHeader(
                    detailTitle = state.model?.illustSeriesDetail?.title.orEmpty(),
                    coverUrl = state.model?.illustSeriesDetail?.coverImageUrls?.medium,
                    userName = state.model?.illustSeriesDetail?.user?.name.orEmpty(),
                    userAvatarUrl = state.model?.illustSeriesDetail?.user?.profileImageUrls?.medium,
                    caption = state.model?.illustSeriesDetail?.caption.orEmpty(),
                    watchlistAdded = state.watchlistAdded,
                    onBack = onBack,
                    onRefresh = { scope.launch { store.fetch() } },
                    onToggleWatchlist = {
                        scope.launch {
                            if (state.watchlistAdded) store.removeWatchlist() else store.addWatchlist()
                        }
                    },
                )
            }
            if (state.isLoading && state.illusts.isEmpty()) {
                items(6, contentType = { "illust_skeleton" }) { IllustCardSkeleton() }
            }
            if (state.errorMessage != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = state.errorMessage ?: "",
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }
            if (state.illusts.isEmpty() && !state.isLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.detail_related))
                }
            }
            gridItems(state.illusts, key = { it.id }, contentType = { "illust_card" }) { illust ->
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
                    highQualityImages = feedHighQuality,
                    showAiBadge = showAiBadge,
                )
            }
            if (!settings.autoLoadMore && state.model?.nextUrl != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = { scope.launch { store.loadMore() } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = overlayActionButtonColors(),
                    ) {
                        Text(stringResource(R.string.action_load_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesHeader(
    detailTitle: String,
    coverUrl: String?,
    userName: String,
    userAvatarUrl: String?,
    caption: String,
    watchlistAdded: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleWatchlist: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.48f)
                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (!coverUrl.isNullOrBlank()) {
                PixivImage(
                    url = coverUrl,
                    contentDescription = detailTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    thumbnail = true,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.FavoritesFill,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                MiuixTheme.colorScheme.surface.copy(alpha = 0.54f),
                            ),
                        ),
                    ),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderOverlayIcon(icon = MiuixIcons.Back, onClick = onBack)
                HeaderOverlayIcon(icon = MiuixIcons.Refresh, onClick = onRefresh)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = detailTitle.ifBlank { stringResource(R.string.detail_series) },
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AvatarImage(
                    url = userAvatarUrl,
                    name = userName.ifBlank { detailTitle },
                    size = 34.dp,
                )
                Text(
                    text = userName.ifBlank { stringResource(R.string.detail_series) },
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Button(
                onClick = onToggleWatchlist,
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.surfaceContainer,
                    contentColor = MiuixTheme.colorScheme.onBackground,
                ),
                insideMargin = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    text = if (watchlistAdded) stringResource(R.string.action_remove_bookmark) else stringResource(R.string.action_add),
                    fontWeight = FontWeight.Bold,
                )
            }

            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun Illusts.toIllust(): Illust {
    return Illust(
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
}
