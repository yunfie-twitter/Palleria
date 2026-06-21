package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
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
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ViewHistoryScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val feedHighQuality = state.settings.highQualityImages && state.settings.feedPreviewQuality != "low"
    val showAiBadge = state.settings.showAiBadge

    if (showDeleteConfirm) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.data_delete_view_history),
            summary = stringResource(R.string.data_delete_view_history_desc),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                viewModel.clearViewHistory()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.more_view_history),
                largeTitle = stringResource(R.string.more_view_history),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
                actions = {
                    HeaderIcon(MiuixIcons.Delete, onClick = { showDeleteConfirm = true })
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

        if (state.settings.viewHistory.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(stringResource(R.string.search_empty_illust))
            }
        }

        gridItems(state.settings.viewHistory, key = { it.id }, contentType = { "illust_card" }) { illust ->
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
