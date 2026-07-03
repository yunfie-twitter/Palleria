package com.yunfie.illustia.models

import androidx.compose.runtime.Immutable

@Immutable
data class NovelPreview(
    val id: Long,
    val title: String,
    val caption: String,
    val userId: Long,
    val userName: String,
    val userAccount: String,
    val coverUrl: String,
    val pageCount: Int,
    val textLength: Int,
    val isBookmarked: Boolean,
    val totalBookmarks: Int,
    val totalView: Int,
)

@Immutable
data class NovelTextContent(
    val novelId: Long,
    val title: String,
    val text: String,
    val seriesPrevId: Long?,
    val seriesPrevTitle: String?,
    val seriesNextId: Long?,
    val seriesNextTitle: String?,
)
