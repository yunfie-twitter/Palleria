package com.yunfie.illustia.models

import androidx.annotation.StringRes
import com.yunfie.illustia.R

/** 検索 UI で表示するラベル。API 値は shared 側の enum が持つ。 */
@get:StringRes
val SearchSort.labelResId: Int
    get() =
        when (this) {
            SearchSort.DateDesc -> R.string.sort_date_desc
            SearchSort.DateAsc -> R.string.sort_date_asc
            SearchSort.PopularDesc -> R.string.sort_popular_desc
        }

@get:StringRes
val SearchTarget.labelResId: Int
    get() =
        when (this) {
            SearchTarget.PartialTags -> R.string.search_target_tags
            SearchTarget.ExactTags -> R.string.search_target_exact
            SearchTarget.TitleAndCaption -> R.string.search_target_title
        }

@get:StringRes
val SearchWorkType.labelResId: Int
    get() =
        when (this) {
            SearchWorkType.Artworks -> R.string.search_work_type_artworks
            SearchWorkType.IllustrationsAndUgoira -> R.string.search_work_type_illustrations_and_ugoira
            SearchWorkType.Illustrations -> R.string.search_work_type_illustrations
            SearchWorkType.Ugoira -> R.string.search_work_type_ugoira
            SearchWorkType.Manga -> R.string.search_work_type_manga
            SearchWorkType.Novels -> R.string.search_work_type_novels
        }

@get:StringRes
val SearchDuration.labelResId: Int
    get() =
        when (this) {
            SearchDuration.All -> R.string.duration_all
            SearchDuration.Day -> R.string.duration_24h
            SearchDuration.Week -> R.string.duration_1week
            SearchDuration.Month -> R.string.duration_1month
        }

@get:StringRes
val SearchBookmarkFilter.labelResId: Int
    get() =
        when (this) {
            SearchBookmarkFilter.None -> R.string.bookmark_filter_none
            SearchBookmarkFilter.Over100 -> R.string.bookmark_filter_100
            SearchBookmarkFilter.Over500 -> R.string.bookmark_filter_500
            SearchBookmarkFilter.Over1000 -> R.string.bookmark_filter_1000
            SearchBookmarkFilter.Over5000 -> R.string.bookmark_filter_5000
        }
