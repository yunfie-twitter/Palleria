package com.yunfie.illustia.ui.screens.account

/**
 * ログイン関連画面が ViewModel に要求する操作のうち、共有 UI から使える最小契約。
 */
interface LoginActions : AccountActions {
    override fun login()

    override fun updateRefreshToken(value: String)
}
