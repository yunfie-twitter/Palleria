package com.yunfie.illustia.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.SearchBookmarkFilter
import com.yunfie.illustia.models.SearchDuration
import com.yunfie.illustia.models.SearchSort
import com.yunfie.illustia.models.SearchTarget
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.nativebridge.NativeIntentEvent
import com.yunfie.illustia.nativebridge.NativeIntentRouter
import com.yunfie.illustia.data.pixiv.SuggestionStore
import com.yunfie.illustia.ui.components.*
import kotlinx.coroutines.flow.collect
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import kotlinx.coroutines.launch

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

    val liveQuery = if (searchExpanded) state.searchDraft else state.activeSearchWord
    LaunchedEffect(liveQuery) {
        suggestionStore.fetch(liveQuery)
    }

    val suggestions = remember(state.settings.searchHistory, state.recommendedTags, autocompleteSuggestions) {
        (state.settings.searchHistory.take(6) + state.recommendedTags + autocompleteSuggestions).distinct().take(18)
    }

    LaunchedEffect(state.sessionReady, state.settings.refreshToken, state.recommendedTagsFetchedAtMillis) {
        if (state.sessionReady) {
            viewModel.refreshRecommendedTags()
        }
    }

    val onClearResults = { viewModel.clearSearchResults() }
    val onExpandedChange: (Boolean) -> Unit = { searchExpanded = it }
    val onUpdateDraft: (String) -> Unit = { viewModel.updateSearchDraft(it) }
    val onSubmit: (String) -> Unit = { viewModel.submitSearch(it) }
    var lastAutoOpenedArtworkUrl by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.searchDraft) {
        val normalized = state.searchDraft.trim()
        val artworkEvent = NativeIntentRouter.parseText(normalized) as? NativeIntentEvent.Artwork
        if (artworkEvent != null && lastAutoOpenedArtworkUrl != normalized) {
            lastAutoOpenedArtworkUrl = normalized
            onSubmit(normalized)
            onExpandedChange(false)
        } else if (artworkEvent == null) {
            lastAutoOpenedArtworkUrl = null
        }
    }

    val contentMode = when {
        searchExpanded -> "suggestions"
        isResultMode -> "results"
        else -> "browse"
    }

    if (isResultMode || searchExpanded) {
        val closeSearch = {
            if (searchExpanded) {
                searchExpanded = false
            } else {
                viewModel.clearSearchResults()
            }
        }
        BackHandler(enabled = true, onBack = closeSearch)
        PredictiveBackHandler(enabled = true) { progress ->
            progress.collect { }
            closeSearch()
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderIcon(
                        MiuixIcons.Back,
                        onClick = onClearResults,
                        modifier = Modifier.height(56.dp),
                    )
                    SearchToolbar(
                        value = state.activeSearchWord,
                        expanded = false,
                        suggestions = suggestions,
                        historyCount = state.settings.searchHistory.size,
                        onExpandedChange = onExpandedChange,
                        onValueChange = onUpdateDraft,
                        onSearch = { onSubmit(state.searchDraft.ifBlank { state.activeSearchWord }); onExpandedChange(false) },
                        onClear = { onUpdateDraft("") },
                        onSuggestionClick = { onSubmit(it); onExpandedChange(false) },
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
                    onSearch = { onSubmit(state.searchDraft.ifBlank { state.activeSearchWord }); onExpandedChange(false) },
                    onClear = { onUpdateDraft("") },
                    onSuggestionClick = { onSubmit(it); onExpandedChange(false) },
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
                    "suggestions" -> Spacer(Modifier.fillMaxSize())
                    "results" -> SearchResultsArea(
                        state = state,
                        viewModel = viewModel,
                        widgetSelectionMode = widgetSelectionMode,
                        onIllustSelected = onIllustSelected,
                    )
                    else -> BrowseArea(
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

@Composable
private fun SearchResultsArea(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    widgetSelectionMode: Boolean = false,
    onIllustSelected: ((Illust) -> Unit)? = null,
) {
    var showOptionsSheet by remember { mutableStateOf(false) }
    val tabIllust = stringResource(R.string.search_tab_illust)
    val tabUser = stringResource(R.string.search_tab_user)
    val tabs = remember(state.settings.searchUsersEnabled, widgetSelectionMode, tabIllust, tabUser) {
        if (widgetSelectionMode) listOf(tabIllust)
        else if (state.settings.searchUsersEnabled) listOf(tabIllust, tabUser) else listOf(tabIllust)
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
            isRefreshing = false,
            onRefresh = { viewModel.submitSearch() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.loadState == LoadState.Loading && state.searchItems.isEmpty() && state.userSearchItems.isEmpty()) {
                IllustGridSkeleton(columns = adaptiveIllustColumns(state.settings))
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

