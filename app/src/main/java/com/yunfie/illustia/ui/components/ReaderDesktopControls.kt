package com.yunfie.illustia.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

@Composable
internal fun ReaderFullscreen(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val window = view.context.readerActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val bars = WindowInsetsCompat.Type.systemBars()
        val previousInsets = ViewCompat.getRootWindowInsets(view)
        val statusVisible = previousInsets?.isVisible(WindowInsetsCompat.Type.statusBars()) != false
        val navigationVisible = previousInsets?.isVisible(WindowInsetsCompat.Type.navigationBars()) != false
        if (enabled) controller?.hide(bars) else controller?.show(bars)
        onDispose {
            if (statusVisible) {
                controller?.show(
                    WindowInsetsCompat.Type.statusBars(),
                )
            } else {
                controller?.hide(WindowInsetsCompat.Type.statusBars())
            }
            if (navigationVisible) {
                controller?.show(
                    WindowInsetsCompat.Type.navigationBars(),
                )
            } else {
                controller?.hide(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }
}

internal fun paragraphTarget(
    indices: List<Int>,
    current: Int,
    direction: Int,
): Int? = if (direction > 0) indices.firstOrNull { it > current } else indices.lastOrNull { it < current }

@Composable
internal fun ParagraphNavigation(
    state: LazyListState,
    request: Int,
    active: Boolean,
    paragraphIndices: List<Int>,
) {
    var previousRequest by remember { mutableIntStateOf(request) }
    LaunchedEffect(request, active) {
        val direction = request.compareTo(previousRequest)
        previousRequest = request
        if (!active || direction == 0) return@LaunchedEffect
        val target = paragraphTarget(paragraphIndices, state.firstVisibleItemIndex, direction) ?: return@LaunchedEffect
        state.animateScrollToItem(target)
    }
}

private tailrec fun Context.readerActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.readerActivity()
        else -> null
    }
