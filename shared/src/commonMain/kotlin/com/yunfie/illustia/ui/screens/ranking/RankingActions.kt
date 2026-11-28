package com.yunfie.illustia.ui.screens.ranking

import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.models.Illust
import kotlinx.coroutines.flow.StateFlow

/**
 * ランキング画面が ViewModel に要求する契約。
 * モード別の描画状態は [illustiaState] から `rankingUiState` を読む。
 */
interface RankingActions {
    val rankingIllustiaState: StateFlow<IllustiaUiState>

    fun selectRankingMode(mode: String)

    fun loadRankingModeIfNeeded(mode: String)

    fun refreshRanking(
        mode: String,
        forceRefresh: Boolean,
    )

    fun loadMoreRanking(mode: String)

    fun toggleBookmarkFromCard(illust: Illust)

    fun openBookmarkTagPicker(illust: Illust)

    fun openIllust(illust: Illust)

    fun onIllustLongPressById(illustId: Long)
}
