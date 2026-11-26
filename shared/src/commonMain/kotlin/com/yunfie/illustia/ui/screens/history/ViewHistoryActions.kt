package com.yunfie.illustia.ui.screens.history

import com.yunfie.illustia.models.Illust

/**
 * 閲覧履歴画面が ViewModel に要求する操作。
 */
interface ViewHistoryActions {
    fun loadFullViewHistory()

    fun clearViewHistory()

    fun removeViewHistory(ids: Collection<Long>)

    fun updateVerticalColumnCount(value: Int)

    fun toggleBookmarkFromCard(illust: Illust)

    fun openIllustFromCard(illust: Illust)
}
