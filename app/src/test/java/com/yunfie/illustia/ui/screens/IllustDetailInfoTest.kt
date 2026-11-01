package com.yunfie.illustia.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IllustDetailInfoTest {
    @Test
    fun formatsOnlyValidIllustrationDimensions() {
        assertEquals("720 × 1080", formatIllustDimensions(720, 1080))
        assertNull(formatIllustDimensions(0, 1080))
        assertNull(formatIllustDimensions(720, 0))
    }
}
