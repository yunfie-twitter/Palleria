package com.yunfie.illustia.models

import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.bookmark_filter_100
import com.yunfie.illustia.resources.bookmark_filter_1000
import com.yunfie.illustia.resources.bookmark_filter_500
import com.yunfie.illustia.resources.bookmark_filter_5000
import com.yunfie.illustia.resources.bookmark_filter_none
import com.yunfie.illustia.resources.duration_1month
import com.yunfie.illustia.resources.duration_1week
import com.yunfie.illustia.resources.duration_24h
import com.yunfie.illustia.resources.duration_all
import com.yunfie.illustia.resources.search_target_exact
import com.yunfie.illustia.resources.search_target_tags
import com.yunfie.illustia.resources.search_target_title
import com.yunfie.illustia.resources.search_work_type_artworks
import com.yunfie.illustia.resources.search_work_type_illustrations
import com.yunfie.illustia.resources.search_work_type_illustrations_and_ugoira
import com.yunfie.illustia.resources.search_work_type_manga
import com.yunfie.illustia.resources.search_work_type_novels
import com.yunfie.illustia.resources.search_work_type_ugoira
import com.yunfie.illustia.resources.sort_date_asc
import com.yunfie.illustia.resources.sort_date_desc
import com.yunfie.illustia.resources.sort_popular_desc
import org.jetbrains.compose.resources.StringResource

/** 検索 UI で表示するラベル。API 値は shared 側の enum が持つ。 */
val SearchSort.labelResId: StringResource
    get() =
        when (this) {
            SearchSort.DateDesc -> Res.string.sort_date_desc
            SearchSort.DateAsc -> Res.string.sort_date_asc
            SearchSort.PopularDesc -> Res.string.sort_popular_desc
        }

val SearchTarget.labelResId: StringResource
    get() =
        when (this) {
            SearchTarget.PartialTags -> Res.string.search_target_tags
            SearchTarget.ExactTags -> Res.string.search_target_exact
            SearchTarget.TitleAndCaption -> Res.string.search_target_title
        }

val SearchWorkType.labelResId: StringResource
    get() =
        when (this) {
            SearchWorkType.Artworks -> Res.string.search_work_type_artworks
            SearchWorkType.IllustrationsAndUgoira -> Res.string.search_work_type_illustrations_and_ugoira
            SearchWorkType.Illustrations -> Res.string.search_work_type_illustrations
            SearchWorkType.Ugoira -> Res.string.search_work_type_ugoira
            SearchWorkType.Manga -> Res.string.search_work_type_manga
            SearchWorkType.Novels -> Res.string.search_work_type_novels
        }

val SearchDuration.labelResId: StringResource
    get() =
        when (this) {
            SearchDuration.All -> Res.string.duration_all
            SearchDuration.Day -> Res.string.duration_24h
            SearchDuration.Week -> Res.string.duration_1week
            SearchDuration.Month -> Res.string.duration_1month
        }

val SearchBookmarkFilter.labelResId: StringResource
    get() =
        when (this) {
            SearchBookmarkFilter.None -> Res.string.bookmark_filter_none
            SearchBookmarkFilter.Over100 -> Res.string.bookmark_filter_100
            SearchBookmarkFilter.Over500 -> Res.string.bookmark_filter_500
            SearchBookmarkFilter.Over1000 -> Res.string.bookmark_filter_1000
            SearchBookmarkFilter.Over5000 -> Res.string.bookmark_filter_5000
        }
