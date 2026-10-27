package com.yunfie.illustia.data.pixiv

import com.yunfie.illustia.data.IllustiaRepository
import com.yunfie.illustia.models.pixiv.Comment
import com.yunfie.illustia.models.pixiv.CommentResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

private fun Comment.isPixivCommentDisabledNotice(): Boolean = comment?.contains("コメントがオフにされています") == true

/** キャッシュ済みのレスポンスを初期表示用の状態へ変換する（スケルトン表示を挟まないため）。 */
internal fun CommentResponse.toCommentState(): CommentState =
    CommentState(
        comments = comments,
        hasDisabledNotice = comments.any { it.isPixivCommentDisabledNotice() },
        nextUrl = nextUrl,
        isEmpty = comments.isEmpty(),
    )

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

class CommentStore(
    private val repository: IllustiaRepository,
    private val id: Long,
    private val parentCommentId: Long? = null,
    private val isReplay: Boolean = false,
    private val type: CommentArtworkType = CommentArtworkType.ILLUST,
) {
    private val _state = MutableStateFlow(CommentState())
    val state: StateFlow<CommentState> = _state.asStateFlow()

    /** 既に取得済みで期限内のコメントを同期的に返す（未取得なら null）。 */
    fun cachedState(): CommentState? = repository.illustCommentsSnapshot(id)?.toCommentState()

    /** キャッシュ済みの内容を、取得を待たずに初期表示として反映する。 */
    fun seed(state: CommentState) {
        _state.value = state
    }

    suspend fun fetch(forceRefresh: Boolean = false) {
        _state.update {
            it.copy(
                isRefreshing = forceRefresh || it.comments.isNotEmpty(),
                isLoading = it.comments.isEmpty(),
                errorMessage = null,
            )
        }
        try {
            // JSON パース（Comment/User/Stamp の生成）を UI スレッドで行うと、シートの
            // スライドアニメーション初回フレームにそのまま割り込むため IO へ逃がす。
            val response =
                withContext(Dispatchers.Default) {
                    when {
                        type == CommentArtworkType.ILLUST && isReplay -> {
                            repository.illustCommentReplies(
                                parentCommentId ?: error("parentCommentId is required"),
                                forceRefresh = forceRefresh,
                            )
                        }

                        type == CommentArtworkType.ILLUST -> {
                            repository.illustComments(id, forceRefresh = forceRefresh)
                        }

                        type == CommentArtworkType.NOVEL && isReplay -> {
                            repository.novelCommentReplies(
                                parentCommentId ?: error("parentCommentId is required"),
                                forceRefresh = forceRefresh,
                            )
                        }

                        else -> {
                            repository.novelComments(id, forceRefresh = forceRefresh)
                        }
                    }
                }
            applyResponse(response)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (expectedFailure: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = expectedFailure.toString(),
                )
            }
        }
    }

    suspend fun next() {
        val nextUrl = _state.value.nextUrl ?: return
        if (_state.value.isPaginating) return
        _state.update { it.copy(isPaginating = true, errorMessage = null) }
        try {
            val response = repository.nextCommentPage(nextUrl)
            applyResponse(response, append = true)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (expectedFailure: Exception) {
            _state.update {
                it.copy(
                    isPaginating = false,
                    errorMessage = expectedFailure.toString(),
                )
            }
        }
    }

    suspend fun postComment(
        text: String,
        replyToId: Long? = null,
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        when (type) {
            CommentArtworkType.ILLUST -> repository.addIllustComment(id, trimmed, replyToId)
            CommentArtworkType.NOVEL -> repository.addNovelComment(id, trimmed, replyToId)
        }
    }

    suspend fun postStampComment(
        stampId: Long,
        replyToId: Long? = null,
    ) {
        if (type == CommentArtworkType.ILLUST) {
            repository.addIllustStampComment(id, stampId, replyToId)
        }
    }

    private fun applyResponse(
        response: CommentResponse,
        append: Boolean = false,
    ) {
        _state.update { current ->
            current.copy(
                comments = if (append) current.comments + response.comments else response.comments,
                hasDisabledNotice =
                    if (append) {
                        current.hasDisabledNotice || response.comments.any { it.isPixivCommentDisabledNotice() }
                    } else {
                        response.comments.any { it.isPixivCommentDisabledNotice() }
                    },
                nextUrl = response.nextUrl,
                isEmpty = response.comments.isEmpty() && !append,
                isLoading = false,
                isRefreshing = false,
                isPaginating = false,
                errorMessage = null,
            )
        }
    }
}
