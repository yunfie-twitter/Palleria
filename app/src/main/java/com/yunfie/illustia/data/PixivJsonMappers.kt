package com.yunfie.illustia.data

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.pixiv.IllustSeries
import com.yunfie.illustia.models.pixiv.Comment
import com.yunfie.illustia.models.pixiv.CommentProfileImageUrls
import com.yunfie.illustia.models.pixiv.CommentResponse
import com.yunfie.illustia.models.pixiv.CommentStamp
import com.yunfie.illustia.models.pixiv.CommentUser
import com.yunfie.illustia.models.pixiv.CoverImageUrls
import com.yunfie.illustia.models.pixiv.IllustSeriesContext
import com.yunfie.illustia.models.pixiv.IllustSeriesDetail
import com.yunfie.illustia.models.pixiv.IllustSeriesUser
import com.yunfie.illustia.models.pixiv.IllustSeriesWithIdModel
import com.yunfie.illustia.models.pixiv.MangaSeriesModel
import com.yunfie.illustia.models.pixiv.MangaSeriesProfileImageUrls
import com.yunfie.illustia.models.pixiv.MangaSeriesUser
import com.yunfie.illustia.models.pixiv.UgoiraFrame
import com.yunfie.illustia.models.pixiv.UgoiraMetadata
import com.yunfie.illustia.models.pixiv.UgoiraMetadataResponse
import com.yunfie.illustia.models.pixiv.UgoiraZipUrls
import com.yunfie.illustia.models.pixiv.WatchlistMangaModel
import com.yunfie.illustia.models.UserPreview
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull

internal fun JsonObject.toIllustOrNull(): Illust? {
    val id = long("id") ?: return null
    val user = this["user"].asObjectOrNull()
    val userImageUrls = user?.get("profile_image_urls").asObjectOrNull()
    val imageUrls = this["image_urls"].asObjectOrNull()
    val mediumPages = imagePageUrls("medium")
    val pages = imagePageUrls("large")
    val originalPages = imagePageUrls("original")
    val original = originalImageUrl(originalPages)
    val medium = imageUrls?.string("medium")
        ?: imageUrls?.string("square_medium")
        ?: imageUrls?.string("large")
        ?: original
    return Illust(
        id = id,
        title = string("title").orEmpty(),
        type = string("type") ?: "illust",
        caption = string("caption").orEmpty(),
        artistId = user?.long("id") ?: 0L,
        artistName = user?.string("name").orEmpty(),
        artistAvatarUrl = userImageUrls?.string("medium"),
        squareImageUrl = imageUrls?.string("square_medium").orEmpty(),
        mediumImageUrl = medium.orEmpty(),
        imageUrl = imageUrls?.string("large") ?: imageUrls?.string("medium") ?: original.orEmpty(),
        originalImageUrl = original,
        mediumImagePages = mediumPages.ifEmpty { pages.ifEmpty { originalPages } },
        imagePages = pages.ifEmpty { originalPages },
        originalImagePages = originalPages,
        tags = tags(),
        pageCount = long("page_count")?.toInt() ?: 1,
        isBookmarked = this["is_bookmarked"]?.jsonPrimitive?.booleanOrNull ?: false,
        totalComments = int("total_comments"),
        series = this["series"].asObjectOrNull()?.let { series ->
            IllustSeries(id = series.long("id") ?: 0L, title = series.string("title"))
        },
    )
}

internal fun JsonObject.toUserPreviewOrNull(): UserPreview? {
    val user = this["user"].asObjectOrNull() ?: return null
    val imageUrls = user["profile_image_urls"].asObjectOrNull()
    return UserPreview(
        id = user.long("id") ?: return null,
        name = user.string("name").orEmpty(),
        account = user.string("account").orEmpty(),
        profileImageUrl = imageUrls?.string("medium"),
        comment = user.string("comment").orEmpty(),
        isFollowed = user["is_followed"]?.jsonPrimitive?.booleanOrNull ?: false,
        previewIllusts = this["illusts"].asArrayOrEmpty().mapNotNull { it.asObjectOrNull()?.toIllustOrNull() },
    )
}

internal fun JsonObject.toCommentResponseOrNull(): CommentResponse {
    return CommentResponse(
        totalComments = int("total_comments"),
        comments = this["comments"].asArrayOrEmpty().mapNotNull { it.asObjectOrNull()?.toCommentOrNull() },
        nextUrl = string("next_url"),
    )
}

internal fun JsonObject.toWatchlistMangaModelOrNull(): WatchlistMangaModel {
    return WatchlistMangaModel(
        series = this["series"].asArrayOrEmpty().mapNotNull { it.asObjectOrNull()?.toMangaSeriesOrNull() },
        nextUrl = string("next_url"),
    )
}

internal fun JsonObject.toIllustSeriesWithIdModelOrNull(): IllustSeriesWithIdModel {
    return IllustSeriesWithIdModel(
        illustSeriesDetail = this["illust_series_detail"].asObjectOrNull()?.toIllustSeriesDetailOrNull(),
        illustSeriesFirstIllust = this["illust_series_first_illust"].asObjectOrNull()?.toIllustOrNullSeries(),
        illusts = this["illusts"].asArrayOrEmpty().mapNotNull { it.asObjectOrNull()?.toIllustOrNullSeries() },
        nextUrl = string("next_url"),
    )
}

internal fun JsonObject.toUgoiraMetadataResponseOrNull(): UgoiraMetadataResponse {
    val metadata = this["ugoira_metadata"].asObjectOrNull()
        ?: return UgoiraMetadataResponse(UgoiraMetadata(UgoiraZipUrls(""), emptyList()))
    val zipUrls = metadata["zip_urls"].asObjectOrNull()
    return UgoiraMetadataResponse(
        ugoiraMetadata = UgoiraMetadata(
            zipUrls = UgoiraZipUrls(medium = zipUrls?.string("medium").orEmpty()),
            frames = metadata["frames"].asArrayOrEmpty().mapNotNull { frame ->
                frame.asObjectOrNull()?.let {
                    UgoiraFrame(file = it.string("file").orEmpty(), delay = it.int("delay") ?: 0)
                }
            },
        ),
    )
}

internal fun JsonObject.toCommentOrNull(): Comment? {
    val user = this["user"].asObjectOrNull()
    return Comment(
        id = long("id"),
        comment = string("comment"),
        date = string("date"),
        user = user?.toCommentUserOrNull(),
        parentComment = this["parent_comment"].asObjectOrNull()?.toCommentOrNull(),
        hasReplies = boolean("has_replies"),
        stamp = this["stamp"].asObjectOrNull()?.toCommentStampOrNull(),
    )
}

internal fun JsonObject.toCommentUserOrNull(): CommentUser? {
    val profile = this["profile_image_urls"].asObjectOrNull()
    return CommentUser(
        id = long("id"),
        name = string("name").orEmpty(),
        account = string("account").orEmpty(),
        profileImageUrls = CommentProfileImageUrls(medium = profile?.string("medium").orEmpty()),
    )
}

internal fun JsonObject.toCommentStampOrNull(): CommentStamp? {
    return CommentStamp(
        stampId = int("stamp_id"),
        stampUrl = string("stamp_url"),
    )
}

internal fun JsonObject.toMangaSeriesOrNull(): MangaSeriesModel? {
    val user = this["user"].asObjectOrNull()
    val profile = user?.get("profile_image_urls").asObjectOrNull()
    return MangaSeriesModel(
        id = long("id") ?: return null,
        url = string("url"),
        publishedContentCount = int("published_content_count") ?: 0,
        title = string("title").orEmpty(),
        user = user?.let {
            MangaSeriesUser(
                id = it.long("id") ?: 0L,
                name = it.string("name").orEmpty(),
                account = it.string("account"),
                profileImageUrls = profile?.let { imageUrls ->
                    MangaSeriesProfileImageUrls(medium = imageUrls.string("medium"))
                },
            )
        },
        lastPublishedContentDatetime = string("last_published_content_datetime"),
        latestContentId = long("latest_content_id") ?: 0L,
    )
}

internal fun JsonObject.toIllustSeriesDetailOrNull(): IllustSeriesDetail? {
    val user = this["user"].asObjectOrNull()
    val cover = this["cover_image_urls"].asObjectOrNull()
    return IllustSeriesDetail(
        height = int("height") ?: 0,
        seriesWorkCount = int("series_work_count") ?: 0,
        id = long("id") ?: return null,
        createDate = string("create_date").orEmpty(),
        title = string("title").orEmpty(),
        width = int("width") ?: 0,
        coverImageUrls = com.yunfie.illustia.models.pixiv.CoverImageUrls(
            medium = cover?.string("medium"),
        ),
        watchlistAdded = boolean("watchlist_added") ?: false,
        caption = string("caption").orEmpty(),
        user = user?.toIllustSeriesUserOrNull(),
    )
}

internal fun JsonObject.toIllustSeriesUserOrNull(): IllustSeriesUser? {
    val profile = this["profile_image_urls"].asObjectOrNull()
    return IllustSeriesUser(
        id = long("id") ?: return null,
        account = string("account").orEmpty(),
        name = string("name").orEmpty(),
        profileImageUrls = profile?.let {
            com.yunfie.illustia.models.pixiv.IllustSeriesProfileImageUrls(medium = it.string("medium"))
        },
        isFollowed = boolean("is_followed") ?: false,
    )
}

private fun JsonObject.toIllustOrNullSeries(): com.yunfie.illustia.models.pixiv.Illusts? {
    val id = long("id") ?: return null
    val user = this["user"].asObjectOrNull()
    val userImageUrls = user?.get("profile_image_urls").asObjectOrNull()
    val imageUrls = this["image_urls"].asObjectOrNull()
    val mediumPages = imagePageUrls("medium")
    val pages = imagePageUrls("large")
    val originalPages = imagePageUrls("original")
    val original = originalImageUrl(originalPages)
    val medium = imageUrls?.string("medium")
        ?: imageUrls?.string("square_medium")
        ?: imageUrls?.string("large")
        ?: original
    return com.yunfie.illustia.models.pixiv.Illusts(
        id = id,
        title = string("title").orEmpty(),
        type = string("type") ?: "illust",
        imageUrls = com.yunfie.illustia.models.pixiv.ImageUrls(
            squareMedium = imageUrls?.string("square_medium").orEmpty(),
            medium = medium.orEmpty(),
            large = imageUrls?.string("large") ?: imageUrls?.string("medium") ?: original.orEmpty(),
        ),
        caption = string("caption").orEmpty(),
        restrict = int("restrict") ?: 0,
        user = com.yunfie.illustia.models.pixiv.PixivUser(
            id = user?.long("id") ?: 0L,
            name = user?.string("name").orEmpty(),
            account = user?.string("account").orEmpty(),
            profileImageUrls = com.yunfie.illustia.models.pixiv.PixivProfileImageUrls(
                medium = userImageUrls?.string("medium").orEmpty(),
            ),
            comment = user?.string("comment"),
            isFollowed = user?.get("is_followed")?.jsonPrimitive?.booleanOrNull,
        ),
        tags = tags().map { com.yunfie.illustia.models.pixiv.PixivTag(it, null) },
        tools = this["tools"].asArrayOrEmpty().mapNotNull { it.jsonPrimitive.contentOrNull },
        createDate = string("create_date").orEmpty(),
        pageCount = long("page_count")?.toInt() ?: 1,
        width = int("width") ?: 0,
        height = int("height") ?: 0,
        sanityLevel = int("sanity_level") ?: 0,
        xRestrict = int("x_restrict") ?: 0,
        metaSinglePage = this["meta_single_page"].asObjectOrNull()?.let {
            com.yunfie.illustia.models.pixiv.MetaSinglePage(originalImageUrl = it.string("original_image_url"))
        },
        metaPages = this["meta_pages"].asArrayOrEmpty().mapNotNull { page ->
            page.asObjectOrNull()?.get("image_urls").asObjectOrNull()?.let { image ->
                com.yunfie.illustia.models.pixiv.MetaPages(
                    imageUrls = com.yunfie.illustia.models.pixiv.MetaPagesImageUrls(
                        squareMedium = image.string("square_medium").orEmpty(),
                        medium = image.string("medium").orEmpty(),
                        large = image.string("large").orEmpty(),
                        original = image.string("original").orEmpty(),
                    ),
                )
            }
        },
        totalView = int("total_view") ?: 0,
        totalBookmarks = int("total_bookmarks") ?: 0,
        isBookmarked = this["is_bookmarked"]?.jsonPrimitive?.booleanOrNull ?: false,
        visible = this["visible"]?.jsonPrimitive?.booleanOrNull ?: true,
        isMuted = this["is_muted"]?.jsonPrimitive?.booleanOrNull ?: false,
        illustAIType = int("illust_ai_type") ?: 0,
        series = this["series"].asObjectOrNull()?.let { s ->
            com.yunfie.illustia.models.pixiv.IllustSeries(id = s.long("id") ?: 0L, title = s.string("title"))
        },
        illustBookStyle = int("illust_book_style"),
        totalComments = int("total_comments"),
    )
}

private fun JsonObject.originalImageUrl(pages: List<String>): String? =
    this["meta_single_page"].asObjectOrNull()?.string("original_image_url") ?: pages.firstOrNull()

private fun JsonObject.imagePageUrls(quality: String): List<String> =
    this["meta_pages"].asArrayOrEmpty().mapNotNull { page ->
        val imageUrls = page.asObjectOrNull()?.get("image_urls").asObjectOrNull()
        imageUrls?.string(quality) ?: imageUrls?.string("large")
    }

private fun JsonObject.tags(): List<String> =
    this["tags"].asArrayOrEmpty().mapNotNull { it.asObjectOrNull()?.string("name") }

internal fun JsonObject.string(name: String): String? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    return value.jsonPrimitive.contentOrNull
}

internal fun JsonObject.long(name: String): Long? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    return value.jsonPrimitive.longOrNull
}

internal fun JsonObject.int(name: String): Int? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    return value.jsonPrimitive.intOrNull
}

internal fun JsonObject.boolean(name: String): Boolean? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    return value.jsonPrimitive.booleanOrNull
}

internal fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArrayOrEmpty(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())
