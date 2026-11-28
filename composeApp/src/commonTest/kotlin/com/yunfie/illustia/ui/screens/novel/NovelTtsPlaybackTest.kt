package com.yunfie.illustia.ui.screens.novel

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * NovelTtsTextSanitizer は composeApp/commonMain にあり iOS でも動くため、
 * ここで両プラットフォームの検証対象にする。
 *
 * NovelTtsState / NovelTtsService / NovelTtsNotificationHelper は Android 専用の
 * 実装なので、それらの検証は androidApp 側のテストに残す。
 */
class NovelTtsPlaybackTest :
    FunSpec({
        test("Ruby and pixiv special tags are parsed and cleaned properly for TTS reading") {
            val rubyRegex = Regex("""\[\[rb:[^>]*>(.*?)\]\]""")
            val jumpRegex = Regex("""\[jump:\d+\]""")
            val newpageRegex = Regex("""\[newpage\]""")
            val pixivImageRegex = Regex("""\[pixivimage:\d+\]""")
            val chapterRegex = Regex("""\[chapter:(.*?)\]""")

            val raw = "吾輩は[[rb:猫>ねこ]]である。[jump:1]\n[chapter:第1章]\n[newpage]\n名前はまだ無い。[pixivimage:98765]"
            val cleaned =
                raw
                    .replace(rubyRegex, "$1")
                    .replace(jumpRegex, "")
                    .replace(newpageRegex, "\n\n")
                    .replace(pixivImageRegex, "")
                    .replace(chapterRegex, "$1")

            val paragraphs = cleaned.split("\n").map { it.trim() }.filter { it.isNotBlank() }

            paragraphs.size shouldBe 3
            paragraphs[0] shouldBe "吾輩はねこである。"
            paragraphs[1] shouldBe "第1章"
            paragraphs[2] shouldBe "名前はまだ無い。"
        }

        test("NovelTtsTextSanitizer replaces custom dictionary words and skips symbols") {
            val text = "★主人公の月姫は……何者かに追われていた――！"
            val dict = mapOf("月姫" to "かぐや")

            val sanitizedWithSkip =
                NovelTtsTextSanitizer.sanitizeParagraph(
                    paragraphText = text,
                    skipSymbols = true,
                    customDictionary = dict,
                )
            sanitizedWithSkip shouldBe "主人公のかぐやは、何者かに追われていた、！"

            val sanitizedWithoutSkip =
                NovelTtsTextSanitizer.sanitizeParagraph(
                    paragraphText = text,
                    skipSymbols = false,
                    customDictionary = dict,
                )
            sanitizedWithoutSkip shouldBe "★主人公のかぐやは……何者かに追われていた――！"
        }
    })
