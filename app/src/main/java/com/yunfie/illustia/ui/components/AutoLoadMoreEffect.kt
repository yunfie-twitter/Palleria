package com.yunfie.illustia.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

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
