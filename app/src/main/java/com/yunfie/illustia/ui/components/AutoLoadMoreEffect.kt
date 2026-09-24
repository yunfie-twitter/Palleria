package com.yunfie.illustia.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

const val DEFAULT_GRID_BUFFER = 16
const val DEFAULT_LIST_BUFFER = 8
const val AUTO_LOAD_COOLDOWN_MS = 1200L
const val AUTO_LOAD_FAILURE_BACKOFF_MS = 5000L
const val AUTO_LOAD_DEBOUNCE_MS = 200L

internal object AutoLoadMoreThrottle {
    fun calculateDelayMillis(
        now: Long,
        lastRequestTime: Long,
        isSameUrl: Boolean,
        lastRequestFailed: Boolean,
        cooldownMs: Long = AUTO_LOAD_COOLDOWN_MS,
        failureBackoffMs: Long = AUTO_LOAD_FAILURE_BACKOFF_MS,
    ): Long {
        val elapsed = (now - lastRequestTime).coerceAtLeast(0L)
        return if (isSameUrl && lastRequestFailed) {
            (failureBackoffMs - elapsed).coerceAtLeast(0L)
        } else {
            (cooldownMs - elapsed).coerceAtLeast(0L)
        }
    }
}

@Composable
fun AutoLoadMoreEffect(
    gridState: LazyGridState,
    enabled: Boolean,
    nextUrl: String?,
    isLoading: Boolean,
    buffer: Int = DEFAULT_GRID_BUFFER,
    onLoadMore: () -> Unit,
) {
    AutoLoadMoreCore(
        enabled = enabled,
        nextUrl = nextUrl,
        isLoading = isLoading,
        buffer = buffer,
        onLoadMore = onLoadMore,
        checkNearBottom = {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 1 - buffer
        },
    )
}

@Composable
fun AutoLoadMoreEffect(
    listState: LazyListState,
    enabled: Boolean,
    nextUrl: String?,
    isLoading: Boolean,
    buffer: Int = DEFAULT_LIST_BUFFER,
    onLoadMore: () -> Unit,
) {
    AutoLoadMoreCore(
        enabled = enabled,
        nextUrl = nextUrl,
        isLoading = isLoading,
        buffer = buffer,
        onLoadMore = onLoadMore,
        checkNearBottom = {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 1 - buffer
        },
    )
}

@Composable
private fun AutoLoadMoreCore(
    enabled: Boolean,
    nextUrl: String?,
    isLoading: Boolean,
    buffer: Int,
    onLoadMore: () -> Unit,
    checkNearBottom: () -> Boolean,
) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentNextUrl by rememberUpdatedState(nextUrl)
    val currentIsLoading by rememberUpdatedState(isLoading)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    val isNearBottom by remember(buffer) {
        derivedStateOf {
            if (!currentEnabled || currentNextUrl == null || currentIsLoading) return@derivedStateOf false
            checkNearBottom()
        }
    }

    var lastRequestedUrl by remember { mutableStateOf<String?>(null) }
    var lastRequestTime by remember { mutableLongStateOf(0L) }
    var wasLoading by remember { mutableStateOf(false) }
    var lastRequestFailed by remember { mutableStateOf(false) }

    LaunchedEffect(currentIsLoading, currentNextUrl) {
        if (wasLoading && !currentIsLoading) {
            if (currentNextUrl != null && currentNextUrl == lastRequestedUrl) {
                lastRequestFailed = true
            } else {
                lastRequestFailed = false
            }
        }
        wasLoading = currentIsLoading
    }

    LaunchedEffect(isNearBottom) {
        snapshotFlow { isNearBottom }
            .distinctUntilChanged()
            .filter { it }
            .collectLatest {
                delay(AUTO_LOAD_DEBOUNCE_MS)

                val targetUrl = currentNextUrl ?: return@collectLatest
                if (!currentEnabled || currentIsLoading) return@collectLatest

                val now = System.currentTimeMillis()
                val isSameUrl = (targetUrl == lastRequestedUrl)

                val requiredDelay =
                    AutoLoadMoreThrottle.calculateDelayMillis(
                        now = now,
                        lastRequestTime = lastRequestTime,
                        isSameUrl = isSameUrl,
                        lastRequestFailed = lastRequestFailed,
                    )
                if (requiredDelay > 0) {
                    delay(requiredDelay)
                }

                val canTrigger = currentEnabled && !currentIsLoading
                if (canTrigger && currentNextUrl == targetUrl && checkNearBottom()) {
                    lastRequestedUrl = targetUrl
                    lastRequestTime = System.currentTimeMillis()
                    currentOnLoadMore()
                }
            }
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
