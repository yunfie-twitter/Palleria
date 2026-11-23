package com.yunfie.illustia.platform

/**
 * ViewModel 層が必要とする端末性能プロファイル。
 *
 * 判定結果だけを保持する値オブジェクトにして、ViewModel から
 * プラットフォームの端末情報 API を完全に追い出す。
 */
interface DeviceProfile {
    /** 低 RAM・低性能端末か。 */
    val isLowRamDevice: Boolean

    /** 設定永続化のデバウンス間隔（ミリ秒）。 */
    val dataStoreDebounceMs: Long
}
