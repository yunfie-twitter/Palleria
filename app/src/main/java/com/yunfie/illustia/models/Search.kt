package com.yunfie.illustia.models

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.yunfie.illustia.R

@Immutable
enum class SearchSort(
    val apiValue: String,
    @StringRes val labelResId: Int,
) {
    DateDesc("date_desc", R.string.sort_date_desc),
    DateAsc("date_asc", R.string.sort_date_asc),
    PopularDesc("popular_desc", R.string.sort_popular_desc),
}

@Immutable
enum class SearchTarget(
    val apiValue: String,
    @StringRes val labelResId: Int,
) {
    PartialTags("partial_match_for_tags", R.string.search_target_tags),
    ExactTags("exact_match_for_tags", R.string.search_target_exact),
    TitleAndCaption("title_and_caption", R.string.search_target_title),
}

@Immutable
enum class SearchWorkType(
    @StringRes val labelResId: Int,
) {
    Artworks(R.string.search_work_type_artworks),
    IllustrationsAndUgoira(R.string.search_work_type_illustrations_and_ugoira),
    Illustrations(R.string.search_work_type_illustrations),
    Ugoira(R.string.search_work_type_ugoira),
    Manga(R.string.search_work_type_manga),
    Novels(R.string.search_work_type_novels),
    ;

    val isNovel: Boolean
        get() = this == Novels

    val apiType: String?
        get() =
            when (this) {
                Illustrations -> "illust"
                Manga -> "manga"
                Ugoira -> "ugoira"
                else -> null
            }

    fun acceptsIllustType(type: String): Boolean =
        when (this) {
            Artworks -> type == "illust" || type == "manga" || type == "ugoira"
            IllustrationsAndUgoira -> type == "illust" || type == "ugoira"
            Illustrations -> type == "illust"
            Ugoira -> type == "ugoira"
            Manga -> type == "manga"
            Novels -> false
        }
}

@Immutable
enum class SearchDuration(
    val apiValue: String?,
    @StringRes val labelResId: Int,
) {
    All(null, R.string.duration_all),
    Day("within_last_day", R.string.duration_24h),
    Week("within_last_week", R.string.duration_1week),
    Month("within_last_month", R.string.duration_1month),
}

@Immutable
enum class SearchBookmarkFilter(
    val keyword: String?,
    @StringRes val labelResId: Int,
) {
    None(null, R.string.bookmark_filter_none),
    Over100("100users入り", R.string.bookmark_filter_100),
    Over500("500users入り", R.string.bookmark_filter_500),
    Over1000("1000users入り", R.string.bookmark_filter_1000),
    Over5000("5000users入り", R.string.bookmark_filter_5000),
}
