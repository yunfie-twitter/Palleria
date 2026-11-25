package com.yunfie.illustia.ui.screens.relatedusers

import com.yunfie.illustia.IllustiaUiState
import kotlinx.coroutines.flow.StateFlow

/** 関連ユーザー画面が ViewModel に要求する契約。 */
interface RelatedUsersActions {
    val relatedUsersIllustiaState: StateFlow<IllustiaUiState>

    fun loadSelectedRelatedUsers(
        targetUserId: Long,
        force: Boolean,
    )

    fun loadMoreSelectedRelatedUsers(targetUserId: Long)
}
