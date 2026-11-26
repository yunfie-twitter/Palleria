package com.yunfie.illustia.data.pixiv

import com.yunfie.illustia.models.pixiv.Comment
import com.yunfie.illustia.models.pixiv.CommentResponse

enum class CommentArtworkType {
    ILLUST,
    NOVEL,
}

data class CommentState(
    val comments: List<Comment> = emptyList(),
    val hasDisabledNotice: Boolean = false,
    val nextUrl: String? = null,
    val errorMessage: String? = null,
    val isEmpty: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isPaginating: Boolean = false,
)

fun Comment.isPixivCommentDisabledNotice(): Boolean = comment?.contains("コメントがオフにされています") == true

/** キャッシュ済みのレスポンスを初期表示用の状態へ変換する（スケルトン表示を挟まないため）。 */
fun CommentResponse.toCommentState(): CommentState =
    CommentState(
        comments = comments,
        hasDisabledNotice = comments.any { it.isPixivCommentDisabledNotice() },
        nextUrl = nextUrl,
        isEmpty = comments.isEmpty(),
    )
