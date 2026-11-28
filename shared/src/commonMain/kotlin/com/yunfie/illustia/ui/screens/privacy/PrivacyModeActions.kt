package com.yunfie.illustia.ui.screens.privacy

/**
 * プライバシーモード設定画面が ViewModel に要求する操作。
 * `enablePrivacyModeWithCode` / `verifyCurrentUnlockCode` / `changeUnlockCode` は
 * ハッシュ照合を伴うため suspend。
 */
interface PrivacyModeActions {
    fun disablePrivacyMode()

    suspend fun enablePrivacyMode()

    fun hasUnlockCodeSet(): Boolean

    fun isValidUnlockCode(code: String): Boolean

    suspend fun enablePrivacyModeWithCode(code: String): Boolean

    suspend fun verifyCurrentUnlockCode(code: String): Boolean

    suspend fun changeUnlockCode(
        currentCode: String,
        newCode: String,
    ): Boolean

    fun updatePrivacyModeAutoLockTiming(value: String)

    fun updateHideRecents(value: Boolean)

    fun updateHideNotifications(value: Boolean)

    fun updateDummyIconVariant(value: String)

    fun updateDummyAppName(value: String)
}
