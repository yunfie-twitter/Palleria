package com.yunfie.illustia.platform

import android.view.KeyEvent

enum class DesktopCommand { Search, Refresh, CloseReader }

internal fun desktopCommand(event: KeyEvent): DesktopCommand? {
    if (event.isAltPressed || event.isMetaPressed) return null
    return when {
        event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_F -> DesktopCommand.Search

        event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_W -> DesktopCommand.CloseReader

        (event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_R) ||
            (!event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_F5) -> DesktopCommand.Refresh

        else -> null
    }
}

internal fun readerPageDirection(
    event: KeyEvent,
    volumeKeysEnabled: Boolean,
): Int {
    if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return 0
    return when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT -> 1
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> -1
        KeyEvent.KEYCODE_SPACE -> if (event.isShiftPressed) -1 else 1
        KeyEvent.KEYCODE_VOLUME_DOWN -> if (volumeKeysEnabled) 1 else 0
        KeyEvent.KEYCODE_VOLUME_UP -> if (volumeKeysEnabled) -1 else 0
        else -> 0
    }
}
