package com.yunfie.illustia.platform

/**
 * ViewModel 層が必要とするネットワーク状態の境界。
 *
 * `ConnectivityManager` / `NetworkCapabilities` の直接利用をここに閉じ込める。
 */
interface Connectivity {
    /** 現在アクティブなネットワークが Wi-Fi かどうか。 */
    fun isWifiConnected(): Boolean

    /** アクティブなネットワークが無く、明示的にオフラインかどうか。 */
    fun isOffline(): Boolean

    /** 従量制（モバイル回線など）のネットワークに接続しているかどうか。 */
    fun isMetered(): Boolean
}
