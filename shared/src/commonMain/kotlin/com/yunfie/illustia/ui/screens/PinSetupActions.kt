package com.yunfie.illustia.ui.screens

/** PIN 設定画面が ViewModel に要求する操作。 */
interface PinSetupActions {
    suspend fun verifyPin(pin: String): Boolean

    suspend fun setupPin(pin: String)

    suspend fun changePin(newPin: String)
}
