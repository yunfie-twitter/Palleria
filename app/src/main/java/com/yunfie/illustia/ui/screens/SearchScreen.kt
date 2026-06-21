package com.yunfie.illustia.ui.screens

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
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.LoadState
import com.yunfie.illustia.data.SearchBookmarkFilter
import com.yunfie.illustia.data.SearchDuration
import com.yunfie.illustia.data.SearchSort
import com.yunfie.illustia.data.SearchTarget
import com.yunfie.illustia.data.UserPreview
import com.yunfie.illustia.ui.components.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import kotlinx.coroutines.launch

private val SuggestedTags = listOf(
    "#オリジナル", "#ウマ娘プリティーダービー", "#女の子", "#100日チャレンジ", "#裸足", "#橘ハコミ",
    "#ZenlessZoneZero", "#鳴潮", "#WutheringWaves", "#少女", "#百合", "#イラスト",
)
private val SearchSortOptions = SearchSort.entries.toList()
private val SearchTargetOptions = SearchTarget.entries.toList()
private val SearchDurationOptions = SearchDuration.entries.toList()
private val SearchBookmarkFilterOptions = SearchBookmarkFilter.entries.toList()

@Composable
fun SearchScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
) {
    var searchExpanded by remember { mutableStateOf(false) }
    
    val isResultMode by remember(state.activeSearchWord) {
        derivedStateOf {
            state.activeSearchWord.isNotBlank()
        }
    }

    val suggestions = remember(state.settings.searchHistory) {
        (state.settings.searchHistory.take(6) + SuggestedTags.map { it.removePrefix("#") }).distinct().take(12)
    }

    val onClearResults = { viewModel.clearSearchResults() }
    val onExpandedChange: (Boolean) -> Unit = { searchExpanded = it }
    val onUpdateDraft: (String) -> Unit = { viewModel.updateSearchDraft(it) }
    val onSubmit: (String) -> Unit = { viewModel.submitSearch(it) }

    val contentMode = when {
        searchExpanded -> "suggestions"
        isResultMode -> "results"
        else -> "browse"
    }

    if (isResultMode || searchExpanded) {
        PredictiveBackGestureHandler {
            if (searchExpanded) {
                searchExpanded = false
            } else {
                viewModel.clearSearchResults()
            }
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
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderIcon(MiuixIcons.Back, onClick = onClearResults)
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
                    "results" -> SearchResultsArea(state = state, viewModel = viewModel)
                    else -> BrowseArea(state = state, viewModel = viewModel, showHeader = true)
                }
            }
        }
    }
}

@Composable
private fun SearchResultsArea(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
) {
    var selectedResultTab by remember { mutableStateOf(0) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    val tabIllust = stringResource(R.string.search_tab_illust)
    val tabUser = stringResource(R.string.search_tab_user)
    val tabs = remember(state.settings.searchUsersEnabled, tabIllust, tabUser) {
        if (state.settings.searchUsersEnabled) listOf(tabIllust, tabUser) else listOf(tabIllust)
    }
    val resultPagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(resultPagerState.currentPage) {
        selectedResultTab = resultPagerState.currentPage
    }

    LaunchedEffect(tabs.size) {
        if (selectedResultTab >= tabs.size) {
            selectedResultTab = 0
            resultPagerState.scrollToPage(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TabRow(
                tabs = tabs,
                selectedTabIndex = selectedResultTab,
                onTabSelected = { index ->
                    selectedResultTab = index
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
                CenteredLoadingIndicator()
            } else {
                HorizontalPager(state = resultPagerState, modifier = Modifier.fillMaxSize()) { page ->
                    SearchResultGrid(page = page, state = state, viewModel = viewModel)
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
private fun SearchOptionsSheet(
    show: Boolean,
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onDismiss: () -> Unit,
) {
    if (!show) return
    OverlayBottomSheet(
        show = true,
        title = stringResource(R.string.search_options),
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
        onDismissRequest = onDismiss,
        backgroundColor = LocalBottomSheetBackgroundColor.current,
    ) {
        SearchOptionsContent(
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun SearchOptionsContent(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.search_sort_order), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Bold)
        ChoiceRow(
            values = SearchSortOptions,
            selected = state.settings.searchSort,
            label = { stringResource(it.labelResId) },
            onSelect = viewModel::updateSearchSort,
        )
        Text(stringResource(R.string.search_target), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Bold)
        ChoiceRow(
            values = SearchTargetOptions,
            selected = state.settings.searchTarget,
            label = { stringResource(it.labelResId) },
            onSelect = viewModel::updateSearchTarget,
        )
        Text(stringResource(R.string.search_duration), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Bold)
        ChoiceRow(
            values = SearchDurationOptions,
            selected = state.settings.searchDuration,
            label = { stringResource(it.labelResId) },
            onSelect = viewModel::updateSearchDuration,
        )
        Text(stringResource(R.string.search_bookmark_count), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Bold)
        ChoiceRow(
            values = SearchBookmarkFilterOptions,
            selected = state.settings.searchBookmarkFilter,
            label = { stringResource(it.labelResId) },
            onSelect = viewModel::updateSearchBookmarkFilter,
        )
        SettingSwitchRow(
            title = stringResource(R.string.search_users),
            checked = state.settings.searchUsersEnabled,
            onCheckedChange = viewModel::updateSearchUsersEnabled,
        )
    }
}

@Composable
private fun SearchResultGrid(
    page: Int,
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
) {
    val feedHighQuality = remember(state.settings.highQualityImages, state.settings.feedPreviewQuality) {
        state.settings.highQualityImages && state.settings.feedPreviewQuality != "low"
    }
    val showAiBadge = remember(state.settings.showAiBadge) { state.settings.showAiBadge }
    val prefetchUrls = remember(page, state.searchItems, feedHighQuality) {
        if (page == 0) {
            state.searchItems.map { if (feedHighQuality) it.previewUrl else it.thumbnailUrl }
        } else {
            emptyList()
        }
    }
    PrefetchPixivImages(prefetchUrls, enabled = state.settings.prefetchImages)

    val gridState = rememberSaveable(page, saver = LazyGridState.Saver) {
        LazyGridState()
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(if (page == 0) adaptiveIllustColumns(state.settings) else 1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = MainNavigationContentPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (page == 0) {
            gridItems(state.searchItems, key = { it.id }, contentType = { "illust_card" }) { illust ->
                IllustCard(
                    illust = illust,
                    onBookmark = { viewModel.toggleBookmark(illust) },
                    onClick = { viewModel.openIllust(illust) },
                    highQualityImages = feedHighQuality,
                    showAiBadge = showAiBadge,
                )
            }
            if (state.searchNextUrl != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(onClick = viewModel::loadMoreSearch, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(stringResource(R.string.action_load_more))
                    }
                }
            }
            if (state.searchItems.isEmpty() && state.loadState != LoadState.Loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.search_empty_illust))
                }
            }
        } else {
            gridItems(state.userSearchItems, key = { it.id }, contentType = { "user_card" }) { user ->
                UserResultCard(user = user, onClick = { viewModel.openUserPage(user) })
            }
            if (state.userSearchItems.isEmpty() && state.loadState != LoadState.Loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.search_empty_user))
                }
            }
            if (state.userSearchNextUrl != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(onClick = viewModel::loadMoreUserSearch, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(stringResource(R.string.action_load_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseArea(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    showHeader: Boolean = false,
) {
    val feedHighQuality = remember(state.settings.highQualityImages, state.settings.feedPreviewQuality) {
        state.settings.highQualityImages && state.settings.feedPreviewQuality != "low"
    }
    val showAiBadge = remember(state.settings.showAiBadge) { state.settings.showAiBadge }
    val browsePrefetchUrls = remember(state.settings.viewHistory, state.homeItems, state.searchItems, feedHighQuality) {
        val historyUrls = state.settings.viewHistory.take(8).map {
            if (feedHighQuality) it.previewUrl else it.thumbnailUrl
        }
        val tagUrls = (state.homeItems + state.searchItems)
            .distinctBy { it.id }
            .take(12)
            .map { it.squareImageUrl.ifBlank { it.imageUrl } }
        historyUrls + tagUrls
    }
    PrefetchPixivImages(browsePrefetchUrls, enabled = state.settings.prefetchImages)

    LazyVerticalGrid(
        state = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() },
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = MainNavigationContentPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (showHeader) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.nav_search),
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
        }
        if (state.settings.viewHistory.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(stringResource(R.string.search_recent_viewed), action = stringResource(R.string.action_view_all), onAction = viewModel::openViewHistory)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    items(state.settings.viewHistory.take(8), key = { "recent_${it.id}" }, contentType = { "illust_card" }) { illust ->
                        Box(modifier = Modifier.width(130.dp)) {
                            IllustCard(
                                illust = illust,
                                onBookmark = { viewModel.toggleBookmark(illust) },
                                onClick = { viewModel.openIllust(illust) },
                                highQualityImages = feedHighQuality,
                                showAiBadge = showAiBadge,
                            )
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.search_recommended_tags), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.Bold)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val tagImages = remember(state.homeItems, state.searchItems) {
                (state.homeItems + state.searchItems).distinctBy { it.id }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SuggestedTags.chunked(3).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEachIndexed { index, tag ->
                            val illust = tagImages.getOrNull(rowIndex * 3 + index)
                            TagTile(
                                tag = tag,
                                imageUrl = illust?.squareImageUrl?.ifBlank { illust.imageUrl },
                                onClick = { viewModel.submitSearch(tag.removePrefix("#")) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchToolbar(
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    suggestions: List<String> = emptyList(),
    historyCount: Int = 0,
) {
    SearchBar(
        inputField = {
            InputField(
                query = value,
                onQueryChange = onValueChange,
                onSearch = { onSearch() },
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                label = stringResource(R.string.search_placeholder),
            )
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        outsideEndAction = {
            HeaderIcon(
                icon = MiuixIcons.Close,
                onClick = {
                    onClear()
                    onExpandedChange(false)
                },
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val query = value.trim().removePrefix("#")
            val showLiveSuggestions = query.length >= 2
            val shownHistoryCount = historyCount.coerceAtMost(6)
            val historyItems = if (showLiveSuggestions) emptyList() else suggestions.take(shownHistoryCount)
            val suggestedItems = if (showLiveSuggestions) {
                suggestions
                    .drop(shownHistoryCount)
                    .filter { it.contains(query, ignoreCase = true) }
                    .ifEmpty { listOf(query) }
                    .take(8)
            } else {
                emptyList()
            }
            if (historyItems.isNotEmpty()) {
                Text(stringResource(R.string.search_history), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Bold)
            }
            historyItems.forEach { suggestion ->
                BasicComponent(
                    title = suggestion,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSuggestionClick(suggestion) },
                )
            }
            if (suggestedItems.isNotEmpty()) {
                Text(
                    stringResource(R.string.search_suggest),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (historyItems.isEmpty()) 0.dp else 8.dp),
                )
            }
            suggestedItems.forEach { suggestion ->
                BasicComponent(
                    title = suggestion,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSuggestionClick(suggestion) },
                )
            }
        }
    }
}

@Composable
fun UserResultCard(user: UserPreview, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (user.previewIllusts.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    user.previewIllusts.take(3).forEach { illust ->
                        PixivImage(
                            url = illust.squareImageUrl.ifBlank { illust.mediumImageUrl.ifBlank { illust.imageUrl } },
                            contentDescription = illust.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).height(118.dp),
                        )
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
                    Text(user.name, color = MiuixTheme.colorScheme.onBackground, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("@${user.account}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FollowPill(isFollowed = user.isFollowed)
            }
        }
    }
}
