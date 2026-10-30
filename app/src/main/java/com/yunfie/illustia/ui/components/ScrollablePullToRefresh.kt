package com.yunfie.illustia.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.PullToRefreshDefaults
import top.yukonga.miuix.kmp.basic.PullToRefreshState
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState

/**
 * Pull-to-refresh that keeps the list scrollable while the request is running.
 *
 * Miuix's [PullToRefresh] consumes every nested-scroll event in
 * [top.yukonga.miuix.kmp.basic.RefreshState.Refreshing] and
 * [top.yukonga.miuix.kmp.basic.RefreshState.RefreshComplete], so the content cannot be
 * scrolled until the request finishes. Keep the library at the idle level and show the
 * in-flight state with a thin progress bar instead. The pull gesture still starts a
 * refresh, and a second release while one is running is ignored.
 */
@Composable
fun ScrollablePullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    pullToRefreshState: PullToRefreshState = rememberPullToRefreshState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    topAppBarScrollBehavior: ScrollBehavior? = null,
    color: Color = PullToRefreshDefaults.color,
    circleSize: Dp = PullToRefreshDefaults.circleSize,
    refreshTexts: List<String> = PullToRefreshDefaults.refreshTexts,
    refreshTextStyle: TextStyle = PullToRefreshDefaults.refreshTextStyle,
    content: @Composable () -> Unit,
) {
    val currentIsRefreshing by rememberUpdatedState(isRefreshing)
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    Box(modifier = modifier) {
        PullToRefresh(
            isRefreshing = false,
            onRefresh = { if (!currentIsRefreshing) currentOnRefresh() },
            pullToRefreshState = pullToRefreshState,
            contentPadding = contentPadding,
            topAppBarScrollBehavior = topAppBarScrollBehavior,
            color = color,
            circleSize = circleSize,
            refreshTexts = refreshTexts,
            refreshTextStyle = refreshTextStyle,
            modifier = Modifier.fillMaxSize(),
        ) {
            content()
        }
        if (isRefreshing) {
            LinearProgressIndicator(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(3.dp),
            )
        }
    }
}
