package com.yunfie.illustia.ui.screens.library

/**
 * ライブラリ系画面が ViewModel に要求する操作のうち、共有 UI から使える最小契約。
 */
interface LibraryActions {
    fun clearFinishedDownloads()

    fun closeSavedIllustViewer()

    fun openInternalProxySettings()
}
