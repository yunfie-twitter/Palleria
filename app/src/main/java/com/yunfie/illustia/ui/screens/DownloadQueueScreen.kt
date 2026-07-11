package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.DownloadQueueEntry
import com.yunfie.illustia.DownloadQueueStatus
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class QueueTab {
    All,
    Downloading,
    Completed,
}

@Composable
fun DownloadQueueScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    var selectedTab by remember { mutableIntStateOf(0) }
    val allItems = state.downloadQueue.sortedByDescending { it.timestampMillis }
    val downloadingItems = allItems.filter { it.status == DownloadQueueStatus.Waiting || it.status == DownloadQueueStatus.Downloading }
    val completedItems = allItems.filter { it.status == DownloadQueueStatus.Completed }
    val visibleItems = when (QueueTab.entries[selectedTab.coerceIn(0, QueueTab.entries.lastIndex)]) {
        QueueTab.All -> allItems
        QueueTab.Downloading -> downloadingItems
        QueueTab.Completed -> completedItems
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.download_queue_title),
                largeTitle = stringResource(R.string.download_queue_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = { HeaderIcon(MiuixIcons.Back, onClick = onBack) },
            )
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            color = MiuixTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                TabRow(
                    tabs = listOf(
                        stringResource(R.string.download_queue_tab_all),
                        stringResource(R.string.download_queue_tab_downloading),
                        stringResource(R.string.download_queue_tab_completed),
                    ),
                    selectedTabIndex = selectedTab,
                    onTabSelected = { selectedTab = it },
                    colors = TabRowDefaults.tabRowColors(
                        backgroundColor = MiuixTheme.colorScheme.surface,
                        contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        selectedBackgroundColor = MiuixTheme.colorScheme.surfaceContainerHigh,
                        selectedContentColor = MiuixTheme.colorScheme.onBackground,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 10.dp),
                    minWidth = 84.dp,
                    maxWidth = 164.dp,
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    if (visibleItems.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.download_queue_empty),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            )
                        }
                    } else {
                        items(visibleItems, key = { it.id }) { item ->
                            DownloadQueueRow(item = item)
                            DividerLine()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadQueueRow(item: DownloadQueueEntry) {
    BasicComponent(
        title = item.title,
        summary = queueStatusLabel(item.status),
        modifier = Modifier.fillMaxWidth(),
        startAction = {
            FileGlyph()
        },
        endActions = {
            DownloadStateIcon(item.status)
        },
    )
}

@Composable
private fun FileGlyph() {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.8.dp, Color(0xFF2FB9D9), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(8.dp)
                .background(MiuixTheme.colorScheme.surface),
        )
    }
}

@Composable
private fun DownloadStateIcon(status: DownloadQueueStatus) {
    val (tint, alpha) = when (status) {
        DownloadQueueStatus.Waiting -> MiuixTheme.colorScheme.onSurfaceVariantSummary to 0.35f
        DownloadQueueStatus.Downloading -> MiuixTheme.colorScheme.primary to 0.55f
        DownloadQueueStatus.Completed -> MiuixTheme.colorScheme.onSurfaceVariantSummary to 0.5f
        DownloadQueueStatus.Failed -> MiuixTheme.colorScheme.error to 0.65f
    }
    Icon(
        imageVector = MiuixIcons.ChevronForward,
        contentDescription = null,
        tint = tint.copy(alpha = alpha),
        modifier = Modifier.size(20.dp).graphicsLayer(rotationZ = 90f),
    )
}

@Composable
private fun queueStatusLabel(status: DownloadQueueStatus): String {
    return when (status) {
        DownloadQueueStatus.Waiting -> stringResource(R.string.download_queue_waiting)
        DownloadQueueStatus.Downloading -> stringResource(R.string.download_queue_downloading)
        DownloadQueueStatus.Completed -> stringResource(R.string.download_queue_completed)
        DownloadQueueStatus.Failed -> stringResource(R.string.download_queue_failed)
    }
}
