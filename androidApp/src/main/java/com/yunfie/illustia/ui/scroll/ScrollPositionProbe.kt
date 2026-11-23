package com.yunfie.illustia.ui.scroll

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.yunfie.illustia.IllustiaViewModel

fun LazyGridState.asScrollPositionProbe(): ScrollPositionProbe =
    ScrollPositionProbe {
        firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0 || isScrollInProgress
    }

fun LazyListState.asScrollPositionProbe(): ScrollPositionProbe =
    ScrollPositionProbe {
        firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0 || isScrollInProgress
    }

/**
 * ホームフィードのグリッド状態を ViewModel に接続する。
 * このグリッドが画面から外れたら、ViewModel 側の参照も初期値に戻す。
 */
@Composable
fun InstallHomeScrollProbe(
    viewModel: IllustiaViewModel,
    state: LazyGridState,
) {
    DisposableEffect(viewModel, state) {
        viewModel.homeScrollProbe = state.asScrollPositionProbe()
        onDispose { viewModel.homeScrollProbe = ScrollPositionProbe.Never }
    }
}
