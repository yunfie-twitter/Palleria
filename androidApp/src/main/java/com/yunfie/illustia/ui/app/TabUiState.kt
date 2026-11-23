package com.yunfie.illustia.ui.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * タブの選択状態と「一番上へスクロール」要求を保持する UI 層の状態。
 *
 * ViewModel とは独立した UI 専用の調整役で、タブ型 ([AppTab]) を ViewModel 層へ
 * 持ち込まないために切り出している。
 */
internal class TabUiState {
    /** 現在選択中のタブ。 */
    var activeTab: AppTab? = null

    private val _scrollToTopRequests = MutableSharedFlow<AppTab>(extraBufferCapacity = 16)

    /** 各タブの「一番上へスクロール」要求。 */
    val scrollToTopRequests: SharedFlow<AppTab> = _scrollToTopRequests

    /** 指定タブにスクロール要求を送る。 */
    fun requestScrollToTop(tab: AppTab) {
        _scrollToTopRequests.tryEmit(tab)
    }
}
