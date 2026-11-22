package com.yunfie.illustia.models

import androidx.compose.runtime.Immutable

@Immutable
data class PageResult<T>(
    val items: List<T>,
    val nextUrl: String?,
)

@Immutable
sealed interface LoadState {
    data object Idle : LoadState

    data object Loading : LoadState

    data object Loaded : LoadState

    data class Error(
        val message: String,
    ) : LoadState
}

/**
 * True only when a list has finished loading and came back empty.
 *
 * [LoadState.Idle] is the initial state before the first request starts. Treating it as
 * "empty" made the "ありません" placeholder flash for a frame on first composition, so the
 * empty state is gated on [LoadState.Loaded]; otherwise skeletons (or nothing) stay on screen.
 */
fun isEmptyAfterLoad(
    isEmpty: Boolean,
    loadState: LoadState,
): Boolean = isEmpty && loadState is LoadState.Loaded
