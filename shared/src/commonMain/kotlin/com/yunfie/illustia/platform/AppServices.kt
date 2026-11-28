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

    /** ランキングウィジェットを再描画する。 */
    suspend fun refreshRankingWidget()

    /** ライブ壁紙へ設定変更を通知する。 */
    fun notifyLiveWallpaperSettingsChanged()

    /** アカウント情報をアプリ全体へ同期する。 */
    fun reconcileAccounts(accounts: List<com.yunfie.illustia.models.StoredAccount>)

    /**
     * ブックマーク切り替えの遅延送信をワークキューへ積む。
     * @return 積めたら true。
     */
    fun enqueueBookmarkToggle(
        illustId: Long,
        bookmarked: Boolean,
        restrict: com.yunfie.illustia.models.Restrict,
        followRestrict: com.yunfie.illustia.models.Restrict,
        bookmarkUserId: Long,
        artistId: Long,
        tags: List<String>,
        bookmarkTags: List<String>,
    ): Boolean

    /** フォロー差分同期のスナップショットを保存し、ETag を温める。 */
    suspend fun recordFollowDeltaSnapshot(
        token: String,
        freshItems: List<com.yunfie.illustia.models.Illust>,
        proxyBaseUrl: String,
    )

    /**
     * アプリ自身が占めるストレージ容量を読み取る。
     *
     * プラットフォームの統計取得は数秒かかることがあるため suspend にしてある。
     */
    suspend fun readStorageUsage(): com.yunfie.illustia.data.AppStorageUsage

    /**
     * 作品保存の間、フォアグラウンドサービスを維持する。
     * 戻り値は `close()` で解放する。
     */
    fun acquireArtworkDownloadService(): AutoCloseable
}
