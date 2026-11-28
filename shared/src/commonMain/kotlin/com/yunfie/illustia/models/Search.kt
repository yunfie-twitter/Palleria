package com.yunfie.illustia.models

import androidx.compose.runtime.Immutable

@Immutable
@kotlinx.serialization.Serializable
enum class SearchSort(
    val apiValue: String,
) {
    DateDesc("date_desc"),
    DateAsc("date_asc"),
    PopularDesc("popular_desc"),
}

@Immutable
@kotlinx.serialization.Serializable
enum class SearchTarget(
    val apiValue: String,
) {
    PartialTags("partial_match_for_tags"),
    ExactTags("exact_match_for_tags"),
    TitleAndCaption("title_and_caption"),
}

@Immutable
@kotlinx.serialization.Serializable
enum class SearchWorkType(
    val apiValue: String?,
) {
    Artworks(null),
    IllustrationsAndUgoira(null),
    Illustrations("illust"),
    Ugoira("ugoira"),
    Manga("manga"),
    Novels(null),
    ;

    val isNovel: Boolean
        get() = this == Novels

    val apiType: String?
        get() = apiValue

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
@kotlinx.serialization.Serializable
enum class SearchDuration(
    val apiValue: String?,
) {
    All(null),
    Day("within_last_day"),
    Week("within_last_week"),
    Month("within_last_month"),
}

@Immutable
@kotlinx.serialization.Serializable
enum class SearchBookmarkFilter(
    val keyword: String?,
) {
    None(null),
    Over100("100users入り"),
    Over500("500users入り"),
    Over1000("1000users入り"),
    Over5000("5000users入り"),
}
