package com.yunfie.illustia.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.RecommendedTagTile
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.SearchBookmarkFilter
import com.yunfie.illustia.models.SearchDuration
import com.yunfie.illustia.models.SearchSort
import com.yunfie.illustia.models.SearchTarget
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.ChoiceRow
import com.yunfie.illustia.ui.components.AvatarImage
import com.yunfie.illustia.ui.components.FollowPill
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.MainNavigationContentPadding
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PrefetchPixivImages
import com.yunfie.illustia.ui.components.SectionHeader
import com.yunfie.illustia.ui.components.TagTile
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import androidx.compose.foundation.layout.width

private val SearchSortOptions = SearchSort.entries.toList()
private val SearchTargetOptions = SearchTarget.entries.toList()
private val SearchDurationOptions = SearchDuration.entries.toList()
private val SearchBookmarkFilterOptions = SearchBookmarkFilter.entries.toList()

@Composable
internal fun SearchOptionsSheet(
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
        backgroundColor = com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor.current,
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
internal fun SearchOptionsContent(
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
    }
}

@Composable
internal fun SearchResultGrid(
    page: Int,
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onIllustSelected: ((Illust) -> Unit)? = null,
) {
    val feedHighQuality = remember(state.settings.highQualityImages, state.settings.feedPreviewQuality) {
        state.settings.highQualityImages && state.settings.feedPreviewQuality != "low"
    }
    val showAiBadge = remember(state.settings.showAiBadge) { state.settings.showAiBadge }
    val prefetchUrls = remember(page, state.searchItems, feedHighQuality) {
        if (page == 0) {
            state.searchItems.asSequence()
                .take(16)
                .map { if (feedHighQuality) it.previewUrl else it.thumbnailUrl }
                .toList()
        } else {
            emptyList()
        }
    }
    PrefetchPixivImages(prefetchUrls, enabled = state.settings.prefetchImages)

    val gridState = viewModel.searchResultGridState
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
                    onClick = { onIllustSelected?.invoke(illust) ?: viewModel.openIllust(illust) },
                    highQualityImages = feedHighQuality,
                    showAiBadge = showAiBadge,
                )
            }
            if (state.searchNextUrl != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = viewModel::loadMoreSearch,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = overlayActionButtonColors(),
                    ) {
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
                    Button(
                        onClick = viewModel::loadMoreUserSearch,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = overlayActionButtonColors(),
                    ) {
                        Text(stringResource(R.string.action_load_more))
                    }
                }
            }
        }
    }
}

@Composable
internal fun BrowseArea(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    showHeader: Boolean = false,
    onIllustSelected: ((Illust) -> Unit)? = null,
) {
    val feedHighQuality = remember(state.settings.highQualityImages, state.settings.feedPreviewQuality) {
        state.settings.highQualityImages && state.settings.feedPreviewQuality != "low"
    }
    val showAiBadge = remember(state.settings.showAiBadge) { state.settings.showAiBadge }
    val browsePrefetchUrls = remember(state.settings.viewHistory, state.homeItems, state.searchItems, feedHighQuality) {
        val historyUrls = state.settings.viewHistory.take(8).map {
            if (feedHighQuality) it.previewUrl else it.thumbnailUrl
        }
        val tagUrls = sequenceOf(state.homeItems.asSequence(), state.searchItems.asSequence())
            .flatten()
            .distinctBy { it.id }
            .take(12)
            .map { it.squareImageUrl.ifBlank { it.imageUrl } }
            .toList()
        historyUrls + tagUrls
    }
    PrefetchPixivImages(browsePrefetchUrls, enabled = state.settings.prefetchImages)

    LazyVerticalGrid(
        state = viewModel.searchBrowseGridState,
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
                                onClick = { onIllustSelected?.invoke(illust) ?: viewModel.openIllust(illust) },
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
            val recommendedTags = remember(state.recommendedTagTiles, state.recommendedTags) {
                if (state.recommendedTagTiles.isNotEmpty()) state.recommendedTagTiles
                else state.recommendedTags.map { RecommendedTagTile(tag = it) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                recommendedTags.chunked(3).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEachIndexed { index, tag ->
                            TagTile(
                                tag = tag.tag,
                                imageUrl = tag.imageUrl,
                                onClick = { viewModel.submitSearch(tag.tag.removePrefix("#")) },
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
internal fun UserResultCard(user: UserPreview, onClick: () -> Unit) {
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

