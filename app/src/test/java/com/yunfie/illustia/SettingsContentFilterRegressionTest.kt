package com.yunfie.illustia

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.settings.AppSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * [IllustiaUiState.withSettings] におけるコンテンツフィルタリングの回帰テスト。
 * 遷移（Transition）の有無にかかわらず、ターゲット設定（allowR18 == false / hideAiWorks == true）
 * に基づいて確実に制限対象コンテンツが除外されること、および許可時は誤削除されないこと、
 * 同じ設定を繰り返し適用しても安全かつ効率的であることを検証する。
 */
class SettingsContentFilterRegressionTest :
    FunSpec({
        // isR18 は xRestrict > 0 で判定される
        // isAi は illustAiType == 2 で判定される
        val normalIllust =
            Illust(
                id = 1L,
                title = "Normal",
                type = "illust",
                caption = "",
                artistId = 0L,
                artistName = "",
                artistAvatarUrl = null,
                squareImageUrl = "",
                imageUrl = "",
                originalImageUrl = null,
                tags = emptyList(),
                pageCount = 1,
                isBookmarked = false,
                xRestrict = 0,
                illustAiType = 0,
            )
        val r18Illust =
            Illust(
                id = 2L,
                title = "R18",
                type = "illust",
                caption = "",
                artistId = 0L,
                artistName = "",
                artistAvatarUrl = null,
                squareImageUrl = "",
                imageUrl = "",
                originalImageUrl = null,
                tags = emptyList(),
                pageCount = 1,
                isBookmarked = false,
                xRestrict = 1,
                illustAiType = 0,
            )
        val aiIllust =
            Illust(
                id = 3L,
                title = "AI",
                type = "illust",
                caption = "",
                artistId = 0L,
                artistName = "",
                artistAvatarUrl = null,
                squareImageUrl = "",
                imageUrl = "",
                originalImageUrl = null,
                tags = emptyList(),
                pageCount = 1,
                isBookmarked = false,
                xRestrict = 0,
                illustAiType = 2,
            )
        val r18AiIllust =
            Illust(
                id = 4L,
                title = "R18 AI",
                type = "illust",
                caption = "",
                artistId = 0L,
                artistName = "",
                artistAvatarUrl = null,
                squareImageUrl = "",
                imageUrl = "",
                originalImageUrl = null,
                tags = emptyList(),
                pageCount = 1,
                isBookmarked = false,
                xRestrict = 1,
                illustAiType = 2,
            )

        // isR18 は title/caption に "R-18" を含む場合に判定される
        val normalNovel =
            NovelPreview(
                id = 10L,
                title = "Normal Novel",
                caption = "",
                userId = 0L,
                userName = "",
                userAccount = "",
                coverUrl = "",
                pageCount = 1,
                textLength = 0,
                isBookmarked = false,
                totalBookmarks = 0,
                totalView = 0,
            )
        val r18Novel =
            NovelPreview(
                id = 20L,
                title = "R-18 Novel",
                caption = "",
                userId = 0L,
                userName = "",
                userAccount = "",
                coverUrl = "",
                pageCount = 1,
                textLength = 0,
                isBookmarked = false,
                totalBookmarks = 0,
                totalView = 0,
            )

        test("1. R18 content is purged when allowR18 == false regardless of prior state") {
            // ケース1A: allowR18が元からfalseの状態で、状態内にR18コンテンツが混入・復元された場合
            val initialStateAlreadyRestricted =
                IllustiaUiState(
                    settings = AppSettings(allowR18 = false, hideAiWorks = false),
                    homeItems = listOf(normalIllust, r18Illust),
                    novelItems = listOf(normalNovel, r18Novel),
                )
            val filteredA =
                initialStateAlreadyRestricted.withSettings(AppSettings(allowR18 = false, hideAiWorks = false))
            filteredA.homeItems.shouldContainExactly(normalIllust)
            filteredA.novelItems.shouldContainExactly(normalNovel)

            // ケース1B: allowR18 == true から allowR18 == false への遷移時
            val initialStateAllowed =
                IllustiaUiState(
                    settings = AppSettings(allowR18 = true, hideAiWorks = false),
                    homeItems = listOf(normalIllust, r18Illust),
                    novelItems = listOf(normalNovel, r18Novel),
                )
            val filteredB =
                initialStateAllowed.withSettings(AppSettings(allowR18 = false, hideAiWorks = false))
            filteredB.homeItems.shouldContainExactly(normalIllust)
            filteredB.novelItems.shouldContainExactly(normalNovel)
        }

        test("2. AI content is purged when hideAiWorks == true regardless of prior state") {
            // ケース2A: hideAiWorksが元からtrueの状態で、状態内にAIコンテンツが混入・復元された場合
            val initialStateAlreadyHidingAi =
                IllustiaUiState(
                    settings = AppSettings(allowR18 = true, hideAiWorks = true),
                    homeItems = listOf(normalIllust, aiIllust, r18Illust, r18AiIllust),
                )
            val filteredA =
                initialStateAlreadyHidingAi.withSettings(AppSettings(allowR18 = true, hideAiWorks = true))
            filteredA.homeItems.shouldContainExactly(normalIllust, r18Illust)

            // ケース2B: hideAiWorks == false から hideAiWorks == true への遷移時
            val initialStateShowingAi =
                IllustiaUiState(
                    settings = AppSettings(allowR18 = true, hideAiWorks = false),
                    homeItems = listOf(normalIllust, aiIllust, r18Illust, r18AiIllust),
                )
            val filteredB =
                initialStateShowingAi.withSettings(AppSettings(allowR18 = true, hideAiWorks = true))
            filteredB.homeItems.shouldContainExactly(normalIllust, r18Illust)
        }

        test("3. Content is never erroneously dropped when settings allow them") {
            // allowR18 == true かつ hideAiWorks == false の場合、すべての作品が維持される
            val allIllusts = listOf(normalIllust, r18Illust, aiIllust, r18AiIllust)
            val allNovels = listOf(normalNovel, r18Novel)
            val state =
                IllustiaUiState(
                    settings = AppSettings(allowR18 = true, hideAiWorks = false),
                    homeItems = allIllusts,
                    searchItems = allIllusts,
                    timelineItems = allIllusts,
                    shortsFeedItems = allIllusts,
                    novelItems = allNovels,
                    searchNovelItems = allNovels,
                )

            val updated = state.withSettings(AppSettings(allowR18 = true, hideAiWorks = false))

            updated.homeItems.shouldContainExactly(allIllusts)
            updated.searchItems.shouldContainExactly(allIllusts)
            updated.timelineItems.shouldContainExactly(allIllusts)
            updated.shortsFeedItems.shouldContainExactly(allIllusts)
            updated.novelItems.shouldContainExactly(allNovels)
            updated.searchNovelItems.shouldContainExactly(allNovels)
        }

        test("4. Applying the same settings repeatedly is idempotent and avoids redundant re-allocation") {
            val initialList = listOf(normalIllust)
            val initialNovelList = listOf(normalNovel)
            val settings = AppSettings(allowR18 = false, hideAiWorks = true)

            val state =
                IllustiaUiState(
                    settings = settings,
                    homeItems = initialList,
                    novelItems = initialNovelList,
                )

            // 1回目の適用（既にサニタイズ済み）
            val firstApply = state.withSettings(settings)
            firstApply.homeItems.shouldContainExactly(normalIllust)
            firstApply.novelItems.shouldContainExactly(normalNovel)
            // サニタイズ済みで変更不要なため、同一参照が維持される
            (firstApply.homeItems === initialList) shouldBe true
            (firstApply.novelItems === initialNovelList) shouldBe true

            // 2回目の連続適用
            val secondApply = firstApply.withSettings(settings)
            secondApply.homeItems.shouldContainExactly(normalIllust)
            secondApply.novelItems.shouldContainExactly(normalNovel)
            (secondApply.homeItems === firstApply.homeItems) shouldBe true
            (secondApply.novelItems === firstApply.novelItems) shouldBe true
        }

        test("related-content controls remove loaded adult works when global filters allow them") {
            val settings =
                AppSettings(
                    allowR18 = true,
                    allowR18G = true,
                    hideAiWorks = false,
                    showR18Badge = true,
                    showRelatedR18 = true,
                )
            val allIllusts = listOf(normalIllust, r18Illust, r18Illust.copy(id = 5L, xRestrict = 2))
            val state =
                IllustiaUiState(
                    settings = settings,
                    homeItems = allIllusts,
                    relatedIllusts = allIllusts,
                )

            listOf(
                settings.copy(showRelatedR18 = false),
                settings.copy(showR18Badge = false),
            ).forEach { restrictedSettings ->
                val updated = state.withSettings(restrictedSettings)
                updated.relatedIllusts.shouldContainExactly(normalIllust)
                (updated.homeItems === allIllusts) shouldBe true
            }

            (state.withSettings(settings).relatedIllusts === allIllusts) shouldBe true
        }
    })
