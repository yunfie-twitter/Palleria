package com.yunfie.illustia.data.pixiv

import com.yunfie.illustia.models.pixiv.Comment
import com.yunfie.illustia.models.pixiv.CommentProfileImageUrls
import com.yunfie.illustia.models.pixiv.CommentResponse
import com.yunfie.illustia.models.pixiv.CommentUser
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Covers the state used to render the comment sheet before the network response arrives:
 * a cached page must produce list content, not the loading skeleton.
 */
class CommentStoreCacheTest :
    StringSpec({
        "cached page maps to list content that is not loading" {
            val comment =
                Comment(
                    id = 1L,
                    comment = "cached",
                    date = "2026-01-01",
                    user =
                        CommentUser(
                            id = 9L,
                            name = "user",
                            account = "account",
                            profileImageUrls = CommentProfileImageUrls("https://example.test/avatar.png"),
                        ),
                )

            val state = CommentResponse(comments = listOf(comment), nextUrl = "next").toCommentState()

            state.comments shouldBe listOf(comment)
            state.isEmpty shouldBe false
            state.isLoading shouldBe false
            state.isRefreshing shouldBe false
            state.nextUrl shouldBe "next"
        }

        "disabled notice is detected from the cached page" {
            val state =
                CommentResponse(comments = listOf(Comment(id = 2L, comment = "コメントがオフにされています"))).toCommentState()

            state.hasDisabledNotice shouldBe true
        }

        "an empty cached page is reported as empty instead of loading" {
            val state = CommentResponse(comments = emptyList(), nextUrl = null).toCommentState()

            state.isEmpty shouldBe true
            state.isLoading shouldBe false
            state.comments shouldBe emptyList<Comment>()
        }
    })
