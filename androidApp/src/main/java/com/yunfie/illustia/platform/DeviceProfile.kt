package com.yunfie.illustia.platform

/**
 * ViewModel 層が必要とする端末性能プロファイル。
 *
 * 従来 ViewModel は `PlatformCapabilities.xxx(getApplication())` を直接呼んで
 * いたため `Context` が必要だった。判定結果だけを保持する値オブジェクトへ
 * 変換し、ViewModel から Android の `Context` を完全に追い出す。
 */
interface DeviceProfile {
    /** 低 RAM・低性能端末か。 */
    val isLowRamDevice: Boolean

    /** 設定永続化のデバウンス間隔（ミリ秒）。 */
    val dataStoreDebounceMs: Long
}
