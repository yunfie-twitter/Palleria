package com.yunfie.illustia.platform

/**
 * ViewModel 層が必要とするテレメトリ有効/無効の切り替え境界。
 *
 * 設定画面のトグルから呼ばれる。実装はクラッシュレポート送信基盤を扱う Android 側に閉じる。
 */
interface TelemetryControl {
    /** テレメトリ送信を有効または無効にする。 */
    fun setEnabled(enabled: Boolean)
}
