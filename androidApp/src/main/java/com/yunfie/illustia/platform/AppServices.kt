package com.yunfie.illustia.platform

/**
 * ViewModel 層が必要とするアプリ内の連携処理の境界。
 *
 * ランチャーアイコンの切り替え、ウィジェット再描画、壁紙スケジューラ、
 * バックグラウンド保存サービスのような Android コンポーネント操作を、
 * `Context` を渡さずに呼べる形へ閉じ込める。
 */
interface AppServices {
    /** プレイリスト壁紙スケジューラの有効・無効を切り替える。 */
    fun setWallpaperPlaylistEnabled(enabled: Boolean)

    /** プライバシー状態を反映するためウィジェットを再描画する。 */
    fun refreshPrivacySensitiveWidgets()

    /**
     * 作品保存の間、フォアグラウンドサービスを維持する。
     * 返り値の `close()` で解放する。
     */
    fun acquireArtworkDownloadService(): AutoCloseable
}
