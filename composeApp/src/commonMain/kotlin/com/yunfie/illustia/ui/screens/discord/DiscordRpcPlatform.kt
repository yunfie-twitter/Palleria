package com.yunfie.illustia.ui.screens.discord

import kotlinx.coroutines.flow.StateFlow

/** Discord Rich Presence の設定画面が必要とする Android 側の問い合わせ口。 */
interface DiscordRpcPlatform {
    val defaultApplicationId: String

    val diagnostics: StateFlow<List<String>>

    fun isSupported(): Boolean

    fun clearDiagnostics()
}
