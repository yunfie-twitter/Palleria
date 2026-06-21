package com.yunfie.illustia.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

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

internal fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArrayOrEmpty(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())
