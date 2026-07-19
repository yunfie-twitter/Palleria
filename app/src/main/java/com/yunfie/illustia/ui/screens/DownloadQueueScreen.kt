package com.yunfie.illustia.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.DownloadQueueEntry
import com.yunfie.illustia.DownloadQueueStatus
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import java.text.DateFormat
import java.util.Date
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private enum class QueueTab {
    All,
    Active,
    Completed,
    Failed,
}

private data class QueueGroups(
    val active: List<DownloadQueueEntry>,
    val completed: List<DownloadQueueEntry>,
    val failed: List<DownloadQueueEntry>,
) {
    val all: List<DownloadQueueEntry>
        get() = active + failed + completed
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
    var showClearConfirmation by remember { mutableStateOf(false) }
    val groups = remember(state.downloadQueue) {
        val sorted = state.downloadQueue.sortedByDescending(DownloadQueueEntry::timestampMillis)
        QueueGroups(
            active = sorted.filter {
                it.status == DownloadQueueStatus.Waiting ||
                    it.status == DownloadQueueStatus.Downloading
            },
            completed = sorted.filter { it.status == DownloadQueueStatus.Completed },
            failed = sorted.filter { it.status == DownloadQueueStatus.Failed },
        )
    }
    val selected = QueueTab.entries[selectedTab.coerceIn(0, QueueTab.entries.lastIndex)]
    val hasFinishedItems = groups.completed.isNotEmpty() || groups.failed.isNotEmpty()

    if (showClearConfirmation) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.download_queue_clear_title),
            summary = stringResource(R.string.download_queue_clear_summary),
            confirmText = stringResource(R.string.download_queue_clear),
            destructive = true,
            onConfirm = {
                showClearConfirmation = false
                viewModel.clearFinishedDownloads()
            },
            onDismiss = { showClearConfirmation = false },
        )
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.download_queue_title),
                largeTitle = stringResource(R.string.download_queue_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = { HeaderIcon(MiuixIcons.Back, onClick = onBack) },
                actions = {
                    if (hasFinishedItems) {
                        HeaderIcon(
                            icon = MiuixIcons.Delete,
                            onClick = { showClearConfirmation = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            color = MiuixTheme.colorScheme.surface,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    QueueTabs(
                        selectedTab = selectedTab,
                        onSelected = { selectedTab = it },
                        groups = groups,
                    )
                }

                when (selected) {
                    QueueTab.All -> {
                        queueSection(
                            titleRes = R.string.download_queue_section_downloading,
                            items = groups.active,
                        )
                        queueSection(
                            titleRes = R.string.download_queue_section_failed,
                            items = groups.failed,
                        )
                        queueSection(
                            titleRes = R.string.download_queue_section_completed,
                            items = groups.completed,
                        )
                        if (groups.all.isEmpty()) {
                            item { QueueEmptyState(QueueTab.All) }
                        }
                    }

                    QueueTab.Active -> queueTabContent(groups.active, QueueTab.Active)
                    QueueTab.Completed -> queueTabContent(groups.completed, QueueTab.Completed)
                    QueueTab.Failed -> queueTabContent(groups.failed, QueueTab.Failed)
                }
            }
        }
    }
}

@Composable
private fun ActiveTransferTrack() {
    val transition = rememberInfiniteTransition(label = "download-track")
    val offset by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_450),
            repeatMode = RepeatMode.Restart,
        ),
        label = "download-track-offset",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            MiuixTheme.colorScheme.primary,
                            Color.Transparent,
                        ),
                        start = Offset(offset, 0f),
                        end = Offset(offset + 360f, 0f),
                    ),
                ),
        )
    }
}

@Composable
private fun QueueTabs(
    selectedTab: Int,
    onSelected: (Int) -> Unit,
    groups: QueueGroups,
) {
    TabRow(
        tabs = listOf(
            "${stringResource(R.string.download_queue_tab_all)} ${groups.all.size}",
            "${stringResource(R.string.download_queue_tab_downloading)} ${groups.active.size}",
            "${stringResource(R.string.download_queue_tab_completed)} ${groups.completed.size}",
            "${stringResource(R.string.download_queue_section_failed)} ${groups.failed.size}",
        ),
        selectedTabIndex = selectedTab,
        onTabSelected = onSelected,
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = MiuixTheme.colorScheme.surface,
            contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            selectedBackgroundColor = MiuixTheme.colorScheme.surfaceContainerHigh,
            selectedContentColor = MiuixTheme.colorScheme.onBackground,
        ),
        modifier = Modifier.fillMaxWidth(),
        minWidth = 78.dp,
        maxWidth = 136.dp,
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.queueSection(
    titleRes: Int,
    items: List<DownloadQueueEntry>,
) {
    if (items.isEmpty()) return
    item(key = "section_$titleRes") {
        QueueSectionHeader(titleRes = titleRes, count = items.size)
    }
    items(items, key = { it.id }) { entry ->
        DownloadQueueCard(entry)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.queueTabContent(
    items: List<DownloadQueueEntry>,
    tab: QueueTab,
) {
    if (items.isEmpty()) {
        item { QueueEmptyState(tab) }
    } else {
        items(items, key = { it.id }) { entry ->
            DownloadQueueCard(entry)
        }
    }
}

@Composable
private fun QueueSectionHeader(titleRes: Int, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(titleRes),
            color = MiuixTheme.colorScheme.onBackground,
            style = MiuixTheme.textStyles.title4,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString().padStart(2, '0'),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DownloadQueueCard(entry: DownloadQueueEntry) {
    val accent = statusAccent(entry.status)
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            DownloadStatusGlyph(entry.status, accent)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = entry.title,
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.status == DownloadQueueStatus.Downloading) {
                    ActiveTransferTrack()
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusPill(entry.status, accent)
                Spacer(Modifier.height(7.dp))
                Text(
                    text = remember(entry.timestampMillis) {
                        DateFormat.getTimeInstance(DateFormat.SHORT)
                            .format(Date(entry.timestampMillis))
                    },
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote2,
                )
            }
        }
    }
}

@Composable
private fun DownloadStatusGlyph(status: DownloadQueueStatus, accent: Color) {
    val alpha = if (status == DownloadQueueStatus.Downloading) {
        val transition = rememberInfiniteTransition(label = "status-downloading")
        val pulse by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(820),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "status-pulse",
        )
        pulse
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(accent.copy(alpha = 0.14f * alpha)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when (status) {
                DownloadQueueStatus.Waiting -> "…"
                DownloadQueueStatus.Downloading -> "↓"
                DownloadQueueStatus.Completed -> "✓"
                DownloadQueueStatus.Failed -> "!"
            },
            color = accent.copy(alpha = alpha),
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun StatusPill(status: DownloadQueueStatus, accent: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            text = queueStatusLabel(status),
            color = accent,
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun QueueEmptyState(tab: QueueTab) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        cornerRadius = 24.dp,
        insideMargin = PaddingValues(24.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "— 00 —",
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = stringResource(
                    when (tab) {
                        QueueTab.All -> R.string.download_queue_empty_all
                        QueueTab.Active -> R.string.download_queue_empty
                        QueueTab.Completed -> R.string.download_queue_empty_completed
                        QueueTab.Failed -> R.string.download_queue_empty_failed
                    },
                ),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}

@Composable
private fun statusAccent(status: DownloadQueueStatus): Color {
    return when (status) {
        DownloadQueueStatus.Waiting -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        DownloadQueueStatus.Downloading -> MiuixTheme.colorScheme.primary
        DownloadQueueStatus.Completed -> Color(0xFF2AA876)
        DownloadQueueStatus.Failed -> MiuixTheme.colorScheme.error
    }
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
