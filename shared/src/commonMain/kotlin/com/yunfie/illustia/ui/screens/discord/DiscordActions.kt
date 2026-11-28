package com.yunfie.illustia.ui.screens.discord

/** Discord 設定画面が ViewModel に要求する操作。 */
interface DiscordActions {
    fun updateDiscordToken(value: String)

    fun updateDiscordApplicationId(value: String)

    fun updateDiscordRpcEnabled(value: Boolean)

    fun updateDiscordRpcShowArtworkDetails(value: Boolean)

    fun updateDiscordRpcShowButtons(value: Boolean)

    fun updateDiscordRpcShowLogs(value: Boolean)
}
