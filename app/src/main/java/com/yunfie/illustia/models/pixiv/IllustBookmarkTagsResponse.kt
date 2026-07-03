package com.yunfie.illustia.models.pixiv

data class IllustBookmarkTagsResponse(
    val bookmarkTags: List<BookmarkTag> = emptyList(),
    val nextUrl: String? = null,
)

data class BookmarkTag(
    val name: String,
    val count: Int,
)
