package com.yunfie.illustia.ui.screens

import com.yunfie.illustia.settings.AppSettings

/**
 * 壁紙プレイリスト設定画面が ViewModel とプラットフォームに要求する操作。
 * フォルダ選択・ライブ壁紙のプレビュー起動は SAF / WallpaperManager 依存のため注入する。
 */
interface WallpaperPlaylistActions {
    val settings: AppSettings

    /** ライブ壁紙をサポートする端末か。 */
    fun isLiveWallpaperSupported(): Boolean

    fun updateWallpaperPlaylistEnabled(enabled: Boolean)

    fun updateLiveWallpaperSource(value: String)

    fun updateLiveWallpaperSourceFolder(folder: String)

    fun updateLiveWallpaperChangeMode(value: String)

    fun updateLiveWallpaperIntervalMinutes(minutes: Int)

    fun updateLiveWallpaperOrder(value: String)

    fun updateLiveWallpaperScaleMode(value: String)

    fun updateLiveWallpaperBackground(value: String)

    fun updateLiveWallpaperCrossfade(enabled: Boolean)

    fun updateLiveWallpaperExcludeSensitive(enabled: Boolean)

    /** 保存先フォルダの表示名。未設定なら空文字。 */
    fun folderLabel(folder: String): String

    /** フォルダ選択を開く。選択結果は [updateLiveWallpaperSourceFolder] 経由で戻る。 */
    fun pickSourceFolder()

    /** フォルダ選択失敗などを画面に通知する。 */
    fun showMessage(message: String)

    /** ライブ壁紙のプレビュー画面を開く。 */
    fun openLiveWallpaperPreview()
}
