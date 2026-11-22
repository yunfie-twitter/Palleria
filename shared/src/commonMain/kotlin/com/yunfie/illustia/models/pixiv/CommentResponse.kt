package com.yunfie.illustia.models.pixiv

data class CommentResponse(
    val totalComments: Int? = null,
    val comments: List<Comment> = emptyList(),
    val nextUrl: String? = null,
)

data class Comment(
    val id: Long? = null,
    val comment: String? = null,
    val date: String? = null,
    val user: CommentUser? = null,
    val parentComment: Comment? = null,
    val hasReplies: Boolean? = null,
    val stamp: CommentStamp? = null,
)

data class CommentUser(
    val id: Long? = null,
    val name: String,
    val account: String,
    val profileImageUrls: CommentProfileImageUrls,
)

data class CommentStamp(
    val stampId: Int? = null,
    val stampUrl: String? = null,
)

data class CommentProfileImageUrls(
    val medium: String,
)
