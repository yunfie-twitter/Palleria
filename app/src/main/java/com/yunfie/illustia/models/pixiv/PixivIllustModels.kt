package com.yunfie.illustia.models.pixiv

data class MetaPages(
    val imageUrls: MetaPagesImageUrls? = null,
)

data class MetaPagesImageUrls(
    val squareMedium: String,
    val medium: String,
    val large: String,
    val original: String,
)

data class Illusts(
    val id: Long,
    val title: String,
    val type: String,
    val imageUrls: ImageUrls,
    val caption: String,
    val restrict: Int,
    val user: PixivUser,
    val tags: List<PixivTag> = emptyList(),
    val tools: List<String> = emptyList(),
    val createDate: String,
    val pageCount: Int,
    val width: Int,
    val height: Int,
    val sanityLevel: Int,
    val xRestrict: Int,
    val metaSinglePage: MetaSinglePage? = null,
    val metaPages: List<MetaPages> = emptyList(),
    val totalView: Int,
    val totalBookmarks: Int,
    val isBookmarked: Boolean,
    val visible: Boolean,
    val isMuted: Boolean,
    val illustAIType: Int,
    val series: IllustSeries? = null,
    val illustBookStyle: Int? = null,
    val totalComments: Int? = null,
)

data class IllustSeries(
    val id: Long,
    val title: String? = null,
)

data class ImageUrls(
    val squareMedium: String,
    val medium: String,
    val large: String,
)

data class PixivUser(
    val id: Long,
    val name: String,
    val account: String,
    val profileImageUrls: PixivProfileImageUrls,
    val comment: String? = null,
    val isFollowed: Boolean? = null,
)

data class PixivProfileImageUrls(
    val medium: String,
)

data class PixivTag(
    val name: String,
    val translatedName: String? = null,
)

data class MetaSinglePage(
    val originalImageUrl: String? = null,
)
