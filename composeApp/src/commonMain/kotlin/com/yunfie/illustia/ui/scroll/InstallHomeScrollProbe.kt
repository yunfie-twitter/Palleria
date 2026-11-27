package com.yunfie.illustia.ui.scroll

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

fun LazyGridState.asScrollPositionProbe(): ScrollPositionProbe =
    ScrollPositionProbe {
        firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0 || isScrollInProgress
    }

fun LazyListState.asScrollPositionProbe(): ScrollPositionProbe =
    ScrollPositionProbe {
        firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0 || isScrollInProgress
    }

/**
 * ホームフィードのグリッド状態を購読側へ接続する。
 * このグリッドが画面から外れたら、参照も初期値に戻す。
 */
@Composable
fun InstallHomeScrollProbe(
    state: LazyGridState,
    onProbeChanged: (ScrollPositionProbe) -> Unit,
) {
    DisposableEffect(state, onProbeChanged) {
        onProbeChanged(state.asScrollPositionProbe())
        onDispose { onProbeChanged(ScrollPositionProbe.Never) }
    }
}
