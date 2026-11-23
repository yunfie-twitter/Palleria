package com.yunfie.illustia.ui.scroll

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
