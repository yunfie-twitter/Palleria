package com.yunfie.illustia.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
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
import com.yunfie.illustia.SearchUiState
import com.yunfie.illustia.data.pixiv.SuggestionStore
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.SearchAgeRestriction
import com.yunfie.illustia.models.SearchBookmarkFilter
import com.yunfie.illustia.models.SearchDuration
import com.yunfie.illustia.models.SearchSort
import com.yunfie.illustia.models.SearchTarget
import com.yunfie.illustia.models.SearchWorkType
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.nativebridge.NativeIntentEvent
import com.yunfie.illustia.nativebridge.NativeIntentRouter
import com.yunfie.illustia.searchUiState
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.IllustGridSkeleton
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.ScrollBlurScaffold
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
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
    state: SearchUiState,
    viewModel: IllustiaViewModel,
    focusRequest: Int = 0,
    onFocusRequestHandled: () -> Unit = {},
    widgetSelectionMode: Boolean = false,
    isResultRoute: Boolean = false,
    onIllustSelected: ((Illust) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onBackFromResults: (() -> Unit)? = null,
    onNavigateToResults: ((String) -> Unit)? = null,
) {
    var searchExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) searchExpanded = true
    }
    val repository = remember(viewModel) { viewModel.uiRepository() }
    val suggestionStore = remember(repository) { SuggestionStore(repository) }
    val autocompleteSuggestions by suggestionStore.autoWords.collectAsStateWithLifecycle()

    val isResultMode =
        remember(state.activeSearchWord, isResultRoute, widgetSelectionMode) {
            if (widgetSelectionMode) {
                state.activeSearchWord.isNotBlank()
            } else {
                isResultRoute
            }
        }

    val liveQuery = (if (searchExpanded) state.searchDraft else state.activeSearchWord).trim()
    LaunchedEffect(liveQuery, searchExpanded) {
        if (searchExpanded && liveQuery.isNotEmpty()) {
            delay(250)
            suggestionStore.fetch(liveQuery)
        } else if (!searchExpanded || liveQuery.isEmpty()) {
            suggestionStore.fetch("")
        }
    }

    val suggestions =
        remember(state.settings.searchHistory, state.recommendedTags, autocompleteSuggestions) {
            val result = LinkedHashSet<String>(18)
            val history = state.settings.searchHistory
            val historyLimit = minOf(6, history.size)
            for (i in 0 until historyLimit) {
                result.add(history[i])
            }
            for (tag in state.recommendedTags) {
                if (result.size >= 18) break
                result.add(tag)
            }
            for (word in autocompleteSuggestions) {
                if (result.size >= 18) break
                result.add(word)
            }
            result.toList()
        }

    LaunchedEffect(state.sessionReady, state.settings.refreshToken, state.recommendedTagsFetchedAtMillis) {
        if (state.sessionReady) {
            viewModel.refreshRecommendedTags()
        }
    }

    val onClearResults =
        remember(isResultRoute, onBackFromResults, viewModel) {
            {
                if (isResultRoute) {
                    onBackFromResults?.invoke() ?: viewModel.clearSearchResults()
                } else {
                    viewModel.clearSearchResults()
                }
            }
        }
    val onExpandedChange: (Boolean) -> Unit =
        remember(state.activeSearchWord, onClearResults, viewModel) {
            { expanded ->
                searchExpanded = expanded
                if (!expanded) {
                    viewModel.updateSearchDraft(state.activeSearchWord)
                    if (state.activeSearchWord.isBlank()) {
                        onClearResults()
                    }
                }
            }
        }
    val onUpdateDraft: (String) -> Unit = remember(viewModel) { { viewModel.updateSearchDraft(it) } }
    val onSubmit: (String) -> Unit =
        remember(onClearResults, onNavigateToResults, viewModel) {
            { word ->
                val trimmed = word.trim()
                if (trimmed.isBlank()) {
                    onClearResults()
                } else {
                    val nativeEvent = NativeIntentRouter.parseText(trimmed)
                    val isDirectSearchEvent =
                        nativeEvent is NativeIntentEvent.Artwork ||
                            nativeEvent is NativeIntentEvent.User ||
                            nativeEvent is NativeIntentEvent.Tag
                    if (isDirectSearchEvent) {
                        viewModel.submitSearch(trimmed)
                    } else if (onNavigateToResults != null) {
                        onNavigateToResults.invoke(trimmed)
                    } else {
                        viewModel.submitSearch(trimmed)
                    }
                }
                searchExpanded = false
            }
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
            viewModel.updateSearchDraft(state.activeSearchWord)
            if (state.activeSearchWord.isBlank()) {
                onClearResults()
            }
            searchExpanded = false
        }
    } else if (widgetSelectionMode && isResultMode) {
        BackHandler(enabled = true) {
            viewModel.clearSearchResults()
        }
    } else if (onBackFromResults != null) {
        PredictiveBackGestureHandler(enabled = true) {
            onBackFromResults()
        }
    } else if (onBack != null) {
        PredictiveBackGestureHandler(enabled = true) {
            onBack()
        }
    }

    val scheme = MiuixTheme.colorScheme
    val performHaptic = rememberHapticFeedbackAction()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = scheme.surface,
    ) {
        ScrollBlurScaffold(
            modifier = Modifier.fillMaxSize(),
            enabled = state.settings.isFeatureEnabled(FeatureFlag.TopScrollBlur) && !isResultMode && !searchExpanded,
            scrollBehavior = MiuixScrollBehavior(),
            scrollFraction = {
                val grid = viewModel.searchBrowseGridState
                if (grid.firstVisibleItemIndex > 0) 1f else (grid.firstVisibleItemScrollOffset / 64f).coerceIn(0f, 1f)
            },
            header = {
                val hasBackButton = isResultMode || onBack != null
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(
                                start = if (hasBackButton && !searchExpanded) 4.dp else 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 6.dp,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(
                        visible = hasBackButton && !searchExpanded,
                        enter = fadeIn(tween(200)) + expandHorizontally(),
                        exit = fadeOut(tween(200)) + shrinkHorizontally(),
                    ) {
                        HeaderIcon(
                            MiuixIcons.Back,
                            onClick = {
                                performHaptic(AppHapticEffect.Click)
                                if (isResultMode) {
                                    (onBackFromResults ?: onClearResults).invoke()
                                } else {
                                    onBack?.invoke()
                                }
                            },
                            modifier = Modifier.height(56.dp),
                        )
                    }
                    SearchToolbar(
                        focusRequest = focusRequest,
                        onFocusRequestHandled = onFocusRequestHandled,
                        value =
                            if (searchExpanded) {
                                state.searchDraft
                            } else if (isResultMode) {
                                state.activeSearchWord
                            } else {
                                state.searchDraft
                            },
                        expanded = searchExpanded,
                        suggestions = suggestions,
                        historyCount = state.settings.searchHistory.size,
                        onRemoveHistoryItem = { viewModel.removeSearchHistoryItem(it) },
                        onExpandedChange = { expanded ->
                            if (expanded && state.searchDraft.isBlank()) {
                                onUpdateDraft(state.activeSearchWord)
                            }
                            onExpandedChange(expanded)
                        },
                        onValueChange = onUpdateDraft,
                        onSearch = {
                            val target = state.searchDraft.ifBlank { state.activeSearchWord }
                            onSubmit(target)
                        },
                        onSuggestionClick = {
                            onSubmit(it)
                        },
                        onCancel = {
                            if (isResultMode) {
                                onUpdateDraft(state.activeSearchWord)
                            } else {
                                onUpdateDraft("")
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        ) {
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
                            onSearch = onSubmit,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultsArea(
    state: SearchUiState,
    viewModel: IllustiaViewModel,
    widgetSelectionMode: Boolean = false,
    onIllustSelected: ((Illust) -> Unit)? = null,
) {
    var showOptionsSheet by rememberSaveable { mutableStateOf(false) }
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
    val initialPage =
        remember(state.activeSearchWord) {
            state.searchSelectedTab.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        }
    val resultPagerState =
        rememberPagerState(
            initialPage = initialPage,
            pageCount = { tabs.size },
        )
    val coroutineScope = rememberCoroutineScope()
    val selectedResultTab = resultPagerState.currentPage

    LaunchedEffect(selectedResultTab) {
        if (state.searchSelectedTab != selectedResultTab) {
            viewModel.updateSearchSelectedTab(selectedResultTab)
        }
    }

    LaunchedEffect(state.searchSelectedTab, tabs.size) {
        val targetPage = state.searchSelectedTab.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        if (resultPagerState.currentPage != targetPage) {
            resultPagerState.scrollToPage(targetPage)
        }
    }

    val performHaptic = rememberHapticFeedbackAction()
    val isSearchFilterActive =
        state.settings.searchSort != SearchSort.DateDesc ||
            state.settings.searchTarget != SearchTarget.PartialTags ||
            state.settings.searchWorkType != SearchWorkType.Artworks ||
            state.settings.searchDuration != SearchDuration.All ||
            state.settings.searchBookmarkFilter != SearchBookmarkFilter.None ||
            state.settings.searchAgeRestriction != SearchAgeRestriction.All ||
            state.settings.hideAiWorks

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
                    if (index != selectedResultTab) {
                        performHaptic(AppHapticEffect.Toggle)
                    }
                    viewModel.updateSearchSelectedTab(index)
                    coroutineScope.launch { resultPagerState.animateScrollToPage(index) }
                },
                modifier = Modifier.weight(1f).pointerHoverIcon(PointerIcon.Hand),
            )
            Box {
                IconButton(modifier = Modifier.pointerHoverIcon(PointerIcon.Hand), onClick = {
                    performHaptic(AppHapticEffect.Click)
                    showOptionsSheet = true
                }) {
                    Icon(
                        imageVector = MiuixIcons.Filter,
                        contentDescription = stringResource(R.string.search_options),
                        tint = if (isSearchFilterActive) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                    )
                }
                if (isSearchFilterActive) {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .align(Alignment.TopEnd)
                                .padding(end = 4.dp, top = 4.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.primary),
                    )
                }
            }
        }

        PullToRefresh(
            isRefreshing = state.isSearchRefreshing,
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

@Composable
fun SearchScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    focusRequest: Int = 0,
    onFocusRequestHandled: () -> Unit = {},
    widgetSelectionMode: Boolean = false,
    isResultRoute: Boolean = false,
    onIllustSelected: ((Illust) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onBackFromResults: (() -> Unit)? = null,
    onNavigateToResults: ((String) -> Unit)? = null,
) {
    SearchScreen(
        state = state.searchUiState,
        focusRequest = focusRequest,
        onFocusRequestHandled = onFocusRequestHandled,
        viewModel = viewModel,
        widgetSelectionMode = widgetSelectionMode,
        isResultRoute = isResultRoute,
        onIllustSelected = onIllustSelected,
        onBack = onBack,
        onBackFromResults = onBackFromResults,
        onNavigateToResults = onNavigateToResults,
    )
}
