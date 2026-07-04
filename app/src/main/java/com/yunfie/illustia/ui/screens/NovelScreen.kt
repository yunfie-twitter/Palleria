package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.NovelTextContent
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.AutoLoadMoreEffect
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.PrefetchPixivImages
import com.yunfie.illustia.ui.components.StateBanner
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val prefetchUrls = remember(items) {
        items.asSequence().take(12).map { it.coverUrl }.toList()
    }
    PrefetchPixivImages(prefetchUrls, enabled = settings.prefetchImages)
    AutoLoadMoreEffect(
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
                    IconButton(onClick = viewModel::refreshNovels) {
                        Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.dialog_reload))
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        PullToRefresh(
            isRefreshing = loadState == LoadState.Loading && items.isNotEmpty(),
            onRefresh = { viewModel.refreshNovels() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(1),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = scaffoldPadding.calculateTopPadding() + 8.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (items.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { StateBanner(loadState) }
                }
                if (items.isEmpty() && loadState != LoadState.Loading && loadState !is LoadState.Error) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(stringResource(R.string.novel_empty))
                    }
                }

                gridItems(items, key = { it.id }, contentType = { "novel_card" }) { novel ->
                    NovelCard(novel = novel, onClick = { viewModel.openNovel(novel) })
                }

                if (!settings.autoLoadMore && nextUrl != null) {
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

@Composable
fun NovelReaderScreen(
    novel: NovelPreview?,
    text: NovelTextContent?,
    loadState: LoadState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val currentNovel = novel ?: return
    val scrollBehavior = MiuixScrollBehavior()
    val pages = remember(text?.text) {
        text?.text?.let(::parseNovelPages).orEmpty().ifEmpty { listOf(NovelPage(emptyList())) }
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = currentNovel.title,
                largeTitle = currentNovel.title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    IconButton(onClick = onRetry) {
                        Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.dialog_reload))
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        when {
            loadState == LoadState.Loading && text == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }
            text != null -> {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { pageIndex ->
                    NovelReaderPage(
                        page = pages[pageIndex],
                        pageIndex = pageIndex,
                        pageCount = pages.size,
                        viewModel = viewModel,
                        uriHandler = uriHandler,
                        onJumpPage = { targetPage ->
                            if (targetPage in pages.indices) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(targetPage)
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        modifier = Modifier.padding(scaffoldPadding),
                    )
                }
            }
            else -> {
                Box(
                    modifier = Modifier
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
}
