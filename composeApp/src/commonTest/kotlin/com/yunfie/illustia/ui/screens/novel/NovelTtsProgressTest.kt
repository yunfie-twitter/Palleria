package com.yunfie.illustia.ui.screens.novel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/** Kotlin/Native のテストランナーは `@Test` を直接検出するため `kotlin.test` を使う。 */
class NovelTtsProgressTest {
    @Test
    fun pauseResumesAtTheCurrentRangeInsteadOfTheParagraphBeginning() {
        val progress = NovelTtsProgress()
        val text = "最初の文。続きの文。最後の文。"
        val (id, spoken) = progress.begin(text)
        assertEquals(text, spoken)
        progress.onRangeStart(id, 5, 10)
        progress.pause()
        assertEquals("続きの文。最後の文。", progress.begin(text).second)
    }

    @Test
    fun repeatedPausesAccumulateOffsetsRelativeToEachResumedUtterance() {
        val progress = NovelTtsProgress()
        val text = "最初の文。続きの文。最後の文。"
        val first = progress.begin(text).first
        progress.onRangeStart(first, 5, 10)
        progress.pause()
        val second = progress.begin(text).first
        progress.onRangeStart(second, 5, 10)
        progress.pause()
        assertEquals("最後の文。", progress.begin(text).second)
    }

    @Test
    fun lateCallbacksFromAPausedUtteranceCannotOverwriteResumedProgress() {
        val progress = NovelTtsProgress()
        val text = "abcdefghij"
        val oldId = progress.begin(text).first
        progress.onRangeStart(oldId, 3, 5)
        progress.pause()
        assertFalse(progress.isActive(oldId))
        progress.onRangeStart(oldId, 8, 10)
        val newId = progress.begin(text).first
        assertNotEquals(oldId, newId)
        progress.onRangeStart(oldId, 8, 10)
        progress.onRangeStart(newId, 2, 4)
        progress.pause()
        assertEquals("fghij", progress.begin(text).second)
    }

    @Test
    fun paragraphNavigationAndNewNovelsResetOffsetsAndInvalidateOldUtterances() {
        val progress = NovelTtsProgress()
        val oldId = progress.begin("abcdefghij").first
        progress.onRangeStart(oldId, 5, 8)
        progress.reset()
        val (newId, spoken) = progress.begin("次の段落")
        assertEquals("次の段落", spoken)
        assertNotEquals(oldId, newId)
        assertFalse(progress.isActive(oldId))
        assertFalse(progress.isActive(null))
    }

    @Test
    fun invalidOrBackwardsRangeCallbacksDoNotCorruptTheResumeOffset() {
        val progress = NovelTtsProgress()
        val id = progress.begin("abcdefghij").first
        progress.onRangeStart(id, 4, 6)
        progress.onRangeStart(id, -1, 2)
        progress.onRangeStart(id, 8, 20)
        progress.onRangeStart(id, 6, 6)
        progress.onRangeStart(id, 0, 2)
        progress.pause()
        assertEquals("efghij", progress.begin("abcdefghij").second)
    }

    @Test
    fun offsetsReferToDictionaryExpandedTextAndMissingRangesNeverSkipText() {
        val progress = NovelTtsProgress()
        val text = NovelTtsTextSanitizer.sanitizeParagraph("月姫は歩いた。", true, mapOf("月姫" to "かぐや"))
        val id = progress.begin(text).first
        progress.onRangeStart(id, 4, text.length)
        progress.pause()
        assertEquals("歩いた。", progress.begin(text).second)
        progress.reset()
        progress.begin(text)
        progress.pause()
        assertEquals(text, progress.begin(text).second)
    }
}
