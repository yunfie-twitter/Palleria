package com.yunfie.illustia.ui.screens

/** App Lock 設定画面が ViewModel に要求する操作。 */
interface AppLockActions {
    suspend fun verifyPin(pin: String): Boolean

    fun resetLockFailCount()

    fun recordLockFailure()

    fun disableAppLock()

    fun updateBiometricEnabled(enabled: Boolean)

    fun updateAppLockTiming(value: String)

    fun openAppLockPinEntry()
}
