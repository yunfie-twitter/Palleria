package com.yunfie.illustia.platform

/**
 * ViewModel 層が必要とするアプリ自身のメタ情報境界。
 *
 * `PackageManager` / `PackageInfoCompat` の直接利用をここに閉じ込める。
 */
interface AppInfo {
    /** アプリのパッケージ名。 */
    fun packageName(): String

    /** 現在の versionCode。取得できなければ null。 */
    fun versionCode(): Int?
}
