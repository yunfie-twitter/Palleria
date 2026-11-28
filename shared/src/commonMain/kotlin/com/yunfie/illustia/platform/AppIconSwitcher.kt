package com.yunfie.illustia.platform

/**
 * ViewModel 層が必要とするアプリアイコン切り替えの境界。
 *
 * プライバシーモード時にダミーアイコンのランチャーエイリアスへ切り替える。
 * 実装は `PackageManager` を扱う Android 側に閉じる。
 */
interface AppIconSwitcher {
    /**
     * アイコンの表示状態を反映する。
     *
     * @param privacyModeEnabled プライバシーモードが有効か。
     * @param appIconVariant 選択中のアイコーンバリアント。
     */
    fun apply(
        privacyModeEnabled: Boolean,
        appIconVariant: String = "default",
    )
}
