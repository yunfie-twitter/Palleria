package com.yunfie.illustia.ui.scroll

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.yunfie.illustia.IllustiaViewModel

/**
 * ViewModel がスクロール位置について知る必要がある最小限の情報。
 *
 * ViewModel は「先頭から離れているか」だけを判断したいのであって、
 * `LazyGridState` そのものを扱いたいわけではない。この小さな読み取り専用
 * ビューを挟むことで、ViewModel 側に Compose の型が漏れないようにする。
 */
fun interface ScrollPositionProbe {
    fun isScrolledAwayFromStart(): Boolean

    companion object {
        val Never = ScrollPositionProbe { false }
    }
}

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
