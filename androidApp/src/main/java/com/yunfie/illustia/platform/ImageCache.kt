package com.yunfie.illustia.platform

/**
 * ViewModel 層が必要とする画像キャッシュ操作の境界。
 *
 * Coil (`SingletonImageLoader`) への直接依存をここに閉じ込める。
 */
interface ImageCache {
    /** 指定 URL の画像を先読みキューに積む。 */
    fun prefetch(url: String)

    /** ディスク・メモリ両方の画像キャッシュを破棄する。 */
    fun clear()
}
