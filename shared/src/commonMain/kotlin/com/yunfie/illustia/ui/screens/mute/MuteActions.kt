package com.yunfie.illustia.ui.screens.mute

/**
 * ミュート設定画面が ViewModel に要求する操作。
 */
interface MuteActions {
    fun muteTag(tag: String)

    fun unmuteIllust(id: Long)

    fun unmuteTag(tag: String)

    fun unmuteUser(id: Long)
}
