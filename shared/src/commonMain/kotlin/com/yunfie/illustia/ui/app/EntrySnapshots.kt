package com.yunfie.illustia.ui.app

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.models.pixiv.Comment

data class DetailEntrySnapshot(
    val illust: Illust,
    val relatedIllusts: List<Illust> = emptyList(),
    val relatedIllustsLoaded: Boolean = false,
    val firstComment: Comment? = null,
    val user: UserProfile? = null,
    val isRefreshing: Boolean = false,
)

data class SearchEntrySnapshot(
    val query: String,
    val searchItems: List<Illust> = emptyList(),
    val searchNextUrl: String? = null,
    val searchNovelItems: List<NovelPreview> = emptyList(),
    val searchNovelNextUrl: String? = null,
    val userSearchItems: List<UserPreview> = emptyList(),
    val userSearchNextUrl: String? = null,
    val selectedTab: Int = 0,
)
