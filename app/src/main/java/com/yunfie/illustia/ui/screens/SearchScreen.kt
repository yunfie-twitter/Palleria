package com.yunfie.illustia.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.pixiv.SuggestionStore
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.SearchBookmarkFilter
import com.yunfie.illustia.models.SearchDuration
import com.yunfie.illustia.models.SearchSort
import com.yunfie.illustia.models.SearchTarget
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.nativebridge.NativeIntentEvent
import com.yunfie.illustia.nativebridge.NativeIntentRouter
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.IllustGridSkeleton
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Trim
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import androidx.compose.foundation.lazy.grid.items as gridItems

private val SearchSortOptions = SearchSort.entries.toList()
private val SearchTargetOptions = SearchTarget.entries.toList()
private val SearchDurationOptions = SearchDuration.entries.toList()
private val SearchBookmarkFilterOptions = SearchBookmarkFilter.entries.toList()

@Composable
fun SearchScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    widgetSelectionMode: Boolean = false,
    onIllustSelected: ((Illust) -> Unit)? = null,
    onBackFromResults: (() -> Unit)? = null,
) {
    var searchExpanded by remember { mutableStateOf(false) }
    val repository = remember(viewModel) { viewModel.uiRepository() }
    val suggestionStore = remember(repository) { SuggestionStore(repository) }
    val autocompleteSuggestions by suggestionStore.autoWords.collectAsStateWithLifecycle()

    val isResultMode by remember(state.activeSearchWord) {
        derivedStateOf {
            state.activeSearchWord.isNotBlank()
        }
    }

    val liveQuery = (if (searchExpanded) state.searchDraft else state.activeSearchWord).trim()
    LaunchedEffect(liveQuery) {
        if (liveQuery.isNotEmpty()) {
            delay(250)
        }
        suggestionStore.fetch(liveQuery)
    }

    val suggestions =
        remember(state.settings.searchHistory, state.recommendedTags, autocompleteSuggestions) {
            (state.settings.searchHistory.take(6) + state.recommendedTags + autocompleteSuggestions).distinct().take(18)
        }

    LaunchedEffect(state.sessionReady, state.settings.refreshToken, state.recommendedTagsFetchedAtMillis) {
        if (state.sessionReady) {
            viewModel.refreshRecommendedTags()
        }
    }

    val onClearResults = { viewModel.clearSearchResults() }
    val onExpandedChange: (Boolean) -> Unit = { expanded ->
        searchExpanded = expanded
        if (!expanded && state.searchDraft.isBlank()) {
            viewModel.clearSearchResults()
        }
    }
    val onUpdateDraft: (String) -> Unit = { viewModel.updateSearchDraft(it) }
    val onSubmit: (String) -> Unit = { word ->
        if (word.isBlank()) {
            viewModel.clearSearchResults()
        } else {
            viewModel.submitSearch(word)
        }
        searchExpanded = false
    }
    var lastAutoOpenedArtworkUrl by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.searchDraft) {
        val normalized = state.searchDraft.trim()
        val artworkEvent = NativeIntentRouter.parseText(normalized) as? NativeIntentEvent.Artwork
        if (artworkEvent != null && lastAutoOpenedArtworkUrl != normalized) {
            lastAutoOpenedArtworkUrl = normalized
            onSubmit(normalized)
        } else if (artworkEvent == null) {
            lastAutoOpenedArtworkUrl = null
        }
    }

    val contentMode =
        when {
            searchExpanded -> "suggestions"
            isResultMode -> "results"
            else -> "browse"
        }

    if (searchExpanded) {
        BackHandler(enabled = true) {
            if (state.searchDraft.isBlank()) {
                viewModel.clearSearchResults()
            }
            searchExpanded = false
        }
    } else if (onBackFromResults != null) {
        PredictiveBackGestureHandler(enabled = true) {
            viewModel.clearSearchResults()
            onBackFromResults()
        }
    } else if (isResultMode) {
        PredictiveBackGestureHandler(enabled = true) {
            viewModel.clearSearchResults()
        }
    }

    val scheme = MiuixTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = scheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
            // Search bar area (no TopAppBar title spacing)
            if (isResultMode && !searchExpanded) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderIcon(
                        MiuixIcons.Back,
                        onClick = onBackFromResults ?: onClearResults,
                        modifier = Modifier.height(56.dp),
                    )
                    SearchToolbar(
                        value = state.activeSearchWord,
                        expanded = false,
                        suggestions = suggestions,
                        historyCount = state.settings.searchHistory.size,
                        onExpandedChange = { expanded ->
                            if (expanded && state.searchDraft.isBlank()) {
                                onUpdateDraft(state.activeSearchWord)
                            }
                            onExpandedChange(expanded)
                        },
                        onValueChange = { newQuery ->
                            onUpdateDraft(newQuery)
                            onExpandedChange(true)
                        },
                        onSearch = {
                            val target = state.searchDraft.ifBlank { state.activeSearchWord }
                            onSubmit(target)
                        },
                        onSuggestionClick = {
                            onSubmit(it)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                SearchToolbar(
                    value = state.searchDraft,
                    expanded = searchExpanded,
                    suggestions = suggestions,
                    historyCount = state.settings.searchHistory.size,
                    onExpandedChange = onExpandedChange,
                    onValueChange = onUpdateDraft,
                    onSearch = {
                        onSubmit(state.searchDraft)
                    },
                    onSuggestionClick = {
                        onSubmit(it)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // Content area
            AnimatedContent(
                targetState = contentMode,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "search-mode",
                modifier = Modifier.fillMaxSize(),
            ) { mode ->
                when (mode) {
                    "suggestions" -> {
                        Spacer(Modifier.fillMaxSize())
                    }

                    "results" -> {
                        SearchResultsArea(
                            state = state,
                            viewModel = viewModel,
                            widgetSelectionMode = widgetSelectionMode,
                            onIllustSelected = onIllustSelected,
                        )
                    }

                    else -> {
                        BrowseArea(
                            state = state,
                            viewModel = viewModel,
                            showHeader = true,
                            onIllustSelected = onIllustSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultsArea(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    widgetSelectionMode: Boolean = false,
    onIllustSelected: ((Illust) -> Unit)? = null,
) {
    var showOptionsSheet by remember { mutableStateOf(false) }
    val tabIllust = stringResource(R.string.search_tab_illust)
    val tabNovel = stringResource(R.string.search_tab_novel)
    val tabUser = stringResource(R.string.search_tab_user)
    val tabs =
        remember(state.settings.searchUsersEnabled, state.settings.searchWorkType, widgetSelectionMode, tabIllust, tabNovel, tabUser) {
            val workTab = if (state.settings.searchWorkType.isNovel) tabNovel else tabIllust
            if (widgetSelectionMode) {
                listOf(workTab)
            } else if (state.settings.searchUsersEnabled) {
                listOf(workTab, tabUser)
            } else {
                listOf(workTab)
            }
        }
    val resultPagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    val selectedResultTab = resultPagerState.currentPage

    LaunchedEffect(tabs.size) {
        if (selectedResultTab >= tabs.size) {
            resultPagerState.scrollToPage(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TabRowWithContour(
                tabs = tabs,
                selectedTabIndex = selectedResultTab,
                onTabSelected = { index ->
                    coroutineScope.launch { resultPagerState.animateScrollToPage(index) }
                },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showOptionsSheet = true }) {
                Icon(
                    imageVector = MiuixIcons.Filter,
                    contentDescription = stringResource(R.string.search_options),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        }

        PullToRefresh(
            isRefreshing =
                state.loadState == LoadState.Loading &&
                    (state.searchItems.isNotEmpty() || state.searchNovelItems.isNotEmpty() || state.userSearchItems.isNotEmpty()),
            onRefresh = { viewModel.submitSearch(forceRefresh = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (
                state.loadState == LoadState.Loading &&
                state.searchItems.isEmpty() &&
                state.searchNovelItems.isEmpty() &&
                state.userSearchItems.isEmpty()
            ) {
                if (state.settings.searchWorkType.isNovel) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                } else {
                    IllustGridSkeleton(columns = adaptiveIllustColumns(state.settings))
                }
            } else {
                HorizontalPager(state = resultPagerState, modifier = Modifier.fillMaxSize()) { page ->
                    SearchResultGrid(
                        page = page,
                        state = state,
                        viewModel = viewModel,
                        onIllustSelected = onIllustSelected,
                    )
                }
            }
        }
    }

    SearchOptionsSheet(
        show = showOptionsSheet,
        state = state,
        viewModel = viewModel,
        onDismiss = { showOptionsSheet = false },
    )
}
