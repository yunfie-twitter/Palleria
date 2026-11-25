package com.yunfie.illustia.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.illust_load_more
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text

/** 作品カードのグリッド。カード描画は [IllustCard] に委譲する。 */
@Composable
fun IllustGrid(
    illusts: List<Illust>,
    emptyMessage: String,
    onOpenIllust: (Illust) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    onBookmark: (Illust) -> Unit = {},
    hasMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    showBookmarkButton: Boolean = true,
    isMutedByTag: (Illust) -> Boolean = { false },
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
    state: LazyGridState = rememberLazyGridState(),
) {
    if (illusts.isEmpty()) {
        EmptyState(emptyMessage)
    } else {
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Fixed(columns.coerceAtLeast(1)),
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(illusts, key = { it.id }, contentType = { "illust_card" }) { illust ->
                val onClick = remember(illust, onOpenIllust) { { onOpenIllust(illust) } }
                val onBookmarkClick = remember(illust, onBookmark) { { onBookmark(illust) } }
                IllustCard(
                    illust = illust,
                    modifier = animatedGridPlacement(),
                    onBookmark = onBookmarkClick,
                    onClick = onClick,
                    showBookmarkButton = showBookmarkButton,
                    isMutedByTag = isMutedByTag(illust),
                )
            }
            if (hasMore && onLoadMore != null) {
                item(key = "illust_load_more", contentType = "load_more") {
                    Button(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(stringResource(Res.string.illust_load_more))
                    }
                }
            }
        }
    }
}
