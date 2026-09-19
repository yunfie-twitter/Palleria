package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import com.yunfie.illustia.visibleWithSettings
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.lazy.grid.items as gridItems

private enum class ViewHistoryDeleteTarget {
    All,
    Selected,
}

private enum class ViewHistorySortOrder {
    Newest,
    Oldest,
    Title,
    Artist,
}

private enum class ViewHistoryTypeFilter {
    All,
    Illust,
    Manga,
    Ugoira,
}

@Composable
fun ViewHistoryScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    val performHaptic = rememberHapticFeedbackAction()
    PredictiveBackGestureHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        viewModel.loadFullViewHistory()
    }
    var deleteTarget by remember { mutableStateOf<ViewHistoryDeleteTarget?>(null) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var showSearch by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(ViewHistorySortOrder.Newest) }
    var filterType by remember { mutableStateOf(ViewHistoryTypeFilter.All) }
    val gridState = rememberLazyGridState()
    val feedHighQuality = state.settings.useHighQualityFeedImages
    val showAiBadge = state.settings.showAiBadge
    val hasSelection = selectedIds.isNotEmpty()
    val selectedCountText = stringResource(R.string.data_items_count, selectedIds.size)

    val visibleHistory =
        remember(state.settings.viewHistory, state.settings, searchQuery, sortOrder, filterType) {
            val history = state.settings.viewHistory.visibleWithSettings(state.settings)
            filterAndSortHistory(
                history = history,
                query = searchQuery.trim(),
                filterType = filterType,
                sortOrder = sortOrder,
            )
        }

    LaunchedEffect(visibleHistory) {
        val availableIds = visibleHistory.asSequence().map { it.id }.toSet()
        selectedIds = selectedIds.filterTo(mutableSetOf()) { it in availableIds }
    }

    LaunchedEffect(showSearch) {
        if (showSearch) {
            gridState.animateScrollToItem(0)
        }
    }

    deleteTarget?.let { target ->
        val title =
            when (target) {
                ViewHistoryDeleteTarget.All -> stringResource(R.string.data_delete_view_history)
                ViewHistoryDeleteTarget.Selected -> stringResource(R.string.view_history_delete_selected)
            }
        val summary =
            when (target) {
                ViewHistoryDeleteTarget.All -> stringResource(R.string.data_delete_view_history_desc)
                ViewHistoryDeleteTarget.Selected -> stringResource(R.string.view_history_delete_selected_desc, selectedCountText)
            }
        MiuixConfirmDialog(
            show = true,
            title = title,
            summary = summary,
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                performHaptic(AppHapticEffect.Click)
                when (target) {
                    ViewHistoryDeleteTarget.All -> viewModel.clearViewHistory()
                    ViewHistoryDeleteTarget.Selected -> viewModel.removeViewHistory(selectedIds)
                }
                selectedIds = emptySet()
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    val sortOrderLabel = stringResource(R.string.search_sort_order)
    val sortNewestLabel = stringResource(R.string.sort_date_desc)
    val sortOldestLabel = stringResource(R.string.sort_date_asc)
    val sortTitleLabel = stringResource(R.string.sort_name_asc)
    val sortArtistLabel = stringResource(R.string.view_history_sort_artist)

    val filterLabel = stringResource(R.string.search_work_type)
    val filterAllLabel = stringResource(R.string.novel_filter_all)
    val filterIllustLabel = stringResource(R.string.search_work_type_illustrations)
    val filterMangaLabel = stringResource(R.string.search_work_type_manga)
    val filterUgoiraLabel = stringResource(R.string.search_work_type_ugoira)

    val selectAllLabel = stringResource(R.string.view_history_select_all)
    val deselectAllLabel = stringResource(R.string.view_history_deselect_all)
    val bookmarkSelectedLabel = stringResource(R.string.view_history_bookmark_selected)

    val deleteHistoryLabel = stringResource(R.string.data_delete_view_history)
    val deleteSelectedLabel = stringResource(R.string.view_history_delete_selected)
    val deleteAllLabel = stringResource(R.string.view_history_delete_all)

    val reloadLabel = stringResource(R.string.action_reload)
    val showSearchLabel = stringResource(R.string.view_history_show_search)
    val hideSearchLabel = stringResource(R.string.view_history_hide_search)
    val moreLabel = stringResource(R.string.nav_more)

    val menuEntries =
        remember(
            hasSelection,
            visibleHistory,
            sortOrder,
            filterType,
            showSearch,
            sortOrderLabel,
            sortNewestLabel,
            sortOldestLabel,
            sortTitleLabel,
            sortArtistLabel,
            filterLabel,
            filterAllLabel,
            filterIllustLabel,
            filterMangaLabel,
            filterUgoiraLabel,
            selectAllLabel,
            deselectAllLabel,
            bookmarkSelectedLabel,
            deleteHistoryLabel,
            deleteSelectedLabel,
            deleteAllLabel,
            reloadLabel,
            showSearchLabel,
            hideSearchLabel,
        ) {
            listOf(
                DropdownEntry(
                    items =
                        listOfNotNull(
                            if (!hasSelection) {
                                DropdownItem(
                                    text = selectAllLabel,
                                    onClick = {
                                        performHaptic(AppHapticEffect.Click)
                                        selectedIds = visibleHistory.map { it.id }.toSet()
                                    },
                                )
                            } else {
                                DropdownItem(
                                    text = deselectAllLabel,
                                    onClick = {
                                        performHaptic(AppHapticEffect.Click)
                                        selectedIds = emptySet()
                                    },
                                )
                            },
                            if (hasSelection) {
                                DropdownItem(
                                    text = bookmarkSelectedLabel,
                                    onClick = {
                                        performHaptic(AppHapticEffect.Toggle)
                                        selectedIds.forEach { id ->
                                            visibleHistory.firstOrNull { it.id == id }?.let { illust ->
                                                if (!illust.isBookmarked) {
                                                    viewModel.toggleBookmark(illust)
                                                }
                                            }
                                        }
                                    },
                                )
                            } else {
                                null
                            },
                            DropdownItem(
                                text = sortOrderLabel,
                                children =
                                    listOf(
                                        DropdownItem(
                                            text = sortNewestLabel,
                                            selected = sortOrder == ViewHistorySortOrder.Newest,
                                            onClick = {
                                                performHaptic(AppHapticEffect.Toggle)
                                                sortOrder = ViewHistorySortOrder.Newest
                                            },
                                        ),
                                        DropdownItem(
                                            text = sortOldestLabel,
                                            selected = sortOrder == ViewHistorySortOrder.Oldest,
                                            onClick = {
                                                performHaptic(AppHapticEffect.Toggle)
                                                sortOrder = ViewHistorySortOrder.Oldest
                                            },
                                        ),
                                        DropdownItem(
                                            text = sortTitleLabel,
                                            selected = sortOrder == ViewHistorySortOrder.Title,
                                            onClick = {
                                                performHaptic(AppHapticEffect.Toggle)
                                                sortOrder = ViewHistorySortOrder.Title
                                            },
                                        ),
                                        DropdownItem(
                                            text = sortArtistLabel,
                                            selected = sortOrder == ViewHistorySortOrder.Artist,
                                            onClick = {
                                                performHaptic(AppHapticEffect.Toggle)
                                                sortOrder = ViewHistorySortOrder.Artist
                                            },
                                        ),
                                    ),
                            ),
                            DropdownItem(
                                text = filterLabel,
                                children =
                                    listOf(
                                        DropdownItem(
                                            text = filterAllLabel,
                                            selected = filterType == ViewHistoryTypeFilter.All,
                                            onClick = {
                                                performHaptic(AppHapticEffect.Toggle)
                                                filterType = ViewHistoryTypeFilter.All
                                            },
                                        ),
                                        DropdownItem(
                                            text = filterIllustLabel,
                                            selected = filterType == ViewHistoryTypeFilter.Illust,
                                            onClick = {
                                                performHaptic(AppHapticEffect.Toggle)
                                                filterType = ViewHistoryTypeFilter.Illust
                                            },
                                        ),
                                        DropdownItem(
                                            text = filterMangaLabel,
                                            selected = filterType == ViewHistoryTypeFilter.Manga,
                                            onClick = {
                                                performHaptic(AppHapticEffect.Toggle)
                                                filterType = ViewHistoryTypeFilter.Manga
                                            },
                                        ),
                                        DropdownItem(
                                            text = filterUgoiraLabel,
                                            selected = filterType == ViewHistoryTypeFilter.Ugoira,
                                            onClick = {
                                                performHaptic(AppHapticEffect.Toggle)
                                                filterType = ViewHistoryTypeFilter.Ugoira
                                            },
                                        ),
                                    ),
                            ),
                        ),
                ),
                DropdownEntry(
                    items =
                        listOfNotNull(
                            DropdownItem(
                                text = deleteHistoryLabel,
                                children =
                                    listOfNotNull(
                                        if (hasSelection) {
                                            DropdownItem(
                                                text = deleteSelectedLabel,
                                                onClick = {
                                                    performHaptic(AppHapticEffect.Click)
                                                    deleteTarget = ViewHistoryDeleteTarget.Selected
                                                },
                                            )
                                        } else {
                                            null
                                        },
                                        DropdownItem(
                                            text = deleteAllLabel,
                                            onClick = {
                                                performHaptic(AppHapticEffect.Click)
                                                deleteTarget = ViewHistoryDeleteTarget.All
                                            },
                                        ),
                                    ),
                            ),
                        ),
                ),
                DropdownEntry(
                    items =
                        listOf(
                            DropdownItem(
                                text = reloadLabel,
                                onClick = {
                                    performHaptic(AppHapticEffect.Click)
                                    viewModel.loadFullViewHistory()
                                },
                            ),
                            DropdownItem(
                                text = if (showSearch) hideSearchLabel else showSearchLabel,
                                onClick = {
                                    performHaptic(AppHapticEffect.Click)
                                    showSearch = !showSearch
                                },
                            ),
                        ),
                ),
            )
        }

    val scrollBehavior = MiuixScrollBehavior()
    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.more_view_history),
                largeTitle = stringResource(R.string.more_view_history),
                subtitle = if (hasSelection) stringResource(R.string.view_history_selected_count, selectedIds.size) else "",
                scrollBehavior = scrollBehavior,
                modifier =
                    Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            performHaptic(AppHapticEffect.Click)
                            coroutineScope.launch {
                                scrollBehavior.state.heightOffset = 0f
                                scrollBehavior.state.contentOffset = 0f
                                gridState.animateScrollToItem(0)
                            }
                        }
                    },
                navigationIcon = {
                    HeaderIcon(
                        MiuixIcons.Back,
                        onClick = {
                            performHaptic(AppHapticEffect.Click)
                            onBack()
                        },
                    )
                },
                actions = {
                    if (hasSelection) {
                        HeaderIcon(
                            MiuixIcons.Close,
                            onClick = {
                                performHaptic(AppHapticEffect.Click)
                                selectedIds = emptySet()
                            },
                        )
                    } else {
                        HeaderIcon(
                            MiuixIcons.Search,
                            onClick = {
                                performHaptic(AppHapticEffect.Click)
                                showSearch = !showSearch
                            },
                        )
                    }
                    Box {
                        HeaderIcon(
                            icon = MiuixIcons.More,
                            contentDescription = moreLabel,
                            onClick = {
                                performHaptic(AppHapticEffect.Click)
                                showMoreMenu = true
                            },
                        )
                        OverlayCascadingListPopup(
                            show = showMoreMenu,
                            entries = menuEntries,
                            onDismissRequest = { showMoreMenu = false },
                        )
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(adaptiveIllustColumns(state.settings)),
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .background(MiuixTheme.colorScheme.surface),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = scaffoldPadding.calculateTopPadding() + 14.dp,
                    bottom = 96.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (showSearch) {
                item(span = { GridItemSpan(maxLineSpan) }, contentType = "history_search") {
                    SearchBar(
                        inputField = {
                            InputField(
                                query = searchQuery,
                                onQueryChange = { query -> searchQuery = query },
                                onSearch = { showSearch = false },
                                expanded = showSearch,
                                onExpandedChange = { expanded -> showSearch = expanded },
                                label = stringResource(R.string.view_history_search_hint),
                            )
                        },
                        expanded = showSearch,
                        onExpandedChange = { expanded -> showSearch = expanded },
                        modifier = Modifier.fillMaxWidth(),
                        outsideEndAction = {
                            HeaderIcon(
                                icon = MiuixIcons.Close,
                                onClick = {
                                    performHaptic(AppHapticEffect.Click)
                                    searchQuery = ""
                                    showSearch = false
                                },
                            )
                        },
                    ) {}
                }
            }

            if (visibleHistory.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.search_empty_illust))
                }
            }

            gridItems(visibleHistory, key = { it.id }, contentType = { "illust_card" }) { illust ->
                val isSelected = illust.id in selectedIds
                IllustCard(
                    illust = illust,
                    isSelected = isSelected,
                    onBookmark = {
                        performHaptic(AppHapticEffect.Toggle)
                        viewModel.toggleBookmark(illust)
                    },
                    onClick = {
                        if (hasSelection) {
                            performHaptic(AppHapticEffect.Toggle)
                            selectedIds = if (isSelected) selectedIds - illust.id else selectedIds + illust.id
                        } else {
                            viewModel.openIllust(illust)
                        }
                    },
                    onLongClick = {
                        performHaptic(AppHapticEffect.Click)
                        selectedIds = if (isSelected) selectedIds - illust.id else selectedIds + illust.id
                    },
                    highQualityImages = feedHighQuality,
                    showAiBadge = showAiBadge,
                )
            }
        }
    }
}

private fun filterAndSortHistory(
    history: List<Illust>,
    query: String,
    filterType: ViewHistoryTypeFilter,
    sortOrder: ViewHistorySortOrder,
): List<Illust> {
    val searched =
        if (query.isEmpty()) {
            history
        } else {
            history.filter { illust ->
                illust.title.contains(query, ignoreCase = true) ||
                    illust.artistName.contains(query, ignoreCase = true) ||
                    illust.tags.any { it.contains(query, ignoreCase = true) }
            }
        }
    val filtered =
        when (filterType) {
            ViewHistoryTypeFilter.All -> searched
            ViewHistoryTypeFilter.Illust -> searched.filter { it.type.equals("illust", ignoreCase = true) || it.type.isBlank() }
            ViewHistoryTypeFilter.Manga -> searched.filter { it.type.equals("manga", ignoreCase = true) }
            ViewHistoryTypeFilter.Ugoira -> searched.filter { it.type.equals("ugoira", ignoreCase = true) }
        }
    return when (sortOrder) {
        ViewHistorySortOrder.Newest -> filtered
        ViewHistorySortOrder.Oldest -> filtered.reversed()
        ViewHistorySortOrder.Title -> filtered.sortedBy { it.title.lowercase() }
        ViewHistorySortOrder.Artist -> filtered.sortedBy { it.artistName.lowercase() }
    }
}
