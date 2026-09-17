package com.yunfie.illustia.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val DEFAULT_BUFFER = 4

@Composable
fun AutoLoadMoreEffect(
    gridState: LazyGridState,
    enabled: Boolean,
    nextUrl: String?,
    isLoading: Boolean,
    buffer: Int = DEFAULT_BUFFER,
    onLoadMore: () -> Unit,
) {
    val shouldLoadMore =
        remember(gridState, enabled, nextUrl, isLoading, buffer) {
            derivedStateOf {
                if (!enabled || nextUrl == null || isLoading) return@derivedStateOf false
                val layoutInfo = gridState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisibleItemIndex >= totalItems - 1 - buffer
            }
        }

    LaunchedEffect(shouldLoadMore) {
        snapshotFlow { shouldLoadMore.value }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }
}

@Composable
fun AutoLoadMoreEffect(
    listState: LazyListState,
    enabled: Boolean,
    nextUrl: String?,
    isLoading: Boolean,
    buffer: Int = DEFAULT_BUFFER,
    onLoadMore: () -> Unit,
) {
    val shouldLoadMore =
        remember(listState, enabled, nextUrl, isLoading, buffer) {
            derivedStateOf {
                if (!enabled || nextUrl == null || isLoading) return@derivedStateOf false
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisibleItemIndex >= totalItems - 1 - buffer
            }
        }

    LaunchedEffect(shouldLoadMore) {
        snapshotFlow { shouldLoadMore.value }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }
}

@Deprecated("Pass gridState or listState to tie pagination to scroll position instead of eager loading.")
@Composable
fun AutoLoadMoreEffect(
    enabled: Boolean,
    nextUrl: String?,
    isLoading: Boolean,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(enabled, nextUrl, isLoading) {
        if (enabled && nextUrl != null && !isLoading) {
            onLoadMore()
        }
    }
}
