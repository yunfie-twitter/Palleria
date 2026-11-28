package com.yunfie.illustia.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.isEmptyAfterLoad
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.action_back
import com.yunfie.illustia.resources.action_load_more
import com.yunfie.illustia.resources.dialog_reload
import com.yunfie.illustia.resources.nav_novel
import com.yunfie.illustia.resources.novel_empty
import com.yunfie.illustia.resources.novel_filter_all
import com.yunfie.illustia.resources.novel_filter_completed
import com.yunfie.illustia.resources.novel_filter_later
import com.yunfie.illustia.resources.novel_filter_reading
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.AutoLoadMoreEffect
import com.yunfie.illustia.ui.components.CenteredErrorState
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.PrefetchNovelGridImages
import com.yunfie.illustia.ui.components.PrefetchPixivImages
import com.yunfie.illustia.ui.components.ScrollablePullToRefresh
import com.yunfie.illustia.ui.components.StateBanner
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.animatedGridPlacement
import com.yunfie.illustia.ui.components.onTopBarTap
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import com.yunfie.illustia.ui.components.pinchToChangeColumns
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import com.yunfie.illustia.ui.components.rememberIllustSkeletonShimmer
import com.yunfie.illustia.ui.components.smoothScrollToTop
import com.yunfie.illustia.ui.screens.novel.NovelListActions
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.lazy.grid.items as gridItems

private enum class NovelFilterTab(
    val labelRes: StringResource,
) {
    All(Res.string.novel_filter_all),
    Reading(Res.string.novel_filter_reading),
    Later(Res.string.novel_filter_later),
    Completed(Res.string.novel_filter_completed),
}

@Composable
fun NovelScreen(
    items: List<NovelPreview>,
    loadState: LoadState,
    nextUrl: String?,
    settings: AppSettings,
    viewModel: NovelListActions,
    onBack: () -> Unit,
    isNovelRefreshing: Boolean = false,
    isNovelPaginating: Boolean = false,
) {
    val gridState = remember { LazyGridState() }
    val scrollBehavior = MiuixScrollBehavior()
    val performHaptic = rememberHapticFeedbackAction()
    val coroutineScope = rememberCoroutineScope()
    var selectedFilter by rememberSaveable { mutableStateOf(NovelFilterTab.All) }
    val filteredItems =
        remember(items, selectedFilter, settings.novelProgress) {
            when (selectedFilter) {
                NovelFilterTab.All -> {
                    items
                }

                NovelFilterTab.Reading -> {
                    items.filter {
                        settings.novelProgress[it.id]?.status == com.yunfie.illustia.models.NovelReadingStatus.Reading
                    }
                }

                NovelFilterTab.Later -> {
                    items.filter {
                        settings.novelProgress[it.id]?.status == com.yunfie.illustia.models.NovelReadingStatus.Later
                    }
                }

                NovelFilterTab.Completed -> {
                    items.filter {
                        settings.novelProgress[it.id]?.status == com.yunfie.illustia.models.NovelReadingStatus.Completed
                    }
                }
            }
        }
    PrefetchNovelGridImages(
        items = items,
        gridState = gridState,
        enabled = settings.prefetchImages,
    )
    val columns = adaptiveIllustColumns(settings)
    val pinchEnabled = settings.gridPinchToZoom
    val showInitialSkeletons = items.isEmpty() && loadState == LoadState.Loading
    val showPaginationSkeletons = settings.autoLoadMore && isNovelPaginating && selectedFilter == NovelFilterTab.All
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
        isLoading = isNovelPaginating || loadState == LoadState.Loading,
        onLoadMore = viewModel::loadMoreNovels,
    )

    LaunchedEffect(Unit) {
        if (items.isEmpty()) {
            viewModel.refreshNovels()
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.nav_novel),
                largeTitle = stringResource(Res.string.nav_novel),
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
                    IconButton(modifier = Modifier.pointerHoverIcon(PointerIcon.Hand), onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    IconButton(
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        onClick = { viewModel.refreshNovels(forceRefresh = true) },
                    ) {
                        Icon(MiuixIcons.Refresh, contentDescription = stringResource(Res.string.dialog_reload))
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        ScrollablePullToRefresh(
            isRefreshing = isNovelRefreshing,
            onRefresh = { viewModel.refreshNovels(forceRefresh = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            val emptyError = (loadState as? LoadState.Error)?.takeIf { filteredItems.isEmpty() }
            if (emptyError != null) {
                CenteredErrorState(
                    message = emptyError.message,
                    onRetry = { viewModel.refreshNovels(forceRefresh = true) },
                )
                return@ScrollablePullToRefresh
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pinchToChangeColumns(
                            enabled = pinchEnabled,
                            currentColumns = columns,
                            onColumnsChange = viewModel::updateVerticalColumnCount,
                        ).nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding =
                    PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = scaffoldPadding.calculateTopPadding() + 8.dp,
                        bottom = 24.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FilterPillRow(
                        entries = NovelFilterTab.entries,
                        selected = selectedFilter,
                        label = { stringResource(it.labelRes) },
                        onSelect = { selectedFilter = it },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    )
                }
                if (showInitialSkeletons) {
                    items(columns * 3, key = { "novel_initial_skeleton_$it" }, contentType = { "novel_skeleton" }) {
                        NovelCardSkeleton(shimmerValue = shimmer)
                    }
                }
                if (filteredItems.isEmpty() && !showInitialSkeletons) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        StateBanner(loadState, onRetry = { viewModel.refreshNovels(forceRefresh = true) })
                    }
                }
                if (isEmptyAfterLoad(filteredItems.isEmpty(), loadState)) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(stringResource(Res.string.novel_empty))
                    }
                }

                gridItems(filteredItems, key = { it.id }, contentType = { "novel_card" }) { novel ->
                    val progress = settings.novelProgress[novel.id]
                    NovelCard(
                        modifier = animatedGridPlacement(),
                        novel = novel,
                        progress = progress,
                        onClick = { viewModel.openNovel(novel) },
                        onToggleBookmark = { viewModel.toggleNovelBookmark(novel) },
                        onStatusToggle = {
                            val next =
                                when (progress?.status) {
                                    com.yunfie.illustia.models.NovelReadingStatus.Reading -> {
                                        com.yunfie.illustia.models.NovelReadingStatus.Later
                                    }

                                    com.yunfie.illustia.models.NovelReadingStatus.Later -> {
                                        com.yunfie.illustia.models.NovelReadingStatus.Completed
                                    }

                                    com.yunfie.illustia.models.NovelReadingStatus.Completed -> {
                                        com.yunfie.illustia.models.NovelReadingStatus.Unread
                                    }

                                    else -> {
                                        com.yunfie.illustia.models.NovelReadingStatus.Reading
                                    }
                                }
                            viewModel.setNovelReadingStatus(novel.id, next)
                        },
                    )
                }

                if (showPaginationSkeletons) {
                    items(columns, key = { "novel_paginating_skeleton_$it" }, contentType = { "novel_skeleton" }) {
                        NovelCardSkeleton(shimmerValue = shimmer)
                    }
                } else if (!settings.autoLoadMore && nextUrl != null && selectedFilter == NovelFilterTab.All) {
                    item(key = "novel_load_more_button", span = { GridItemSpan(maxLineSpan) }) {
                        Button(
                            onClick = viewModel::loadMoreNovels,
                            enabled = !isNovelPaginating,
                            modifier = Modifier.fillMaxWidth(),
                            colors = overlayActionButtonColors(),
                        ) {
                            if (isNovelPaginating) {
                                androidx.compose.foundation.layout.Row(
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

private const val DARK_LUMINANCE_THRESHOLD = 0.5f
