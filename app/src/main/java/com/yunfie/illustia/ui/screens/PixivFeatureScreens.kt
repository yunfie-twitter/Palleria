package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.pixiv.CommentArtworkType
import com.yunfie.illustia.data.pixiv.CommentStore
import com.yunfie.illustia.data.pixiv.IllustSeriesStore
import com.yunfie.illustia.data.pixiv.WatchlistStore
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.pixiv.Comment
import com.yunfie.illustia.models.pixiv.Illusts
import com.yunfie.illustia.models.pixiv.MangaSeriesModel
import com.yunfie.illustia.ui.components.AvatarImage
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.IllustCardSkeleton
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PrefetchPixivImages
import com.yunfie.illustia.ui.components.miuixClickable
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
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
    val scope = rememberCoroutineScope()

    LaunchedEffect(store) {
        store.fetch()
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.watchlist_series_title),
                largeTitle = stringResource(R.string.watchlist_series_title),
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = scaffoldPadding.calculateTopPadding() + 8.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.errorMessage != null) {
                    item { Text(state.errorMessage ?: "", color = MiuixTheme.colorScheme.error) }
                }
                if (state.mangaSeries.isEmpty() && !state.isLoading) {
                    item { EmptyState(stringResource(R.string.watchlist_series_empty)) }
                }
                items(state.mangaSeries, key = { it.id }) { series ->
                    WatchlistSeriesRow(
                        series = series,
                        onClick = { onOpenSeries(series.id) },
                    )
                }
                if (state.model?.nextUrl != null) {
                    item {
                        Button(
                            onClick = { scope.launch { store.loadMore() } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = overlayActionButtonColors(),
                        ) {
                            Text(stringResource(R.string.watchlist_series_load_more))
                        }
                    }
                }
                if (state.isLoading && state.mangaSeries.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistSeriesRow(
    series: MangaSeriesModel,
    onClick: () -> Unit,
) {
    ElevatedPanel(
        modifier = Modifier
            .fillMaxWidth()
            .miuixClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.FavoritesFill,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = series.title,
                    style = MiuixTheme.textStyles.body1,
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
                    text = "ID ${series.id} ・ ${series.publishedContentCount}P",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote2,
                )
            }
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

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

    LaunchedEffect(store) {
        store.fetch()
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = state.model?.illustSeriesDetail?.title ?: stringResource(R.string.detail_series),
                largeTitle = state.model?.illustSeriesDetail?.title ?: stringResource(R.string.detail_series),
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
            isRefreshing = state.isLoading && state.illusts.isNotEmpty(),
            onRefresh = { scope.launch { store.fetch() } },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
                modifier = Modifier
                    .fillMaxSize()
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
                item(span = { GridItemSpan(maxLineSpan) }) {
                        SeriesHeader(
                            detailTitle = state.model?.illustSeriesDetail?.title.orEmpty(),
                            coverUrl = state.model?.illustSeriesDetail?.coverImageUrls?.medium,
                            caption = state.model?.illustSeriesDetail?.caption.orEmpty(),
                            watchlistAdded = state.watchlistAdded,
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
                    item(span = { GridItemSpan(maxLineSpan) }) { Text(state.errorMessage ?: "", color = MiuixTheme.colorScheme.error) }
                }
                if (state.illusts.isEmpty() && !state.isLoading) {
                    item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(stringResource(R.string.detail_related)) }
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
                if (state.model?.nextUrl != null) {
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
}

@Composable
private fun SeriesHeader(
    detailTitle: String,
    coverUrl: String?,
    caption: String,
    watchlistAdded: Boolean,
    onToggleWatchlist: () -> Unit,
) {
    ElevatedPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(20.dp))
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
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = detailTitle.ifBlank { stringResource(R.string.detail_series) },
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.Black,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Button(
                        onClick = onToggleWatchlist,
                        colors = overlayActionButtonColors(),
                    ) {
                        Text(if (watchlistAdded) stringResource(R.string.action_remove_bookmark) else stringResource(R.string.action_add))
                    }
                }
            }
            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
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

@Composable
fun CommentScreen(
    show: Boolean,
    id: Long,
    type: CommentArtworkType,
    viewModel: IllustiaViewModel,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    if (!show) return
    val repository = remember(viewModel) { viewModel.uiRepository() }
    val store = remember(repository, id, type) { CommentStore(repository, id, type = type) }
    val state by store.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var commentText by remember { mutableStateOf("") }
    val hideCommentInput = remember(state.comments) {
        state.comments.any { it.isPixivCommentDisabledNotice() }
    }

    LaunchedEffect(store) {
        store.fetch()
    }

    OverlayBottomSheet(
        show = true,
        title = stringResource(R.string.detail_comments),
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
        endAction = {
            IconButton(onClick = { scope.launch { store.fetch() } }) {
                Icon(imageVector = MiuixIcons.Refresh, contentDescription = stringResource(R.string.action_load_more))
            }
        },
        onDismissRequest = onDismiss,
        backgroundColor = LocalBottomSheetBackgroundColor.current,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PullToRefresh(
                isRefreshing = state.isLoading && state.comments.isNotEmpty(),
                onRefresh = { scope.launch { store.fetch() } },
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.errorMessage != null) {
                        item { Text(state.errorMessage ?: "", color = MiuixTheme.colorScheme.error) }
                    }
                    if (state.comments.isEmpty() && !state.isLoading) {
                        item { EmptyState(stringResource(R.string.detail_comments)) }
                    }
                    items(state.comments, key = { it.id ?: it.hashCode().toLong() }) { comment ->
                        CommentRow(
                            comment = comment,
                            onOpenUser = comment.user?.id?.let { userId -> { onOpenUser(userId) } },
                        )
                    }
                    if (state.nextUrl != null) {
                        item {
                            Button(
                                onClick = { scope.launch { store.next() } },
                                modifier = Modifier.fillMaxWidth(),
                                colors = overlayActionButtonColors(),
                            ) {
                                Text(stringResource(R.string.action_load_more))
                            }
                        }
                    }
                    if (state.isLoading && state.comments.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                LoadingIndicator()
                            }
                        }
                    }
                }
            }

            if (!hideCommentInput) {
                ElevatedPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            label = stringResource(R.string.detail_comments),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        IconButton(
                            onClick = {
                                val text = commentText.trim()
                                if (text.isNotEmpty()) {
                                    scope.launch {
                                        store.postComment(text)
                                        commentText = ""
                                        store.fetch()
                                    }
                                }
                            },
                            enabled = commentText.isNotBlank(),
                            backgroundColor = MiuixTheme.colorScheme.primary,
                            minWidth = 44.dp,
                            minHeight = 44.dp,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Send,
                                contentDescription = stringResource(R.string.action_add),
                                tint = MiuixTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Comment.isPixivCommentDisabledNotice(): Boolean {
    val message = comment.orEmpty().replace(Regex("\\s+"), "")
    return message.contains("コメントがオフにされています")
}

@Composable
private fun CommentRow(
    comment: Comment,
    onOpenUser: (() -> Unit)?,
) {
    ElevatedPanel(
        modifier = Modifier
            .fillMaxWidth()
            .miuixClickable(onClick = onOpenUser ?: {}),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val user = comment.user
                val avatarUrl = user?.profileImageUrls?.medium
                if (!avatarUrl.isNullOrBlank()) {
                    AvatarImage(url = avatarUrl, name = user?.name.orEmpty(), size = 38.dp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user?.name.orEmpty(),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = comment.date.orEmpty(),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Text(
                text = comment.comment.orEmpty(),
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}
