package com.yunfie.illustia

import com.yunfie.illustia.models.UserPreview
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ViewModelSnapshotsTest :
    StringSpec({
        "restores related creator discovery state with the selected profile" {
            val relatedUser =
                UserPreview(
                    id = 84L,
                    name = "Related Artist",
                    account = "related_artist",
                    profileImageUrl = null,
                    comment = "",
                    isFollowed = false,
                    previewIllusts = emptyList(),
                )
            val source =
                IllustiaUiState(
                    selectedUserId = relatedUser.id,
                    selectedRelatedUsers = listOf(relatedUser),
                    selectedRelatedUsersNextUrl = "https://app-api.pixiv.net/v1/user/related?offset=30",
                    selectedRelatedUsersLoading = true,
                )

            val restored = IllustiaUiState().restore(source.toUserPageSnapshot())

            restored.selectedUserId shouldBe relatedUser.id
            restored.selectedRelatedUsers shouldBe listOf(relatedUser)
            restored.selectedRelatedUsersNextUrl shouldBe source.selectedRelatedUsersNextUrl
            restored.selectedRelatedUsersLoading shouldBe true
        }

        "captures search selected tab in search snapshot" {
            val source =
                IllustiaUiState(
                    activeSearchWord = "miku",
                    searchSelectedTab = 1,
                )

            val snapshot = source.toSearchSnapshot()

            snapshot.activeSearchWord shouldBe "miku"
            snapshot.searchSelectedTab shouldBe 1
        }
    })
