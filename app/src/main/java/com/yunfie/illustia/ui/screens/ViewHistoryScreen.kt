package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.MainNavigationContentPadding
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.visibleWithSettings
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class ViewHistoryDeleteTarget {
    All,
    Selected,
}

@Composable
fun ViewHistoryScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    var deleteTarget by remember { mutableStateOf<ViewHistoryDeleteTarget?>(null) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val feedHighQuality = state.settings.highQualityImages && state.settings.feedPreviewQuality != "low"
    val showAiBadge = state.settings.showAiBadge
    val hasSelection = selectedIds.isNotEmpty()
    val selectedCountText = stringResource(R.string.data_items_count, selectedIds.size)

    val visibleHistory = remember(state.settings.viewHistory, state.settings, searchQuery) {
        val history = state.settings.viewHistory.visibleWithSettings(state.settings)
        val query = searchQuery.trim()
        if (query.isEmpty()) history else history.filter { illust ->
            illust.title.contains(query, ignoreCase = true) ||
                illust.artistName.contains(query, ignoreCase = true) ||
                illust.tags.any { it.contains(query, ignoreCase = true) }
        }
    }

    LaunchedEffect(visibleHistory) {
        val availableIds = visibleHistory.asSequence().map { it.id }.toSet()
        selectedIds = selectedIds.filterTo(mutableSetOf()) { it in availableIds }
    }

    deleteTarget?.let { target ->
        val title = when (target) {
            ViewHistoryDeleteTarget.All -> stringResource(R.string.data_delete_view_history)
            ViewHistoryDeleteTarget.Selected -> stringResource(R.string.view_history_delete_selected)
        }
        val summary = when (target) {
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

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.more_view_history),
                largeTitle = stringResource(R.string.more_view_history),
                subtitle = if (hasSelection) stringResource(R.string.view_history_selected_count, selectedIds.size) else "",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
                actions = {
                    if (hasSelection) {
                        HeaderIcon(MiuixIcons.Close, onClick = { selectedIds = emptySet() })
                        HeaderIcon(MiuixIcons.Delete, onClick = { deleteTarget = ViewHistoryDeleteTarget.Selected })
                    } else {
                        HeaderIcon(MiuixIcons.Search, onClick = { showSearch = !showSearch })
                        HeaderIcon(MiuixIcons.Delete, onClick = { deleteTarget = ViewHistoryDeleteTarget.All })
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        LazyVerticalGrid(
            state = rememberLazyGridState(),
            columns = GridCells.Fixed(adaptiveIllustColumns(state.settings)),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .background(MiuixTheme.colorScheme.surface),
            contentPadding = PaddingValues(
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
                            onQueryChange = { searchQuery = it },
                            onSearch = { showSearch = false },
                            expanded = showSearch,
                            onExpandedChange = { showSearch = it },
                            label = stringResource(R.string.view_history_search_hint),
                        )
                    },
                    expanded = showSearch,
                    onExpandedChange = { showSearch = it },
                    modifier = Modifier.fillMaxWidth(),
                    outsideEndAction = {
                        HeaderIcon(
                            icon = MiuixIcons.Close,
                            onClick = {
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
                onBookmark = { viewModel.toggleBookmark(illust) },
                onClick = {
                    if (hasSelection) {
                        selectedIds = if (isSelected) selectedIds - illust.id else selectedIds + illust.id
                    } else {
                        viewModel.openIllust(illust)
                    }
                },
                onLongClick = {
                    selectedIds = if (isSelected) selectedIds - illust.id else selectedIds + illust.id
                },
                highQualityImages = feedHighQuality,
                showAiBadge = showAiBadge,
            )
        }
    }
    }
}
