package com.yunfie.illustia.platform

/**
 * ViewModel 層が必要とするアプリ固有のパス境界。
 *
 * `Context.cacheDir` の直接利用をここに閉じ込める。`File` を境界に出さないため
 * 実体はパス文字列で返し、`File` 化は呼び出し側（Android 層）で行う。
 */
interface AppPaths {
    /** アプリのキャッシュディレクトリの絶対パス。 */
    fun cacheDir(): String
}
