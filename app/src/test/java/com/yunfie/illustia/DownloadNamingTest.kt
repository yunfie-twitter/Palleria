package com.yunfie.illustia

import com.yunfie.illustia.models.Illust
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DownloadNamingTest :
    StringSpec({
        "builds a safe path from artist, work, and file names" {
            buildDownloadPath(
                filename = "illustia_42?.jpg",
                illust = illust(artistName = "Artist/Name", title = "Work:*"),
                groupByArtist = true,
                groupByWork = true,
            ) shouldBe "Artist_Name/Work___42/illustia_42_.jpg"
        }

        "uses stable ids when optional folder labels are blank" {
            buildDownloadPath(
                filename = "",
                illust = illust(artistName = "  ", title = "..."),
                groupByArtist = true,
                groupByWork = true,
            ) shouldBe "artist_7/work_42/untitled"
        }

        "adds an image extension without replacing an existing one" {
            "cover".withImageExtension("https://example.test/image", "image/jpeg; charset=binary") shouldBe
                "cover.jpg"
            "cover.png".withImageExtension("https://example.test/image.jpg", "image/jpeg") shouldBe
                "cover.png"
            "ugoira_12345".withImageExtension("https://example.test/ugoira.gif", "image/gif") shouldBe
                "ugoira_12345.gif"
            "photo".withImageExtension("https://example.test/photo.webp?param=123#anchor", null) shouldBe
                "photo.webp"
        }

        "extracts only an illustia-prefixed id" {
            extractIllustId("saved_illustia_123_p0") shouldBe 123L
            extractIllustId("saved_123_p0") shouldBe null
        }

        "recognizes urls from any image quality or page" {
            val illust = illust().copy(originalImagePages = listOf("page-original"))

            illust.hasImageUrl("page-original") shouldBe true
            illust.hasImageUrl("unrelated") shouldBe false
        }
    })

private fun illust(
    artistName: String = "Artist",
    title: String = "Work",
): Illust =
    Illust(
        id = 42L,
        title = title,
        type = "illust",
        caption = "",
        artistId = 7L,
        artistName = artistName,
        artistAvatarUrl = null,
        squareImageUrl = "square",
        imageUrl = "large",
        originalImageUrl = "original",
        tags = emptyList(),
        pageCount = 1,
        isBookmarked = false,
    )
