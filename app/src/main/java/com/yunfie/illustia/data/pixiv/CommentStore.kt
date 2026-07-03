package com.yunfie.illustia.data.pixiv

import com.yunfie.illustia.data.IllustiaRepository
import com.yunfie.illustia.models.pixiv.Comment
import com.yunfie.illustia.models.pixiv.CommentResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class CommentArtworkType {
    ILLUST,
    NOVEL,
}

data class CommentState(
    val comments: List<Comment> = emptyList(),
    val nextUrl: String? = null,
    val errorMessage: String? = null,
    val isEmpty: Boolean = false,
    val isLoading: Boolean = false,
)

class CommentStore(
    private val repository: IllustiaRepository,
    private val id: Long,
    private val parentCommentId: Long? = null,
    private val isReplay: Boolean = false,
    private val type: CommentArtworkType = CommentArtworkType.ILLUST,
) {
    private val _state = MutableStateFlow(CommentState())
    val state: StateFlow<CommentState> = _state.asStateFlow()

    suspend fun fetch() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val response = when {
                type == CommentArtworkType.ILLUST && isReplay -> repository.illustCommentReplies(parentCommentId ?: error("parentCommentId is required"))
                type == CommentArtworkType.ILLUST -> repository.illustComments(id)
                type == CommentArtworkType.NOVEL && isReplay -> repository.novelCommentReplies(parentCommentId ?: error("parentCommentId is required"))
                else -> repository.novelComments(id)
            }
            applyResponse(response)
        } catch (error: Throwable) {
            _state.update { it.copy(isLoading = false, errorMessage = error.toString()) }
        }
    }

    suspend fun next() {
        val nextUrl = _state.value.nextUrl ?: return
        try {
            val response = repository.nextCommentPage(nextUrl)
            applyResponse(response, append = true)
        } catch (error: Throwable) {
            _state.update { it.copy(isLoading = false, errorMessage = error.toString()) }
        }
    }

    suspend fun postComment(text: String, replyToId: Long? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        when (type) {
            CommentArtworkType.ILLUST -> repository.addIllustComment(id, trimmed, replyToId)
            CommentArtworkType.NOVEL -> repository.addNovelComment(id, trimmed, replyToId)
        }
    }

    private fun applyResponse(response: CommentResponse, append: Boolean = false) {
        _state.update { current ->
            current.copy(
                comments = if (append) current.comments + response.comments else response.comments,
                nextUrl = response.nextUrl,
                isEmpty = response.comments.isEmpty() && !append,
                isLoading = false,
                errorMessage = null,
            )
        }
    }
}
