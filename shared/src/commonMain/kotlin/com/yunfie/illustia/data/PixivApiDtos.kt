package com.yunfie.illustia.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Pixiv API のワイヤ DTO。値の意味づけはデコーダ側で行う。 */
internal object PixivApiJson {
    val json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }
}

@Serializable
internal data class ImageUrlsDto(
    @SerialName("square_medium") val squareMedium: String? = null,
    @SerialName("medium") val medium: String? = null,
    @SerialName("large") val large: String? = null,
    @SerialName("original") val original: String? = null,
    @SerialName("original_image_url") val originalImageUrl: String? = null,
) {
    val effectiveOriginal: String? get() = original ?: originalImageUrl
}

@Serializable
internal data class UserDto(
    val id: Long = 0L,
    val name: String = "",
    val account: String = "",
    val comment: String? = null,
    @SerialName("profile_image_urls") val profileImageUrls: ImageUrlsDto = ImageUrlsDto(),
    @SerialName("is_followed") val isFollowed: Boolean? = null,
)

@Serializable
internal data class ProfileDto(
    @SerialName("background_image_url") val backgroundImageUrl: String? = null,
    val comment: String = "",
)

@Serializable
internal data class MetaPageDto(
    @SerialName("image_urls") val imageUrls: ImageUrlsDto? = null,
)

@Serializable
internal data class TagDto(
    val name: String? = null,
)

@Serializable
internal data class SeriesDto(
    val id: Long = 0L,
    val title: String? = null,
)

@Serializable
internal data class IllustDto(
    val id: Long? = null,
    val title: String = "",
    @SerialName("type") val illustType: String = "illust",
    val caption: String = "",
    val user: UserDto = UserDto(),
    @SerialName("image_urls") val imageUrls: ImageUrlsDto = ImageUrlsDto(),
    @SerialName("meta_single_page") val metaSinglePage: ImageUrlsDto? = null,
    @SerialName("meta_pages") val metaPages: List<MetaPageDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    @SerialName("page_count") val pageCount: Int = 1,
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("is_bookmarked") val isBookmarked: Boolean = false,
    @SerialName("total_comments") val totalComments: Int? = null,
    val series: SeriesDto? = null,
    @SerialName("total_bookmarks") val totalBookmarks: Int = 0,
    @SerialName("x_restrict") val xRestrict: Int = 0,
    @SerialName("sanity_level") val sanityLevel: Int = 0,
    @SerialName("illust_ai_type") val illustAiType: Int = 0,
)

@Serializable
internal data class IllustPageResponse(
    val illusts: List<IllustDto> = emptyList(),
    @SerialName("next_url") val nextUrl: String? = null,
)

@Serializable
internal data class IllustDetailResponse(
    val illust: IllustDto? = null,
)

@Serializable
internal data class UserDetailResponse(
    val user: UserDto = UserDto(),
    val profile: ProfileDto? = null,
)

@Serializable
internal data class AutocompleteResponse(
    val tags: List<TagDto> = emptyList(),
)

@Serializable
internal data class UserPreviewDto(
    val user: UserDto? = null,
    val illusts: List<IllustDto> = emptyList(),
)

@Serializable
internal data class UserPreviewPageResponse(
    @SerialName("user_previews") val userPreviews: List<UserPreviewDto> = emptyList(),
    @SerialName("next_url") val nextUrl: String? = null,
)

@Serializable
internal data class UgoiraZipUrlsDto(
    val medium: String = "",
)

@Serializable
internal data class UgoiraFrameDto(
    val file: String = "",
    val delay: Int = 0,
)

@Serializable
internal data class UgoiraMetadataDto(
    @SerialName("zip_urls") val zipUrls: UgoiraZipUrlsDto = UgoiraZipUrlsDto(),
    val frames: List<UgoiraFrameDto> = emptyList(),
)

@Serializable
internal data class UgoiraMetadataResponseDto(
    @SerialName("ugoira_metadata") val ugoiraMetadata: UgoiraMetadataDto? = null,
)

@Serializable
internal data class CommentUserDto(
    val id: Long? = null,
    val name: String = "",
    val account: String = "",
    @SerialName("profile_image_urls") val profileImageUrls: ImageUrlsDto = ImageUrlsDto(),
)

@Serializable
internal data class CommentStampDto(
    @SerialName("stamp_id") val stampId: Int? = null,
    @SerialName("stamp_url") val stampUrl: String? = null,
)

@Serializable
internal data class CommentDto(
    val id: Long? = null,
    val comment: String? = null,
    val date: String? = null,
    val user: CommentUserDto? = null,
    @SerialName("parent_comment") val parentComment: CommentDto? = null,
    @SerialName("has_replies") val hasReplies: Boolean? = null,
    val stamp: CommentStampDto? = null,
)

@Serializable
internal data class CommentPageResponse(
    @SerialName("total_comments") val totalComments: Int? = null,
    val comments: List<CommentDto> = emptyList(),
    @SerialName("next_url") val nextUrl: String? = null,
)

@Serializable
internal data class NotificationContentDto(
    val text: String? = null,
    @SerialName("left_icon") val leftIcon: String? = null,
    @SerialName("left_image") val leftImage: String? = null,
    @SerialName("right_icon") val rightIcon: String? = null,
    @SerialName("right_image") val rightImage: String? = null,
)

@Serializable
internal data class NotificationViewMoreDto(
    @SerialName("unread_exists") val unreadExists: Boolean = false,
    val title: String? = null,
)

@Serializable
internal data class NotificationDto(
    val id: Long? = null,
    @SerialName("created_datetime") val createdDatetime: String? = null,
    @SerialName("type") val notificationType: Int = 0,
    val content: NotificationContentDto? = null,
    @SerialName("view_more") val viewMore: NotificationViewMoreDto? = null,
    @SerialName("target_url") val targetUrl: String? = null,
    @SerialName("is_read") val isRead: Boolean = true,
)

@Serializable
internal data class NotificationPageResponse(
    val notifications: List<NotificationDto> = emptyList(),
    @SerialName("next_url") val nextUrl: String? = null,
)

@Serializable
internal data class NovelDto(
    val id: Long? = null,
    val title: String = "",
    val caption: String = "",
    val user: UserDto = UserDto(),
    @SerialName("image_urls") val imageUrls: ImageUrlsDto = ImageUrlsDto(),
    @SerialName("page_count") val pageCount: Int = 0,
    @SerialName("text_length") val textLength: Int = 0,
    @SerialName("is_bookmarked") val isBookmarked: Boolean = false,
    @SerialName("total_bookmarks") val totalBookmarks: Int = 0,
    @SerialName("total_view") val totalView: Int = 0,
)

@Serializable
internal data class NovelPageResponse(
    val novels: List<NovelDto> = emptyList(),
    @SerialName("next_url") val nextUrl: String? = null,
)

@Serializable
internal data class MangaSeriesUserDto(
    val id: Long = 0L,
    val name: String = "",
    val account: String? = null,
    @SerialName("profile_image_urls") val profileImageUrls: ImageUrlsDto? = null,
)

@Serializable
internal data class MangaSeriesDto(
    val id: Long? = null,
    val url: String? = null,
    @SerialName("published_content_count") val publishedContentCount: Int = 0,
    val title: String = "",
    val user: MangaSeriesUserDto? = null,
    @SerialName("last_published_content_datetime") val lastPublishedContentDatetime: String? = null,
    @SerialName("latest_content_id") val latestContentId: Long = 0L,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
)

@Serializable
internal data class WatchlistMangaResponse(
    val series: List<MangaSeriesDto> = emptyList(),
    @SerialName("next_url") val nextUrl: String? = null,
)

@Serializable
internal data class SimpleBooleanResponse(
    @SerialName("show_ai") val showAi: Boolean? = null,
)

@Serializable
internal data class UserFollowDetailResponse(
    @SerialName("is_followed") val isFollowed: Boolean? = null,
    @SerialName("is_follow") val isFollow: Boolean? = null,
    val restrict: String? = null,
)

@Serializable
internal data class StampDto(
    @SerialName("stamp_id") val stampId: Long? = null,
    @SerialName("stamp_url") val stampUrl: String? = null,
)

@Serializable
internal data class StampResponse(
    val stamps: List<StampDto> = emptyList(),
)

@Serializable
internal data class TrendingIllustDto(
    val id: Long? = null,
    @SerialName("image_urls") val imageUrls: ImageUrlsDto? = null,
)

@Serializable
internal data class TrendingTagDto(
    val tag: String? = null,
    @SerialName("translated_name") val translatedName: String? = null,
    val illust: TrendingIllustDto? = null,
)

@Serializable
internal data class TrendingTagResponse(
    @SerialName("trend_tags") val trendTags: List<TrendingTagDto> = emptyList(),
)

@Serializable
internal data class BookmarkTagDto(
    val name: String = "",
    val count: Int = 0,
)

@Serializable
internal data class IllustBookmarkTagsResponse(
    @SerialName("bookmark_tags") val bookmarkTags: List<BookmarkTagDto> = emptyList(),
)

@Serializable
internal data class SpotlightArticleDto(
    val id: Long? = null,
    val title: String = "",
    @SerialName("pure_title") val pureTitle: String = "",
    val thumbnail: String = "",
    @SerialName("article_url") val articleUrl: String = "",
    @SerialName("publish_date") val publishDate: String = "",
)

@Serializable
internal data class SpotlightResponse(
    @SerialName("spotlight_articles") val spotlightArticles: List<SpotlightArticleDto> = emptyList(),
    @SerialName("next_url") val nextUrl: String? = null,
)

@Serializable
internal data class UserMeProfileDto(
    @SerialName("user_id") val userId: Long? = null,
    @SerialName("pixiv_id") val pixivId: String = "",
    val name: String = "",
    @SerialName("profile_image_urls") val profileImageUrls: ImageUrlsDto? = null,
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("x_restrict") val xRestrict: Int = 0,
)

@Serializable
internal data class UserMeResponse(
    val profile: UserMeProfileDto? = null,
)

@Serializable
internal data class AccountEditBodyDto(
    @SerialName("is_succeed") val isSucceed: Boolean? = null,
    @SerialName("validation_errors") val validationErrors: Map<String, JsonElement> = emptyMap(),
)

@Serializable
internal data class AccountEditResponse(
    val body: AccountEditBodyDto? = null,
    val error: Boolean = false,
    val message: String = "",
)

@Serializable
internal data class SeriesUserDto(
    val id: Long? = null,
    val account: String = "",
    val name: String = "",
    @SerialName("profile_image_urls") val profileImageUrls: ImageUrlsDto? = null,
    @SerialName("is_followed") val isFollowed: Boolean = false,
)

@Serializable
internal data class IllustSeriesDetailDto(
    val height: Int = 0,
    @SerialName("series_work_count") val seriesWorkCount: Int = 0,
    val id: Long? = null,
    @SerialName("create_date") val createDate: String = "",
    val title: String = "",
    val width: Int = 0,
    @SerialName("cover_image_urls") val coverImageUrls: ImageUrlsDto? = null,
    @SerialName("watchlist_added") val watchlistAdded: Boolean = false,
    val caption: String = "",
    val user: SeriesUserDto? = null,
)

@Serializable
internal data class IllustSeriesPageResponse(
    @SerialName("illust_series_detail") val illustSeriesDetail: IllustSeriesDetailDto? = null,
    @SerialName("illust_series_first_illust") val illustSeriesFirstIllust: IllustDto? = null,
    val illusts: List<IllustDto> = emptyList(),
    @SerialName("next_url") val nextUrl: String? = null,
)

@Serializable
internal data class OAuthResponse(
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: OAuthUserDto? = null,
)

@Serializable
internal data class OAuthUserDto(
    val id: JsonElement? = null,
)

@Serializable
internal data class NovelTextDto(
    val title: String = "",
    val text: String = "",
    @SerialName("novel_text") val novelText: String = "",
    @SerialName("seriesNavigation") val seriesNavigation: SeriesNavigationDto? = null,
)

@Serializable
internal data class SeriesNavigationDto(
    @SerialName("prevNovel") val prevNovel: SeriesNovelDto? = null,
    @SerialName("nextNovel") val nextNovel: SeriesNovelDto? = null,
)

@Serializable
internal data class SeriesNovelDto(
    val id: Long? = null,
    val title: String? = null,
)

/** OAuth のレスポンスは `{response:{...}}` 形式と直下形式の両方がある（Rust 版の untagged 相当）。 */
internal fun parseOAuthResponse(body: String): OAuthResponse {
    val element = PixivApiJson.json.parseToJsonElement(body)
    val wrapped = (element as? JsonObject)?.get("response")
    if (wrapped is JsonObject) {
        return PixivApiJson.json.decodeFromJsonElement(OAuthResponse.serializer(), wrapped)
    }
    return PixivApiJson.json.decodeFromJsonElement(OAuthResponse.serializer(), element)
}

/** `user.id` は数値・文字列どちらでも来るので、数値ならそのまま・文字列なら parse する。 */
internal fun OAuthUserDto.resolveId(): Long? =
    when (val value = id) {
        is JsonPrimitive -> if (value.isString) value.content.toLongOrNull() else value.content.toLongOrNull()
        else -> null
    }

/** novelText の `novel:` マーカー抽出に使う。WebView HTML から `{novel:{...}}` を切り出す。 */
internal fun extractNovelJson(body: String): JsonElement? {
    val trimmed = body.trim()
    val element =
        if (trimmed.startsWith("{")) {
            runCatching { PixivApiJson.json.parseToJsonElement(trimmed) }.getOrNull()
        } else {
            extractNovelMarkerElement(body)
        }
    return element?.jsonObjectOrNull()
}

private fun extractNovelMarkerElement(body: String): JsonElement? {
    val marker = body.indexOf("novel:")
    if (marker < 0) return null
    val source = body.substring(marker + "novel:".length)
    val parsed = runCatching { PixivApiJson.json.parseToJsonElement(source) }.getOrNull()
    return if (parsed is JsonArray) parsed.firstOrNull() else parsed
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
