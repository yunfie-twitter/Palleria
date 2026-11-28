package com.yunfie.illustia.ui.screens.novel

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * NovelTtsTextSanitizer は composeApp/commonMain にあり iOS でも動くため、
 * ここで両プラットフォームの検証対象にする。
 *
 * NovelTtsState / NovelTtsService / NovelTtsNotificationHelper は Android 専用の
 * 実装なので、それらの検証は androidApp 側のテストに残す。
 *
 * `kotlin.test` を使うのは、Kotlin/Native のテストランナーが `@Test` を直接
 * 検出するため。Kotest の FunSpec はネイティブでは自動検出されない。
 */
class NovelTtsPlaybackTest {
    @Test
    fun rubyAndPixivSpecialTagsAreParsedAndCleanedProperlyForTtsReading() {
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

        assertEquals(3, paragraphs.size)
        assertEquals("吾輩はねこである。", paragraphs[0])
        assertEquals("第1章", paragraphs[1])
        assertEquals("名前はまだ無い。", paragraphs[2])
    }

    @Test
    fun sanitizerReplacesCustomDictionaryWordsAndSkipsSymbols() {
        val text = "★主人公の月姫は……何者かに追われていた――！"
        val dict = mapOf("月姫" to "かぐや")

        val sanitizedWithSkip =
            NovelTtsTextSanitizer.sanitizeParagraph(
                paragraphText = text,
                skipSymbols = true,
                customDictionary = dict,
            )
        assertEquals("主人公のかぐやは、何者かに追われていた、！", sanitizedWithSkip)

        val sanitizedWithoutSkip =
            NovelTtsTextSanitizer.sanitizeParagraph(
                paragraphText = text,
                skipSymbols = false,
                customDictionary = dict,
            )
        assertEquals("★主人公のかぐやは……何者かに追われていた――！", sanitizedWithoutSkip)
    }
}
