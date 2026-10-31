package com.yunfie.illustia.models

data class PageResult<T>(
    val items: List<T>,
    val nextUrl: String?,
)

sealed interface LoadState {
    data object Idle : LoadState

    data object Loading : LoadState

    data object Loaded : LoadState

    data class Error(
        val message: String,
    ) : LoadState
}
