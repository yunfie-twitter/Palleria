package com.yunfie.illustia.ui.screens.novel

import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.NovelReadingStatus

/** 小説一覧画面が ViewModel に要求する操作。 */
interface NovelListActions {
    fun refreshNovels(forceRefresh: Boolean = false)

    fun loadMoreNovels()

    fun openNovel(novel: NovelPreview)

    fun toggleNovelBookmark(novel: NovelPreview)

    fun setNovelReadingStatus(
        novelId: Long,
        status: NovelReadingStatus,
    )

    fun updateVerticalColumnCount(count: Int)
}
