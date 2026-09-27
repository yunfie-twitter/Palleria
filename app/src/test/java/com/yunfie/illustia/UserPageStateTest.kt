package com.yunfie.illustia

import com.yunfie.illustia.models.UserPreview
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class UserPageStateTest :
    StringSpec({
        "closing a discovered profile preserves the related artists list and pagination" {
            val artist =
                UserPreview(
                    id = 84L,
                    name = "Related Artist",
                    account = "related_artist",
                    profileImageUrl = null,
                    comment = "",
                    isFollowed = false,
                    previewIllusts = emptyList(),
                )
            val state =
                IllustiaUiState(
                    selectedUserId = artist.id,
                    selectedUserNextUrl = "profile-next-page",
                    selectedUserBookmarksNextUrl = "bookmarks-next-page",
                    userPageDismissed = true,
                    selectedRelatedUsers = listOf(artist),
                    selectedRelatedUsersUserId = 42L,
                    selectedRelatedUsersNextUrl = "related-next-page",
                )

            val cleared = state.clearClosedUserPage()

            cleared.selectedUserId shouldBe null
            cleared.selectedUserNextUrl shouldBe null
            cleared.selectedUserBookmarksNextUrl shouldBe null
            cleared.userPageDismissed shouldBe false
            cleared.selectedRelatedUsers shouldBe listOf(artist)
            cleared.selectedRelatedUsersUserId shouldBe 42L
            cleared.selectedRelatedUsersNextUrl shouldBe "related-next-page"
            cleared.selectedRelatedUsersLoading shouldBe false
        }

        "profile cleanup does not reset an in-flight related artists request" {
            val state =
                IllustiaUiState(
                    selectedUserId = 84L,
                    selectedRelatedUsersUserId = 42L,
                    selectedRelatedUsersLoading = true,
                )

            val cleared = state.clearClosedUserPage()

            cleared.selectedUserId shouldBe null
            cleared.selectedRelatedUsersUserId shouldBe 42L
            cleared.selectedRelatedUsersLoading shouldBe true
        }
    })
