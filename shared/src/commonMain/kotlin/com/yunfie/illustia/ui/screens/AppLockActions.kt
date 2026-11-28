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

/** App Lock 解除画面がアプリ本体に要求する操作。 */
interface AppLockUnlockActions {
    suspend fun verifyPin(pin: String): Boolean

    fun resetLockFailCount()

    fun recordLockFailure()

    fun confirmUnlock()

    fun openRecoveryWebLogin()

    fun resetAppFromLock()

    /** 生体認証の可否。生体認証を扱えない環境では false。 */
    fun isBiometricAvailable(): Boolean = false

    /** 生体認証プロンプトを出す。未対応環境では何もしない。 */
    fun requestBiometricAuth(onSuccess: () -> Unit) {}

    /** 単調増加ミリ秒。クールダウン残り時間の算出に使う。 */
    fun elapsedRealtimeMillis(): Long
}
