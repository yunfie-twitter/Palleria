package com.yunfie.illustia.platform

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DesktopShortcutsTest {
    private fun key(
        code: Int,
        modifiers: Int = 0,
    ) = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, modifiers)

    @Test
    fun globalShortcutsRequireTheirModifiers() {
        assertNull(desktopCommand(key(KeyEvent.KEYCODE_F)))
        assertEquals(DesktopCommand.Search, desktopCommand(key(KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON)))
        assertEquals(DesktopCommand.Refresh, desktopCommand(key(KeyEvent.KEYCODE_R, KeyEvent.META_CTRL_ON)))
        assertEquals(DesktopCommand.Refresh, desktopCommand(key(KeyEvent.KEYCODE_F5)))
        assertEquals(DesktopCommand.CloseReader, desktopCommand(key(KeyEvent.KEYCODE_W, KeyEvent.META_CTRL_ON)))
        assertNull(desktopCommand(key(KeyEvent.KEYCODE_R, KeyEvent.META_ALT_ON or KeyEvent.META_CTRL_ON)))
    }

    @Test
    fun desktopPagingDoesNotDependOnVolumeKeyPreference() {
        assertEquals(1, readerPageDirection(key(KeyEvent.KEYCODE_DPAD_RIGHT), false))
        assertEquals(-1, readerPageDirection(key(KeyEvent.KEYCODE_DPAD_LEFT), false))
        assertEquals(1, readerPageDirection(key(KeyEvent.KEYCODE_SPACE), false))
        assertEquals(-1, readerPageDirection(key(KeyEvent.KEYCODE_SPACE, KeyEvent.META_SHIFT_ON), false))
        assertEquals(0, readerPageDirection(key(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON), true))
    }

    @Test
    fun disabledVolumeKeysRemainAvailableForAudio() {
        assertEquals(0, readerPageDirection(key(KeyEvent.KEYCODE_VOLUME_DOWN), false))
        assertEquals(0, readerPageDirection(key(KeyEvent.KEYCODE_VOLUME_UP), false))
        assertEquals(1, readerPageDirection(key(KeyEvent.KEYCODE_VOLUME_DOWN), true))
        assertEquals(-1, readerPageDirection(key(KeyEvent.KEYCODE_VOLUME_UP), true))
    }
}
