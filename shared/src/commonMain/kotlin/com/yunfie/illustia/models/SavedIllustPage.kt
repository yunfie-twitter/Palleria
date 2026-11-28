package com.yunfie.illustia.models

/**
 * オフライン保存された作品の 1 ページ分の、プラットフォーム非依存表現。
 *
 * Room の `SavedIllustPageEntity` は `SettingsStore` の内側でこの型から写像する。
 */
data class SavedIllustPage(
    val illustId: Long,
    val pageIndex: Int,
    val localPath: String,
    val sourceUrl: String,
)
