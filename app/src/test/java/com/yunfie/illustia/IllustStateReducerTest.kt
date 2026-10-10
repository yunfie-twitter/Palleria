package com.yunfie.illustia

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.settings.AppSettings
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class IllustStateReducerTest :
    StringSpec({
        "updates every visible copy and adds a newly bookmarked work" {
            val original = reducerIllust(isBookmarked = false)
            val updated = original.copy(title = "Updated", isBookmarked = true)
            val state =
                IllustiaUiState(
                    settings = AppSettings(viewHistory = listOf(original)),
                    homeItems = listOf(original),
                    searchItems = listOf(original),
                    selectedIllust = original,
                )

            val result = state.withUpdatedIllust(updated)

            result.homeItems shouldBe listOf(updated)
            result.searchItems shouldBe listOf(updated)
            result.settings.viewHistory shouldBe listOf(updated)
            result.bookmarkItems shouldBe listOf(updated)
            result.selectedIllust shouldBe updated
        }

        "removes an unbookmarked work from bookmarks" {
            val bookmarked = reducerIllust(isBookmarked = true)
            val updated = bookmarked.copy(isBookmarked = false)

            IllustiaUiState(bookmarkItems = listOf(bookmarked))
                .withUpdatedIllust(updated)
                .bookmarkItems shouldBe emptyList()
        }

        "stages only newly refreshed works without duplicates" {
            val current = listOf(reducerIllust(isBookmarked = false).copy(id = 1L))
            val pending = listOf(reducerIllust(isBookmarked = false).copy(id = 2L))
            val refreshed =
                listOf(
                    reducerIllust(isBookmarked = false).copy(id = 3L),
                    current.single(),
                    pending.single().copy(title = "Latest"),
                )

            newHomeItemsToStage(refreshed, current, pending).map { it.id } shouldBe listOf(3L, 2L)
            newHomeItemsToStage(refreshed, current, pending).last().title shouldBe "Latest"
        }
    })

private fun reducerIllust(isBookmarked: Boolean): Illust =
    Illust(
        id = 42L,
        title = "Original",
        type = "illust",
        caption = "",
        artistId = 7L,
        artistName = "Artist",
        artistAvatarUrl = null,
        squareImageUrl = "square",
        imageUrl = "large",
        originalImageUrl = "original",
        tags = emptyList(),
        pageCount = 1,
        isBookmarked = isBookmarked,
    )
