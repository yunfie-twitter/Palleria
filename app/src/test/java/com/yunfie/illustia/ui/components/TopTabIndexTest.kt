package com.yunfie.illustia.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 上部タブのスワイプ移動に関する不変条件。
 *
 * - 左スワイプで右隣、右スワイプで左隣（Pager の自然な並び）。
 * - 端では停止し、循環しない。
 * - タップで範囲外のインデックスが来ても壊れない。
 */
class TopTabIndexTest {
    @Test
    fun `tap index inside range is unchanged`() {
        assertEquals(0, clampTabIndex(0, 4))
        assertEquals(2, clampTabIndex(2, 4))
        assertEquals(3, clampTabIndex(3, 4))
    }

    @Test
    fun `tap index past the edges stops at the edges instead of wrapping`() {
        assertEquals(0, clampTabIndex(-1, 4))
        assertEquals(3, clampTabIndex(4, 4))
        assertEquals(3, clampTabIndex(99, 4))
    }

    @Test
    fun `single tab screen always resolves to zero`() {
        assertEquals(0, clampTabIndex(0, 1))
        assertEquals(0, clampTabIndex(5, 1))
        assertEquals(0, clampTabIndex(-5, 1))
    }

    @Test
    fun `empty page count never produces a negative index`() {
        assertEquals(0, clampTabIndex(0, 0))
        assertEquals(0, clampTabIndex(3, 0))
    }
}
