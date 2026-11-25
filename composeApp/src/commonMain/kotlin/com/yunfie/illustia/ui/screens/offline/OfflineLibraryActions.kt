package com.yunfie.illustia.ui.screens.offline

import com.yunfie.illustia.models.Illust

/** オフラインライブラリ画面が ViewModel に要求する操作。 */
interface OfflineLibraryActions {
    fun loadSavedLibrary()

    fun openIllustFromCard(illust: Illust)
}
