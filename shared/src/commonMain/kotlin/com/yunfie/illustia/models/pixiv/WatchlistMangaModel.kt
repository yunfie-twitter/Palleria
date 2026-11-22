package com.yunfie.illustia.models.pixiv

data class WatchlistMangaModel(
    val series: List<MangaSeriesModel> = emptyList(),
    val nextUrl: String? = null,
)

data class MangaSeriesModel(
    val id: Long,
    val url: String? = null,
    val publishedContentCount: Int = 0,
    val title: String,
    val user: MangaSeriesUser? = null,
    val lastPublishedContentDatetime: String? = null,
    val latestContentId: Long = 0L,
    val thumbnailUrl: String? = null,
)

data class MangaSeriesUser(
    val id: Long,
    val name: String,
    val account: String? = null,
    val profileImageUrls: MangaSeriesProfileImageUrls? = null,
)

data class MangaSeriesProfileImageUrls(
    val medium: String? = null,
)
