package com.yunfie.illustia.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollSpeedTrackerTest {
    @Test fun distinguishesSlowScrollFlingAndDirection() {
        val speed = ScrollSpeedTracker()
        assertFalse(speed.isFast(4f, 100))
        assertTrue(speed.isFast(40f, 116))
        assertTrue(speed.isFast(-40f, 132))
        assertFalse(speed.isFast(4f, 148))
        assertFalse(speed.isFast(40f, 1000))
    }

    @Test fun ignoresHorizontalScrollAndSmallSameFrameUpdates() {
        val speed = ScrollSpeedTracker()
        assertFalse(speed.isFast(4f, 100))
        assertFalse(speed.isFast(0f, 115))
        assertFalse(speed.isFast(4f, 116))
        assertFalse(speed.isFast(1f, 116))
    }
}
