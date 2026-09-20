package com.yunfie.illustia

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.settings.AppSettings
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class R18FilterTest :
    StringSpec({
        "AppSettings default has allowR18 enabled" {
            AppSettings().allowR18 shouldBe true
        }

        "Illust correctly identifies R-18 content by tag and xRestrict" {
            val normal = createIllust(id = 1L, tags = listOf("original", "cute"))
            val r18Tag = createIllust(id = 2L, tags = listOf("original", "R-18"))
            val r18GTag = createIllust(id = 3L, tags = listOf("original", "R-18G"))
            val r18NoDash = createIllust(id = 4L, tags = listOf("r18"))
            val r18Prefix = createIllust(id = 5L, tags = listOf("R-18BL"))
            val r18Restrict = createIllust(id = 6L, tags = emptyList(), xRestrict = 1)
            val r18GRestrict = createIllust(id = 7L, tags = emptyList(), xRestrict = 2)

            normal.isR18 shouldBe false
            normal.ageRestrictionBadgeText shouldBe null

            r18Tag.isR18 shouldBe true
            r18Tag.ageRestrictionBadgeText shouldBe "R-18"

            r18GTag.isR18 shouldBe true
            r18GTag.isR18G shouldBe true
            r18GTag.ageRestrictionBadgeText shouldBe "R-18G"

            r18NoDash.isR18 shouldBe true
            r18NoDash.ageRestrictionBadgeText shouldBe "R-18"

            r18Prefix.isR18 shouldBe true
            r18Prefix.ageRestrictionBadgeText shouldBe "R-18"

            r18Restrict.isR18 shouldBe true
            r18Restrict.ageRestrictionBadgeText shouldBe "R-18"

            r18GRestrict.isR18 shouldBe true
            r18GRestrict.isR18G shouldBe true
            r18GRestrict.ageRestrictionBadgeText shouldBe "R-18G"
        }

        "NovelPreview correctly identifies R-18 content in title or caption" {
            val normal = createNovel(id = 1L, title = "A wholesome tale", caption = "Enjoyable read")
            val r18Title = createNovel(id = 2L, title = "[R-18] Secret romance", caption = "Story")
            val r18Caption = createNovel(id = 3L, title = "Evening Walk", caption = "R18 content inside")
            val r18GTitle = createNovel(id = 4L, title = "Dark Fantasy [R-18G]", caption = "")

            normal.isR18 shouldBe false
            normal.ageRestrictionBadgeText shouldBe null

            r18Title.isR18 shouldBe true
            r18Title.ageRestrictionBadgeText shouldBe "R-18"

            r18Caption.isR18 shouldBe true
            r18Caption.ageRestrictionBadgeText shouldBe "R-18"

            r18GTitle.isR18 shouldBe true
            r18GTitle.isR18G shouldBe true
            r18GTitle.ageRestrictionBadgeText shouldBe "R-18G"
        }

        "visibleWithSettings filters out R-18 illusts when allowR18 is false" {
            val normal = createIllust(id = 1L, tags = listOf("cute"))
            val r18 = createIllust(id = 2L, tags = listOf("R-18"))
            val list = listOf(normal, r18)

            val allowedSettings = AppSettings(allowR18 = true)
            val blockedSettings = AppSettings(allowR18 = false)

            list.visibleWithSettings(allowedSettings) shouldBe listOf(normal, r18)
            list.visibleWithSettings(blockedSettings) shouldBe listOf(normal)
        }

        "visibleWithMutedTagsVisible filters out R-18 illusts when allowR18 is false" {
            val normal = createIllust(id = 1L, tags = listOf("cute"))
            val r18 = createIllust(id = 2L, tags = listOf("R-18"))
            val list = listOf(normal, r18)

            val allowedSettings = AppSettings(allowR18 = true)
            val blockedSettings = AppSettings(allowR18 = false)

            list.visibleWithMutedTagsVisible(allowedSettings) shouldBe listOf(normal, r18)
            list.visibleWithMutedTagsVisible(blockedSettings) shouldBe listOf(normal)
        }

        "visibleWithSettings filters out R-18 novels when allowR18 is false" {
            val normal = createNovel(id = 1L, title = "Standard Novel", caption = "")
            val r18 = createNovel(id = 2L, title = "R-18 Novel", caption = "")
            val list = listOf(normal, r18)

            val allowedSettings = AppSettings(allowR18 = true)
            val blockedSettings = AppSettings(allowR18 = false)

            list.visibleWithSettings(allowedSettings) shouldBe listOf(normal, r18)
            list.visibleWithSettings(blockedSettings) shouldBe listOf(normal)
        }

        "IllustiaUiState withSettings purges already-loaded R-18 content when allowR18 is toggled off" {
            val normalIllust = createIllust(id = 1L, tags = listOf("cute"))
            val r18Illust = createIllust(id = 2L, tags = listOf("R-18"))
            val normalNovel = createNovel(id = 10L, title = "Normal", caption = "")
            val r18Novel = createNovel(id = 20L, title = "Adult R18", caption = "")

            val initialState =
                IllustiaUiState(
                    settings = AppSettings(allowR18 = true),
                    homeItems = listOf(normalIllust, r18Illust),
                    searchItems = listOf(normalIllust, r18Illust),
                    rankingItems = listOf(normalIllust, r18Illust),
                    rankingModeItems = mapOf("daily" to listOf(normalIllust, r18Illust)),
                    searchNovelItems = listOf(normalNovel, r18Novel),
                    novelItems = listOf(normalNovel, r18Novel),
                )

            val updatedState = initialState.withSettings(AppSettings(allowR18 = false))

            updatedState.homeItems shouldBe listOf(normalIllust)
            updatedState.searchItems shouldBe listOf(normalIllust)
            updatedState.rankingItems shouldBe listOf(normalIllust)
            updatedState.rankingModeItems["daily"] shouldBe listOf(normalIllust)
            updatedState.searchNovelItems shouldBe listOf(normalNovel)
            updatedState.novelItems shouldBe listOf(normalNovel)
        }
    })

private fun createIllust(
    id: Long,
    tags: List<String>,
    xRestrict: Int = 0,
): Illust =
    Illust(
        id = id,
        title = "Illust $id",
        type = "illust",
        caption = "",
        artistId = 100L,
        artistName = "Artist",
        artistAvatarUrl = null,
        squareImageUrl = "https://example.com/square.jpg",
        imageUrl = "https://example.com/large.jpg",
        originalImageUrl = null,
        tags = tags,
        pageCount = 1,
        isBookmarked = false,
        xRestrict = xRestrict,
    )

private fun createNovel(
    id: Long,
    title: String,
    caption: String,
): NovelPreview =
    NovelPreview(
        id = id,
        title = title,
        caption = caption,
        userId = 100L,
        userName = "Author",
        userAccount = "author",
        coverUrl = "https://example.com/novel.jpg",
        pageCount = 1,
        textLength = 1000,
        isBookmarked = false,
        totalBookmarks = 5,
        totalView = 50,
    )
