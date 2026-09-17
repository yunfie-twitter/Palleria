package com.yunfie.illustia.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.NovelTextContent
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.AutoLoadMoreEffect
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.PrefetchPixivImages
import com.yunfie.illustia.ui.components.StateBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.lazy.grid.items as gridItems

private enum class NovelFilterTab(
    val labelRes: Int,
) {
    All(R.string.novel_filter_all),
    Reading(R.string.novel_filter_reading),
    Later(R.string.novel_filter_later),
    Completed(R.string.novel_filter_completed),
}

@Composable
fun NovelScreen(
    items: List<NovelPreview>,
    loadState: LoadState,
    nextUrl: String?,
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    val gridState = remember { LazyGridState() }
    val scrollBehavior = MiuixScrollBehavior()
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
    val prefetchUrls =
        remember(items) {
            items
                .asSequence()
                .take(12)
                .map { it.coverUrl }
                .toList()
        }
    PrefetchPixivImages(prefetchUrls, enabled = settings.prefetchImages)
    AutoLoadMoreEffect(
        gridState = gridState,
        enabled = settings.autoLoadMore,
        nextUrl = nextUrl,
        isLoading = loadState == LoadState.Loading,
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
                title = stringResource(R.string.nav_novel),
                largeTitle = stringResource(R.string.nav_novel),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshNovels(forceRefresh = true) }) {
                        Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.dialog_reload))
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        PullToRefresh(
            isRefreshing = loadState == LoadState.Loading && items.isNotEmpty(),
            onRefresh = { viewModel.refreshNovels(forceRefresh = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(1),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.surface)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding =
                    PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = scaffoldPadding.calculateTopPadding() + 8.dp,
                        bottom = 24.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NovelFilterTab.entries.forEach { tab ->
                            val isSelected = selectedFilter == tab
                            NovelMetaPill(
                                text = stringResource(tab.labelRes),
                                backgroundColor =
                                    if (isSelected) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.surfaceContainerHighest
                                    },
                                textColor =
                                    if (isSelected) {
                                        MiuixTheme.colorScheme.onPrimary
                                    } else {
                                        MiuixTheme.colorScheme.onSurface
                                    },
                                onClick = { selectedFilter = tab },
                            )
                        }
                    }
                }
                if (filteredItems.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { StateBanner(loadState) }
                }
                if (filteredItems.isEmpty() && loadState != LoadState.Loading && loadState !is LoadState.Error) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(stringResource(R.string.novel_empty))
                    }
                }

                gridItems(filteredItems, key = { it.id }, contentType = { "novel_card" }) { novel ->
                    val progress = settings.novelProgress[novel.id]
                    NovelCard(
                        novel = novel,
                        progress = progress,
                        onClick = { viewModel.openNovel(novel) },
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

                if (!settings.autoLoadMore && nextUrl != null && selectedFilter == NovelFilterTab.All) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Button(
                            onClick = viewModel::loadMoreNovels,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_load_more))
                        }
                    }
                }
            }
        }
    }
}

private const val DARK_LUMINANCE_THRESHOLD = 0.5f

@Composable
fun NovelReaderScreen(
    novel: NovelPreview?,
    text: NovelTextContent?,
    loadState: LoadState,
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val currentNovel = novel ?: return
    val scrollBehavior = MiuixScrollBehavior()
    val pages by produceState(
        initialValue = listOf(NovelPage(emptyList())),
        key1 = text?.text,
    ) {
        val raw = text?.text
        value =
            if (raw.isNullOrBlank()) {
                listOf(NovelPage(emptyList()))
            } else {
                withContext(Dispatchers.Default) {
                    parseNovelPages(raw)
                }.ifEmpty { listOf(NovelPage(emptyList())) }
            }
    }
    val chapters = remember(pages) { extractChapters(pages) }
    val initialPage =
        remember(currentNovel.id, pages.size) {
            val saved = settings.novelProgress[currentNovel.id]?.lastReadPage ?: 0
            saved.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pages.size })
    val continuousListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    LaunchedEffect(currentNovel.id, pages.size) {
        val saved = settings.novelProgress[currentNovel.id]?.lastReadPage ?: 0
        if (saved > 0 && saved < pages.size) {
            var targetIndex = 0
            for (i in 0 until saved) {
                targetIndex += 1 + pages[i].blocks.size + (if (i < pages.size - 1) 1 else 0)
            }
            continuousListState.scrollToItem(targetIndex)
        }
    }

    val fontSize = settings.novelFontSize
    val lineSpacing = remember(settings.novelLineSpacing) { NovelLineSpacing.fromId(settings.novelLineSpacing) }
    val theme = remember(settings.novelTheme) { NovelTheme.fromId(settings.novelTheme) }
    val layoutMode = remember(settings.novelLayoutMode) { NovelLayoutMode.fromId(settings.novelLayoutMode) }
    val fontFamily = remember(settings.novelFontFamily) { NovelFontFamily.fromId(settings.novelFontFamily) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var showTocSheet by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }

    val isDarkTheme = MiuixTheme.colorScheme.surface.luminance() < DARK_LUMINANCE_THRESHOLD
    DisposableEffect(theme, isDarkTheme) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            val isLightBars =
                when (theme) {
                    NovelTheme.System -> !isDarkTheme
                    NovelTheme.Sepia -> true
                    NovelTheme.Dark, NovelTheme.Black -> false
                }
            insetsController.isAppearanceLightStatusBars = isLightBars
            insetsController.isAppearanceLightNavigationBars = isLightBars
        }
        onDispose {
            val window = (context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
                insetsController.isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }

    val backgroundColor = theme.backgroundColor()
    val textColor = theme.textColor()

    val currentScrollPage =
        remember(continuousListState.firstVisibleItemIndex, pages) {
            val firstVisible = continuousListState.firstVisibleItemIndex
            var count = 0
            var pageIdx = 0
            for (i in pages.indices) {
                val pageItems = 1 + pages[i].blocks.size + (if (i < pages.size - 1) 1 else 0)
                if (firstVisible < count + pageItems) {
                    pageIdx = i
                    break
                }
                count += pageItems
            }
            pageIdx.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        }

    val currentPage =
        when (layoutMode) {
            NovelLayoutMode.Paged, NovelLayoutMode.Vertical -> pagerState.currentPage
            NovelLayoutMode.Scroll -> currentScrollPage
        }

    LaunchedEffect(currentPage, pages.size) {
        if (pages.isNotEmpty() && currentPage in pages.indices) {
            viewModel.updateNovelProgress(
                novelId = currentNovel.id,
                page = currentPage,
                totalPages = pages.size,
            )
        }
    }

    fun jumpToPage(targetPage: Int) {
        if (targetPage in pages.indices) {
            coroutineScope.launch {
                when (layoutMode) {
                    NovelLayoutMode.Paged, NovelLayoutMode.Vertical -> {
                        pagerState.animateScrollToPage(targetPage)
                    }

                    NovelLayoutMode.Scroll -> {
                        var targetIndex = 0
                        for (i in 0 until targetPage) {
                            targetIndex += 1 + pages[i].blocks.size + (if (i < pages.size - 1) 1 else 0)
                        }
                        continuousListState.animateScrollToItem(targetIndex)
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                SmallTopAppBar(
                    title = currentNovel.title,
                    color = backgroundColor,
                    titleColor = textColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.action_close),
                                tint = textColor,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onRetry) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = stringResource(R.string.dialog_reload),
                                tint = textColor,
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = controlsVisible && text != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                NovelBottomControlBar(
                    currentPage = currentPage,
                    pageCount = pages.size,
                    onPageChange = ::jumpToPage,
                    onOpenToc = { showTocSheet = true },
                    onOpenSettings = { showSettingsSheet = true },
                )
            }
        },
    ) { scaffoldPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val readerPadding =
            PaddingValues(
                start = scaffoldPadding.calculateStartPadding(layoutDirection) + 18.dp,
                top = scaffoldPadding.calculateTopPadding(),
                end = scaffoldPadding.calculateEndPadding(layoutDirection) + 18.dp,
                bottom = scaffoldPadding.calculateBottomPadding(),
            )
        when {
            loadState == LoadState.Loading && text == null -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(scaffoldPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }

            text != null -> {
                when (layoutMode) {
                    NovelLayoutMode.Paged -> {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize().background(backgroundColor),
                        ) { pageIndex ->
                            NovelReaderPage(
                                page = pages[pageIndex],
                                pageIndex = pageIndex,
                                pageCount = pages.size,
                                fontSize = fontSize,
                                lineHeightMultiplier = lineSpacing.multiplier,
                                textColor = textColor,
                                fontFamily = fontFamily.fontFamily,
                                viewModel = viewModel,
                                uriHandler = uriHandler,
                                seriesNextId = text.seriesNextId,
                                seriesNextTitle = text.seriesNextTitle,
                                onJumpPage = ::jumpToPage,
                                onOpenSeriesEpisode = { id, title -> viewModel.openNovelById(id, title) },
                                onToggleControls = { controlsVisible = !controlsVisible },
                                scrollBehavior = scrollBehavior,
                                contentPadding = readerPadding,
                            )
                        }
                    }

                    NovelLayoutMode.Scroll -> {
                        NovelReaderContinuousContent(
                            pages = pages,
                            lazyListState = continuousListState,
                            fontSize = fontSize,
                            lineHeightMultiplier = lineSpacing.multiplier,
                            textColor = textColor,
                            fontFamily = fontFamily.fontFamily,
                            viewModel = viewModel,
                            uriHandler = uriHandler,
                            seriesNextId = text.seriesNextId,
                            seriesNextTitle = text.seriesNextTitle,
                            onJumpPage = ::jumpToPage,
                            onOpenSeriesEpisode = { id, title -> viewModel.openNovelById(id, title) },
                            onToggleControls = { controlsVisible = !controlsVisible },
                            scrollBehavior = scrollBehavior,
                            contentPadding = readerPadding,
                            modifier = Modifier.fillMaxSize().background(backgroundColor),
                        )
                    }

                    NovelLayoutMode.Vertical -> {
                        HorizontalPager(
                            state = pagerState,
                            reverseLayout = true,
                            modifier = Modifier.fillMaxSize().background(backgroundColor),
                        ) { pageIndex ->
                            NovelReaderVerticalPage(
                                page = pages[pageIndex],
                                pageIndex = pageIndex,
                                pageCount = pages.size,
                                fontSize = fontSize,
                                lineHeightMultiplier = lineSpacing.multiplier,
                                textColor = textColor,
                                fontFamily = fontFamily.fontFamily,
                                viewModel = viewModel,
                                onJumpPage = ::jumpToPage,
                                onToggleControls = { controlsVisible = !controlsVisible },
                                scrollBehavior = scrollBehavior,
                                contentPadding = readerPadding,
                            )
                        }
                    }
                }
            }

            else -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(scaffoldPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.novel_reader_loading),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }

    NovelTocBottomSheet(
        show = showTocSheet,
        currentPage = currentPage,
        pageCount = pages.size,
        chapters = chapters,
        seriesPrevId = text?.seriesPrevId,
        seriesPrevTitle = text?.seriesPrevTitle,
        seriesNextId = text?.seriesNextId,
        seriesNextTitle = text?.seriesNextTitle,
        onJumpPage = ::jumpToPage,
        onOpenSeriesEpisode = { id, title ->
            viewModel.openNovelById(id, title)
            showTocSheet = false
        },
        onDismiss = { showTocSheet = false },
    )

    NovelSettingsBottomSheet(
        show = showSettingsSheet,
        fontSize = fontSize,
        onFontSizeChange = viewModel::updateNovelFontSize,
        lineSpacing = lineSpacing,
        onLineSpacingChange = { viewModel.updateNovelLineSpacing(it.id) },
        theme = theme,
        onThemeChange = { viewModel.updateNovelTheme(it.id) },
        layoutMode = layoutMode,
        onLayoutModeChange = { newMode ->
            val current = currentPage
            viewModel.updateNovelLayoutMode(newMode.id)
            jumpToPage(current)
        },
        fontFamily = fontFamily,
        onFontFamilyChange = { viewModel.updateNovelFontFamily(it.id) },
        onDismiss = { showSettingsSheet = false },
    )
}
