package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.SearchUiState
import com.yunfie.illustia.isMutedByTags
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.searchUiState
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.ui.components.AutoLoadMoreEffect
import com.yunfie.illustia.ui.components.AvatarImage
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.FollowPill
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.IllustCardSkeleton
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PrefetchIllustGridImages
import com.yunfie.illustia.ui.components.PrefetchPixivImages
import com.yunfie.illustia.ui.components.UserResultCardSkeleton
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.adaptiveMainNavigationContentPadding
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import com.yunfie.illustia.ui.components.pinchToChangeColumns
import com.yunfie.illustia.ui.components.rememberIllustSkeletonShimmer
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
internal fun SearchResultGrid(
    page: Int,
    state: SearchUiState,
    viewModel: IllustiaViewModel,
    onIllustSelected: ((Illust) -> Unit)? = null,
) {
    val feedHighQuality = state.settings.useHighQualityFeedImages
    val showAiBadge = remember(state.settings.showAiBadge) { state.settings.showAiBadge }
    val isNovelResult = page == 0 && state.settings.searchWorkType.isNovel
    val gridState = if (page == 0) viewModel.searchResultGridState else viewModel.userSearchResultGridState
    val isPaginating = if (page == 0) state.isSearchPaginating else state.isUserSearchPaginating

    val isIllustResult = page == 0 && !isNovelResult
    if (isIllustResult) {
        PrefetchIllustGridImages(
            items = state.searchItems,
            gridState = gridState,
            enabled = state.settings.prefetchImages,
            highQualityImages = feedHighQuality,
        )
    } else if (page == 0 && isNovelResult) {
        val prefetchUrls =
            remember(state.searchNovelItems) {
                val items = state.searchNovelItems
                val startIndex = (items.size - 24).coerceAtLeast(0)
                val result = ArrayList<String>(items.size - startIndex)
                for (i in startIndex until items.size) {
                    val cover = items[i].coverUrl
                    if (cover.isNotBlank()) {
                        result.add(cover)
                    }
                }
                result
            }
        PrefetchPixivImages(prefetchUrls, enabled = state.settings.prefetchImages, limit = 24)
    }

    val isAnyLoading = state.loadState == LoadState.Loading || isPaginating
    val shimmer = if (isAnyLoading) rememberIllustSkeletonShimmer() else null

    val nextUrl =
        when {
            page != 0 -> state.userSearchNextUrl
            isNovelResult -> state.searchNovelNextUrl
            else -> state.searchNextUrl
        }
    val illustColumns = adaptiveIllustColumns(state.settings)
    AutoLoadMoreEffect(
        gridState = gridState,
        enabled = state.settings.autoLoadMore,
        nextUrl = nextUrl,
        isLoading = isPaginating || state.loadState == LoadState.Loading,
        onLoadMore = if (page == 0) viewModel::loadMoreSearch else viewModel::loadMoreUserSearch,
    )

    val pinchEnabled =
        state.settings.isFeatureEnabled(FeatureFlag.GridPinchToZoomColumns) &&
            state.settings.gridPinchToZoom && page == 0 && !isNovelResult
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(if (page == 0 && !isNovelResult) illustColumns else 1),
        modifier =
            Modifier
                .fillMaxSize()
                .pinchToChangeColumns(
                    enabled = pinchEnabled,
                    currentColumns = illustColumns,
                    onColumnsChange = viewModel::updateVerticalColumnCount,
                ),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = adaptiveMainNavigationContentPadding()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (page == 0) {
            if (isNovelResult) {
                gridItems(state.searchNovelItems, key = { it.id }, contentType = { "novel_card" }) { novel ->
                    val novelId = novel.id
                    val onClick = remember(novelId) { { viewModel.openNovel(novel) } }
                    NovelCard(novel = novel, onClick = onClick)
                }
                if (state.settings.autoLoadMore && isPaginating) {
                    item(key = "search_novel_paginating_skeleton", span = { GridItemSpan(maxLineSpan) }, contentType = "novel_skeleton") {
                        NovelCardSkeleton(shimmerValue = shimmer)
                    }
                } else if (!state.settings.autoLoadMore && state.searchNovelNextUrl != null) {
                    item(key = "search_novel_load_more_button", span = { GridItemSpan(maxLineSpan) }, contentType = "load_more") {
                        Button(
                            onClick = viewModel::loadMoreSearch,
                            enabled = !isPaginating,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = overlayActionButtonColors(),
                        ) {
                            if (isPaginating) {
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
                if (state.searchNovelItems.isEmpty()) {
                    if (state.loadState is LoadState.Error) {
                        item(key = "search_novel_error_state", span = { GridItemSpan(maxLineSpan) }, contentType = "error_state") {
                            SearchErrorState(
                                message = state.loadState.message,
                                onRetry = { viewModel.submitSearch(forceRefresh = true) },
                            )
                        }
                    } else if (state.loadState == LoadState.Loading) {
                        items(
                            count = 4,
                            key = { "search_novel_initial_skeleton_$it" },
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = { "novel_skeleton" },
                        ) {
                            NovelCardSkeleton(shimmerValue = shimmer)
                        }
                    } else {
                        item(key = "search_novel_empty_state", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_state") {
                            EmptyState(stringResource(R.string.search_empty_novel))
                        }
                    }
                }
            } else {
                gridItems(state.searchItems, key = { it.id }, contentType = { "illust_card" }) { illust ->
                    val illustId = illust.id
                    val onBookmark = remember(illust) { { viewModel.toggleBookmark(illust) } }
                    val onBookmarkLongClick =
                        remember(illust) { { viewModel.toggleBookmark(illust, com.yunfie.illustia.models.Restrict.Private) } }
                    val onClick = remember(illust) { { onIllustSelected?.invoke(illust) ?: viewModel.openIllust(illust) } }
                    val onLongClick = remember(illustId) { { viewModel.onIllustLongPress(illustId) } }

                    IllustCard(
                        illust = illust,
                        onBookmark = onBookmark,
                        onBookmarkLongClick = onBookmarkLongClick,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        highQualityImages = feedHighQuality,
                        showAiBadge = showAiBadge,
                        isMutedByTag = illust.isMutedByTags(state.settings),
                    )
                }
                if (state.settings.autoLoadMore && isPaginating) {
                    items(
                        count = illustColumns,
                        key = { "search_illust_paginating_skeleton_$it" },
                        contentType = { "illust_skeleton" },
                    ) {
                        IllustCardSkeleton(shimmerValue = shimmer)
                    }
                } else if (!state.settings.autoLoadMore && state.searchNextUrl != null) {
                    item(key = "search_illust_load_more_button", span = { GridItemSpan(maxLineSpan) }, contentType = "load_more") {
                        Button(
                            onClick = viewModel::loadMoreSearch,
                            enabled = !isPaginating,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = overlayActionButtonColors(),
                        ) {
                            if (isPaginating) {
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
                if (state.searchItems.isEmpty()) {
                    if (state.loadState is LoadState.Error) {
                        item(key = "search_illust_error_state", span = { GridItemSpan(maxLineSpan) }, contentType = "error_state") {
                            SearchErrorState(
                                message = state.loadState.message,
                                onRetry = { viewModel.submitSearch(forceRefresh = true) },
                            )
                        }
                    } else if (state.loadState == LoadState.Loading) {
                        items(
                            count = illustColumns * 3,
                            key = { "search_illust_initial_skeleton_$it" },
                            contentType = { "illust_skeleton" },
                        ) {
                            IllustCardSkeleton(shimmerValue = shimmer)
                        }
                    } else {
                        item(key = "search_illust_empty_state", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_state") {
                            EmptyState(stringResource(R.string.search_empty_illust))
                        }
                    }
                }
            }
        } else {
            gridItems(state.userSearchItems, key = { it.id }, contentType = { "user_card" }) { user ->
                val userId = user.id
                val onClick = remember(userId) { { viewModel.openUserPage(user) } }
                UserResultCard(user = user, onClick = onClick)
            }
            if (state.settings.autoLoadMore && isPaginating) {
                item(key = "search_user_paginating_skeleton", span = { GridItemSpan(maxLineSpan) }, contentType = "user_skeleton") {
                    UserResultCardSkeleton(shimmerValue = shimmer)
                }
            } else if (!state.settings.autoLoadMore && state.userSearchNextUrl != null) {
                item(key = "search_user_load_more_button", span = { GridItemSpan(maxLineSpan) }, contentType = "load_more") {
                    Button(
                        onClick = viewModel::loadMoreUserSearch,
                        enabled = !isPaginating,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = overlayActionButtonColors(),
                    ) {
                        if (isPaginating) {
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
            if (state.userSearchItems.isEmpty()) {
                if (state.loadState is LoadState.Error) {
                    item(key = "search_user_error_state", span = { GridItemSpan(maxLineSpan) }, contentType = "error_state") {
                        SearchErrorState(
                            message = state.loadState.message,
                            onRetry = { viewModel.submitSearch(forceRefresh = true) },
                        )
                    }
                } else if (state.loadState == LoadState.Loading) {
                    items(
                        count = 4,
                        key = { "search_user_initial_skeleton_$it" },
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = { "user_skeleton" },
                    ) {
                        UserResultCardSkeleton(shimmerValue = shimmer)
                    }
                } else {
                    item(key = "search_user_empty_state", span = { GridItemSpan(maxLineSpan) }, contentType = "empty_state") {
                        EmptyState(stringResource(R.string.search_empty_user))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Button(
            onClick = onRetry,
            colors = overlayActionButtonColors(),
        ) {
            Text(stringResource(R.string.action_reload))
        }
    }
}

@Composable
internal fun UserResultCard(
    user: UserPreview,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
        Column(modifier = Modifier.fillMaxWidth()) {
            if (user.previewIllusts.isNotEmpty()) {
                val previewCount = minOf(3, user.previewIllusts.size)
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (i in 0 until previewCount) {
                        val illust = user.previewIllusts[i]
                        PixivImage(
                            url = illust.squareImageUrl.ifBlank { illust.mediumImageUrl.ifBlank { illust.imageUrl } },
                            contentDescription = illust.title,
                            contentScale = ContentScale.Crop,
                            thumbnail = true,
                            maxDecodeDimensionPx = 384,
                            modifier = Modifier.weight(1f).height(118.dp),
                        )
                    }
                    repeat(3 - previewCount) {
                        Spacer(modifier = Modifier.weight(1f).height(118.dp))
                    }
                }
            }
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AvatarImage(url = user.profileImageUrl, name = user.name, size = 62.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        user.name,
                        color = MiuixTheme.colorScheme.onBackground,
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "@${user.account}",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FollowPill(isFollowed = user.isFollowed)
            }
        }
    }
}

@Composable
internal fun SearchResultGrid(
    page: Int,
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onIllustSelected: ((Illust) -> Unit)? = null,
) {
    SearchResultGrid(
        page = page,
        state = state.searchUiState,
        viewModel = viewModel,
        onIllustSelected = onIllustSelected,
    )
}
