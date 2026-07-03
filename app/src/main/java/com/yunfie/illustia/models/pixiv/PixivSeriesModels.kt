package com.yunfie.illustia.models.pixiv

data class IllustSeriesDetailResponse(
    val illustSeriesContext: IllustSeriesContext? = null,
    val illustSeriesDetail: IllustSeriesDetail? = null,
)

data class IllustSeriesContext(
    val contentOrder: Int? = null,
    val next: Illusts? = null,
    val prev: Illusts? = null,
)

data class IllustSeriesDetail(
    val height: Int,
    val seriesWorkCount: Int,
    val id: Long,
    val createDate: String,
    val title: String,
    val width: Int,
    val coverImageUrls: CoverImageUrls,
    val watchlistAdded: Boolean,
    val caption: String,
    val user: IllustSeriesUser? = null,
)

data class IllustSeriesUser(
    val id: Long,
    val account: String,
    val name: String,
    val profileImageUrls: IllustSeriesProfileImageUrls? = null,
    val isFollowed: Boolean,
)

data class IllustSeriesProfileImageUrls(
    val medium: String? = null,
)

data class CoverImageUrls(
    val medium: String? = null,
)

data class IllustSeriesWithIdModel(
    val illustSeriesDetail: IllustSeriesDetail? = null,
    val illustSeriesFirstIllust: Illusts? = null,
    val illusts: List<Illusts>? = null,
    val nextUrl: String? = null,
)
