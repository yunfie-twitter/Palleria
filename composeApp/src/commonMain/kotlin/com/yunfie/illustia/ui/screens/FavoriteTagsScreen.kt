package com.yunfie.illustia.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.isEmptyAfterLoad
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.action_back
import com.yunfie.illustia.resources.action_delete
import com.yunfie.illustia.resources.action_load_more
import com.yunfie.illustia.resources.data_items_count
import com.yunfie.illustia.resources.dialog_reload
import com.yunfie.illustia.resources.favorite_tags_delete_confirm
import com.yunfie.illustia.resources.favorite_tags_delete_title
import com.yunfie.illustia.resources.favorite_tags_empty
import com.yunfie.illustia.resources.favorite_tags_no_selection
import com.yunfie.illustia.resources.favorite_tags_title
import com.yunfie.illustia.resources.favorite_tags_works_not_found
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.AutoLoadMoreEffect
import com.yunfie.illustia.ui.components.BackNavigationHandler
import com.yunfie.illustia.ui.components.CenteredErrorState
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.IllustCardSkeleton
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PrefetchIllustGridImages
import com.yunfie.illustia.ui.components.ScrollablePullToRefresh
import com.yunfie.illustia.ui.components.StateBanner
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.adaptiveMainNavigationContentPadding
import com.yunfie.illustia.ui.components.miuixClickable
import com.yunfie.illustia.ui.components.onTopBarTap
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import com.yunfie.illustia.ui.components.rememberIllustSkeletonShimmer
import com.yunfie.illustia.ui.components.smoothScrollToTop
import com.yunfie.illustia.ui.screens.favorites.FavoriteTagsActions
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
fun FavoriteTagsScreen(
    state: IllustiaUiState,
    viewModel: FavoriteTagsActions,
    onBack: () -> Unit,
) {
    BackNavigationHandler(onBack = onBack)

    val gridState = rememberLazyGridState()
    val feedHighQuality = state.settings.useHighQualityFeedImages
    val showAiBadge = state.settings.showAiBadge

    // 選択中タグ（最初のタグをデフォルト選択）
    var selectedTag by remember(state.settings.favoriteTags) {
        mutableStateOf(state.settings.favoriteTags.firstOrNull())
    }

    PrefetchIllustGridImages(
        items = state.watchlistItems,
        gridState = gridState,
        enabled = state.settings.prefetchImages,
        highQualityImages = feedHighQuality,
        keyPrefix = "fav_",
    )

    // タグが選択されたら自動ロード
    LaunchedEffect(selectedTag) {
        val tag = selectedTag ?: return@LaunchedEffect
        if (state.watchlistItems.isEmpty() || state.activeWatchlistTag != tag) {
            viewModel.loadWatchlistTag(tag, false)
        }
    }

    // 削除確認ダイアログ
    var tagToDelete by remember { mutableStateOf<String?>(null) }
    tagToDelete?.let { tag ->
        MiuixConfirmDialog(
            show = true,
            title = stringResource(Res.string.favorite_tags_delete_title),
            summary = stringResource(Res.string.favorite_tags_delete_confirm, tag),
            confirmText = stringResource(Res.string.action_delete),
            destructive = true,
            onConfirm = {
                viewModel.toggleFavoriteTag(tag)
                if (selectedTag == tag) {
                    selectedTag = state.settings.favoriteTags.firstOrNull { it != tag }
                }
                tagToDelete = null
            },
            onDismiss = { tagToDelete = null },
        )
    }

    val subtitle =
        selectedTag?.let { "#$it / ${stringResource(Res.string.data_items_count, state.watchlistItems.size)}" }
            ?: stringResource(Res.string.favorite_tags_no_selection)

    val coroutineScope = rememberCoroutineScope()
    val performHaptic = rememberHapticFeedbackAction()

    val scrollBehavior = MiuixScrollBehavior()
    val columns = adaptiveIllustColumns(state.settings)
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.favorite_tags_title),
                largeTitle = stringResource(Res.string.favorite_tags_title),
                subtitle = subtitle,
                scrollBehavior = scrollBehavior,
                modifier =
                    Modifier.onTopBarTap(
                        navIconWidth = 64.dp,
                        actionsWidth = 56.dp,
                        hasBottomContent = false,
                    ) {
                        performHaptic(AppHapticEffect.Click)
                        coroutineScope.launch {
                            gridState.smoothScrollToTop(scrollBehavior)
                        }
                    },
                navigationIcon = {
                    HeaderIcon(
                        icon = MiuixIcons.Back,
                        onClick = onBack,
                        contentDescription = stringResource(Res.string.action_back),
                    )
                },
                actions = {
                    if (selectedTag != null) {
                        HeaderIcon(
                            icon = MiuixIcons.Refresh,
                            onClick = { selectedTag?.let { viewModel.loadWatchlistTag(it, forceRefresh = true) } },
                            contentDescription = stringResource(Res.string.dialog_reload),
                        )
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        ScrollablePullToRefresh(
            isRefreshing = state.isWatchlistRefreshing,
            onRefresh = { selectedTag?.let { viewModel.loadWatchlistTag(it, forceRefresh = true) } },
            modifier = Modifier.fillMaxSize(),
        ) {
            AutoLoadMoreEffect(
                gridState = gridState,
                enabled = state.settings.autoLoadMore,
                nextUrl = state.watchlistNextUrl,
                isLoading = state.isWatchlistPaginating || state.loadState == LoadState.Loading,
                onLoadMore = viewModel::loadMoreWatchlist,
            )
            val showInitialLoading = state.watchlistItems.isEmpty() && state.loadState == LoadState.Loading
            val showPaginating = state.settings.autoLoadMore && state.isWatchlistPaginating
            val shimmer = if (showInitialLoading || showPaginating) rememberIllustSkeletonShimmer() else null
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .background(MiuixTheme.colorScheme.surface),
                contentPadding =
                    PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = scaffoldPadding.calculateTopPadding() + 8.dp,
                        bottom = adaptiveMainNavigationContentPadding(),
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── タグチップ一覧 ────────────────────────────
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (state.settings.favoriteTags.isEmpty()) {
                        EmptyState(stringResource(Res.string.favorite_tags_empty))
                    } else {
                        TagChipRow(
                            tags = state.settings.favoriteTags,
                            selectedTag = selectedTag,
                            onSelectTag = { tag ->
                                selectedTag = tag
                            },
                            onDeleteTag = { tag -> tagToDelete = tag },
                        )
                    }
                }

                // ── 検索結果 ──────────────────────────────────
                if (selectedTag != null) {
                    if (showInitialLoading) {
                        items(
                            count = 6,
                            key = { "fav_initial_skeleton_$it" },
                            contentType = { "illust_skeleton" },
                        ) {
                            IllustCardSkeleton(shimmerValue = shimmer)
                        }
                    } else if (state.loadState is LoadState.Error && state.watchlistItems.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, contentType = "state_banner") {
                            CenteredErrorState(
                                message = (state.loadState as LoadState.Error).message,
                                onRetry = { selectedTag?.let { viewModel.loadWatchlistTag(it, forceRefresh = true) } },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp),
                            )
                        }
                    } else {
                        item(span = { GridItemSpan(maxLineSpan) }, contentType = "state_banner") {
                            StateBanner(
                                state.loadState,
                                onRetry = { selectedTag?.let { viewModel.loadWatchlistTag(it, forceRefresh = true) } },
                            )
                        }
                    }

                    val currentSelectedTag = selectedTag
                    if (isEmptyAfterLoad(state.watchlistItems.isEmpty(), state.loadState) &&
                        currentSelectedTag != null
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty_state") {
                            EmptyState(stringResource(Res.string.favorite_tags_works_not_found, currentSelectedTag))
                        }
                    }

                    gridItems(
                        items = state.watchlistItems,
                        key = { "fav_${it.id}" },
                        contentType = { "illust_card" },
                    ) { illust ->
                        IllustCard(
                            illust = illust,
                            onBookmark = { viewModel.toggleBookmarkFromCard(illust) },
                            onClick = { viewModel.openIllustFromCard(illust) },
                            onLongClick = { viewModel.onIllustLongPressById(illust.id) },
                            highQualityImages = feedHighQuality,
                            showAiBadge = showAiBadge,
                        )
                    }

                    if (state.settings.autoLoadMore && state.isWatchlistPaginating) {
                        items(
                            count = columns,
                            key = { "fav_paginating_skeleton_$it" },
                            contentType = { "illust_skeleton" },
                        ) {
                            IllustCardSkeleton(shimmerValue = shimmer)
                        }
                    } else if (!state.settings.autoLoadMore && state.watchlistNextUrl != null) {
                        item(key = "watchlist_load_more_button", span = { GridItemSpan(maxLineSpan) }, contentType = "load_more_button") {
                            Button(
                                onClick = viewModel::loadMoreWatchlist,
                                enabled = !state.isWatchlistPaginating,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                colors = overlayActionButtonColors(),
                            ) {
                                if (state.isWatchlistPaginating) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        LoadingIndicator(modifier = Modifier.size(16.dp))
                                        Text(stringResource(Res.string.action_load_more))
                                    }
                                } else {
                                    Text(stringResource(Res.string.action_load_more))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  タグチップ行（横スクロール + 長押し削除）
// ─────────────────────────────────────────────────────────
@Composable
private fun TagChipRow(
    tags: List<String>,
    selectedTag: String?,
    onSelectTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
) {
    // タグが多い場合は折り返しレイアウト
    val chunkedTags = remember(tags) { tags.chunked(4) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chunkedTags.forEach { rowTags ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowTags.forEach { tag ->
                    val isSelected = selectedTag == tag
                    TagChip(
                        tag = tag,
                        isSelected = isSelected,
                        onSelect = { onSelectTag(tag) },
                        onDelete = { onDeleteTag(tag) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // 余白埋め
                repeat(4 - rowTags.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TagChip(
    tag: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = isSelected,
        transitionSpec = {
            fadeIn(tween(160)) togetherWith fadeOut(tween(120))
        },
        label = "tag-chip-$tag",
        modifier = modifier,
    ) { selected ->
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .squircleSurface(
                        if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainer,
                        20.dp,
                    ).miuixClickable(onClick = onSelect),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "#$tag",
                    color = if (selected) Color.White else MiuixTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                // 削除ボタン（×）
                Box(
                    modifier =
                        Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (selected) {
                                    Color.White.copy(alpha = 0.25f)
                                } else {
                                    MiuixTheme.colorScheme.surfaceContainerHigh
                                },
                            ).miuixClickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = stringResource(Res.string.action_delete),
                        tint = if (selected) Color.White else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
    }
}
