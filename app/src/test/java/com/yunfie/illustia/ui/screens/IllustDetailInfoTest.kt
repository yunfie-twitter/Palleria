package com.yunfie.illustia.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IllustDetailInfoTest {
    @Test
    fun formatsOnlyValidIllustrationDimensions() {
        assertEquals("720 × 1080", formatIllustDimensions(720, 1080))
        assertNull(formatIllustDimensions(0, 1080))
        assertNull(formatIllustDimensions(720, 0))
    }

    @Test
    fun hidesRelatedSectionOnlyWhenLoadingFinishedWithNoResults() {
        // 取得中はスピナーを出すのでセクションを表示する
        assertTrue(shouldShowRelatedSection(emptyList(), relatedIllustsLoaded = false))
        // 取得完了かつ 0 件なら、見出しごと隠す（スピナーが残らない）
        assertFalse(shouldShowRelatedSection(emptyList(), relatedIllustsLoaded = true))
    }
}
