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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.LoadState
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.MainNavigationContentPadding
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.StateBanner
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.miuixClickable
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.squircle.squircleSurface

@Composable
fun FavoriteTagsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)

    val gridState = rememberLazyGridState()
    val feedHighQuality = state.settings.highQualityImages && state.settings.feedPreviewQuality != "low"
    val showAiBadge = state.settings.showAiBadge

    // 選択中タグ（最初のタグをデフォルト選択）
    var selectedTag by remember(state.settings.favoriteTags) {
        mutableStateOf(state.settings.favoriteTags.firstOrNull())
    }

    // タグが選択されたら自動ロード
    LaunchedEffect(selectedTag) {
        val tag = selectedTag ?: return@LaunchedEffect
        if (state.watchlistItems.isEmpty() || state.activeWatchlistTag != tag) {
            viewModel.loadWatchlistTag(tag)
        }
    }

    // 削除確認ダイアログ
    var tagToDelete by remember { mutableStateOf<String?>(null) }
    tagToDelete?.let { tag ->
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.favorite_tags_delete_title),
            summary = stringResource(R.string.favorite_tags_delete_confirm, tag),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                viewModel.toggleFavoriteTag(tag)
                if (selectedTag == tag) {
                    selectedTag = state.settings.favoriteTags.firstOrNull { it != tag }
                }
                tagToDelete = null
            },
            onDismiss = { tagToDelete = null },
        )
    }

    val subtitle = selectedTag?.let { "#$it / ${stringResource(R.string.data_items_count, state.watchlistItems.size)}" } ?: stringResource(R.string.favorite_tags_no_selection)

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.favorite_tags_title),
                largeTitle = stringResource(R.string.favorite_tags_title),
                subtitle = subtitle,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(icon = MiuixIcons.Back, onClick = onBack)
                },
                actions = {
                    HeaderIcon(
                        icon = MiuixIcons.Refresh,
                        onClick = { selectedTag?.let(viewModel::loadWatchlistTag) },
                    )
                },
            )
        },
    ) { scaffoldPadding ->
        PullToRefresh(
            isRefreshing = state.loadState == LoadState.Loading && state.watchlistItems.isNotEmpty(),
            onRefresh = { selectedTag?.let(viewModel::loadWatchlistTag) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(adaptiveIllustColumns(state.settings)),
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .background(MiuixTheme.colorScheme.surface),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = scaffoldPadding.calculateTopPadding() + 8.dp,
                    bottom = MainNavigationContentPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── タグチップ一覧 ────────────────────────────
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (state.settings.favoriteTags.isEmpty()) {
                        EmptyState(stringResource(R.string.favorite_tags_empty))
                    } else {
                        TagChipRow(
                            tags = state.settings.favoriteTags,
                            selectedTag = selectedTag,
                            onSelectTag = { tag ->
                                selectedTag = tag
                            },
                            onDeleteTag = { tag -> tagToDelete = tag },
                        )
                    }
                }

                // ── 検索結果 ──────────────────────────────────
                if (selectedTag != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        StateBanner(state.loadState)
                    }

                    val currentSelectedTag = selectedTag
                    if (state.watchlistItems.isEmpty() && state.loadState != LoadState.Loading && currentSelectedTag != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(stringResource(R.string.favorite_tags_works_not_found, currentSelectedTag))
                        }
                    }

                    gridItems(
                        items = state.watchlistItems,
                        key = { "fav_${it.id}" },
                        contentType = { "illust_card" },
                    ) { illust ->
                        IllustCard(
                            illust = illust,
                            onBookmark = { viewModel.toggleBookmark(illust) },
                            onClick = { viewModel.openIllust(illust) },
                            onLongClick = { viewModel.onIllustLongPress(illust) },
                            highQualityImages = feedHighQuality,
                            showAiBadge = showAiBadge,
                        )
                    }

                    if (state.watchlistNextUrl != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Button(
                                onClick = viewModel::loadMoreWatchlist,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                            ) {
                                Text(stringResource(R.string.action_load_more))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  タグチップ行（横スクロール + 長押し削除）
// ─────────────────────────────────────────────────────────
@Composable
private fun TagChipRow(
    tags: List<String>,
    selectedTag: String?,
    onSelectTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
) {
    // タグが多い場合は折り返しレイアウト
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.chunked(4).forEach { rowTags ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowTags.forEach { tag ->
                    val isSelected = selectedTag == tag
                    TagChip(
                        tag = tag,
                        isSelected = isSelected,
                        onSelect = { onSelectTag(tag) },
                        onDelete = { onDeleteTag(tag) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // 余白埋め
                repeat(4 - rowTags.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TagChip(
    tag: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = isSelected,
        transitionSpec = {
            fadeIn(tween(160)) togetherWith fadeOut(tween(120))
        },
        label = "tag-chip-$tag",
        modifier = modifier,
    ) { selected ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .squircleSurface(
                    if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainer,
                    20.dp,
                )
                .miuixClickable(onClick = onSelect),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "#$tag",
                    color = if (selected) Color.White else MiuixTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                // 削除ボタン（×）
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (selected) Color.White.copy(alpha = 0.25f)
                            else MiuixTheme.colorScheme.surfaceContainerHigh,
                        )
                        .miuixClickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = if (selected) Color.White else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
    }
}
