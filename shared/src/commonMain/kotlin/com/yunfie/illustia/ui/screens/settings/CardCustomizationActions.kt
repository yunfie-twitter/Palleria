package com.yunfie.illustia.ui.screens.settings

/**
 * カード表示設定画面が ViewModel に要求する操作。
 */
interface CardCustomizationActions {
    fun updateShowCardTitle(value: Boolean)

    fun updateShowCardArtist(value: Boolean)

    fun updateShowCardTags(value: Boolean)

    fun updateShowCardBookmarkCount(value: Boolean)

    fun updateShowAiBadge(value: Boolean)

    fun updateShowR18Badge(value: Boolean)

    fun updateShowCardBookmarkButton(value: Boolean)

    fun updateDoubleTapToBookmark(value: Boolean)

    fun updateQuickPeekEnabled(value: Boolean)

    fun updateQuickPeekSharedElementTransition(enabled: Boolean)

    fun updateShowRelatedR18(value: Boolean)
}
