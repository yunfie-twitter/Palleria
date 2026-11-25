package com.yunfie.illustia.ui.screens.network

/**
 * 内部プロキシ設定画面が ViewModel に要求する操作。
 */
interface InternalProxyActions {
    fun updateDohProvider(value: String)

    fun updateDohCustomUrl(value: String)

    fun updateInternalProxyEnabled(value: Boolean)

    fun updateInternalProxyType(value: String)

    fun updateInternalProxyHost(value: String)

    fun updateInternalProxyPort(value: Int)

    fun updateInternalProxyBypassHosts(value: String)
}
