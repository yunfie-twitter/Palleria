package com.yunfie.illustia.ui.screens.library

import com.yunfie.illustia.models.Illust

/**
 * ライブラリ系画面が ViewModel に要求する操作のうち、共有 UI から使える最小契約。
 */
interface LibraryActions {
    fun clearFinishedDownloads()

    fun closeSavedIllustViewer()

    fun openInternalProxySettings()

    fun loadSavedLibrary()

    fun openIllust(illust: Illust)

    fun removeAccount(index: Int)

    fun switchAccount(index: Int)
}
