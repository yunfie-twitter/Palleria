package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.RecommendedTagTile
import com.yunfie.illustia.isMutedByTags
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.PrefetchPixivImages
import com.yunfie.illustia.ui.components.SectionHeader
import com.yunfie.illustia.ui.components.TagTile
import com.yunfie.illustia.ui.components.adaptiveMainNavigationContentPadding
import com.yunfie.illustia.ui.components.adaptiveProfileGridColumns
import com.yunfie.illustia.ui.components.adaptiveRecommendedTagColumns
import com.yunfie.illustia.visibleWithMutedTagsVisible
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BrowseArea(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    showHeader: Boolean = false,
    onIllustSelected: ((Illust) -> Unit)? = null,
    onSearch: ((String) -> Unit)? = null,
) {
    val feedHighQuality = state.settings.useHighQualityFeedImages
    val showAiBadge = remember(state.settings.showAiBadge) { state.settings.showAiBadge }
    val browsePrefetchUrls =
        remember(state.settings.viewHistory, state.homeItems, state.searchItems, feedHighQuality) {
            val historyUrls =
                state.settings.viewHistory.visibleWithMutedTagsVisible(state.settings).take(8).map {
                    if (feedHighQuality) it.previewUrl else it.thumbnailUrl
                }
            val tagUrls =
                sequenceOf(state.homeItems.asSequence(), state.searchItems.asSequence())
                    .flatten()
                    .distinctBy { it.id }
                    .take(12)
                    .map { it.squareImageUrl.ifBlank { it.imageUrl } }
                    .toList()
            historyUrls + tagUrls
        }
    val recentIllusts =
        remember(state.settings.viewHistory, state.settings.mutedTags) {
            state.settings.viewHistory
                .visibleWithMutedTagsVisible(state.settings)
                .take(8)
        }

    LazyVerticalGrid(
        state = viewModel.searchBrowseGridState,
        columns = GridCells.Fixed(adaptiveProfileGridColumns()),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = adaptiveMainNavigationContentPadding()),
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
        if (recentIllusts.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    stringResource(R.string.search_recent_viewed),
                    action = stringResource(R.string.action_view_all),
                    onAction = viewModel::openViewHistory,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                ) {
                    items(
                        recentIllusts,
                        key = {
                            "recent_${it.id}"
                        },
                        contentType = { "illust_card" },
                    ) { illust ->
                        Box(modifier = Modifier.width(130.dp)) {
                            IllustCard(
                                illust = illust,
                                onBookmark = { viewModel.toggleBookmark(illust) },
                                onClick = { onIllustSelected?.invoke(illust) ?: viewModel.openIllust(illust) },
                                highQualityImages = feedHighQuality,
                                showAiBadge = showAiBadge,
                                isMutedByTag = illust.isMutedByTags(state.settings),
                            )
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.search_recommended_tags),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Bold,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val tagColumns = adaptiveRecommendedTagColumns()
            val recommendedTags =
                remember(state.recommendedTagTiles, state.recommendedTags) {
                    if (state.recommendedTagTiles.isNotEmpty()) {
                        state.recommendedTagTiles
                    } else {
                        state.recommendedTags.map { RecommendedTagTile(tag = it) }
                    }
                }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                recommendedTags.chunked(tagColumns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { tag ->
                            TagTile(
                                tag = tag.tag,
                                imageUrl = tag.imageUrl,
                                onClick = {
                                    val query = tag.tag.removePrefix("#")
                                    onSearch?.invoke(query) ?: viewModel.submitSearch(query)
                                },
                                onLongClick = {
                                    viewModel.openTagOptions(
                                        rawTag = tag.tag,
                                        imageUrl = tag.imageUrl,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(tagColumns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
