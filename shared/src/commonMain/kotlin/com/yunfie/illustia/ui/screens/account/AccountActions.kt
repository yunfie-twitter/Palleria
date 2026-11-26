package com.yunfie.illustia.ui.screens.account

/**
 * アカウント系画面が ViewModel に要求する操作のうち、共有 UI から使える最小契約。
 */
interface AccountActions {
    fun openAccountLoginMethod()

    fun openWebLogin()

    fun updateSendTelemetry(value: Boolean)

    fun updateRefreshToken(value: String)

    fun login()
}
