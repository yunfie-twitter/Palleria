package com.yunfie.illustia.models

/**
 * オフライン保存された作品のプラットフォーム非依存表現。
 *
 * Room の `SavedIllustEntity` はこの型へ写像してから UI 状態へ載せる。
 */
data class SavedIllust(
    val illustId: Long,
    val title: String,
    val artistName: String,
    val artistId: Long,
    val thumbUrl: String,
    val localCoverPath: String?,
    val localPagePathsJson: String,
    val pageCount: Int,
    val savedAt: Long,
    val saveGroup: String,
    val xRestrict: Int,
)
