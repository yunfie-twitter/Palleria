package com.yunfie.illustia.platform

import java.io.File

/**
 * ViewModel 層が必要とするアプリ固有のパス境界。
 *
 * `Context.cacheDir` の直接利用をここに閉じ込める。
 */
interface AppPaths {
    /** アプリのキャッシュディレクトリ。 */
    fun cacheDir(): File
}
