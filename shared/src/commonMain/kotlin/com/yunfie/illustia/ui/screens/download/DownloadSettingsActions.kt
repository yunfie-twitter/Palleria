package com.yunfie.illustia.ui.screens.download

/**
 * ダウンロード設定画面が ViewModel に要求する操作。
 */
interface DownloadSettingsActions {
    fun updateDownloadFolderByArtist(value: Boolean)

    fun updateDownloadFolderByWork(value: Boolean)

    fun updateDuplicateSaveMode(value: String)

    fun updateUgoiraSaveFormat(value: String)

    fun updateSimultaneousDownloads(value: Int)

    fun updateEmbedMetadata(enabled: Boolean)
}
