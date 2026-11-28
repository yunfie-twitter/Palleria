package com.yunfie.illustia.ui.screens.home

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.ui.scroll.ScrollPositionProbe

/**
 * ホーム画面が ViewModel に要求する操作。
 *
 * 一覧描画に必要な取得・再読込と、作品カード操作の入口だけを公開する。
 */
interface HomeActions {
    fun updateHomeSelectedTab(index: Int)

    fun refreshHome(forceRefresh: Boolean = false)

    fun loadMoreHome()

    fun refreshTimeline(forceRefresh: Boolean = false)

    fun loadMoreTimeline()

    fun applyPendingHomeItems()

    fun updateVerticalColumnCount(count: Int)

    fun toggleBookmark(illust: Illust)

    fun openBookmarkTagPicker(illust: Illust)

    fun openIllust(illust: Illust)

    fun onIllustLongPress(illustId: Long)

    fun openAccountSwitcher()

    fun openNotifications()

    /** ホームグリッドのスクロール位置プローブを更新する。 */
    fun onHomeScrollProbeChanged(probe: ScrollPositionProbe)
}
