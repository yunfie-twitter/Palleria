package com.yunfie.illustia.nativebridge

import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.UserProfile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class AssistantContentProviderTest :
    FunSpec({
        test("describes the selected artwork as a schema.org ImageObject") {
            val illust =
                sampleIllust(id = 123L, title = "朝の海 \"quoted\"", artist = "Artist", tags = listOf("landscape", "blue"))

            val snapshot = AssistantContentProvider.resolve(IllustiaUiState(selectedIllust = illust)).shouldNotBeNull()

            snapshot.webUri shouldBe "https://www.pixiv.net/artworks/123"
            snapshot.structuredData.shouldNotBeNull()
            snapshot.structuredData shouldContain "\"@type\":\"ImageObject\""
            snapshot.structuredData shouldContain "https://www.pixiv.net/artworks/123"
            snapshot.structuredData shouldContain "Artist"
            snapshot.structuredData shouldContain "landscape,blue"
        }

        test("escapes quotes so the JSON-LD stays valid") {
            val illust = sampleIllust(id = 1L, title = "a\"b", artist = "c")

            val json = AssistantContentProvider.resolve(IllustiaUiState(selectedIllust = illust)).shouldNotBeNull().structuredData

            json.shouldNotBeNull()
            json shouldContain "a\\\"b"
        }

        test("describes the selected user as a schema.org Person") {
            val user =
                UserProfile(
                    id = 42L,
                    name = "Pixiv User",
                    account = "account42",
                    profileImageUrl = null,
                    backgroundImageUrl = null,
                    comment = "hello",
                    isFollowed = false,
                )

            val snapshot =
                AssistantContentProvider
                    .resolve(IllustiaUiState(showUserPage = true, selectedUser = user))
                    .shouldNotBeNull()

            snapshot.webUri shouldBe "https://www.pixiv.net/users/42"
            snapshot.structuredData.shouldNotBeNull()
            snapshot.structuredData shouldContain "\"@type\":\"Person\""
            snapshot.structuredData shouldContain "account42"
        }

        test("falls back to the home page without structured data") {
            val snapshot = AssistantContentProvider.resolve(IllustiaUiState()).shouldNotBeNull()

            snapshot.webUri shouldBe "https://www.pixiv.net/"
            snapshot.structuredData.shouldBeNull()
        }

        test("exposes nothing while the app is locked") {
            AssistantContentProvider
                .resolve(IllustiaUiState(selectedIllust = sampleIllust(1L, "t", "a"), appLocked = true))
                .shouldBeNull()
            AssistantContentProvider
                .resolve(IllustiaUiState(selectedIllust = sampleIllust(1L, "t", "a"), privacyLocked = true))
                .shouldBeNull()
        }
    })

private fun sampleIllust(
    id: Long,
    title: String,
    artist: String,
    tags: List<String> = emptyList(),
): Illust =
    Illust(
        id = id,
        title = title,
        type = "illust",
        caption = "",
        artistId = 1L,
        artistName = artist,
        artistAvatarUrl = null,
        squareImageUrl = "",
        imageUrl = "",
        originalImageUrl = null,
        tags = tags,
        pageCount = 1,
        isBookmarked = false,
    )
