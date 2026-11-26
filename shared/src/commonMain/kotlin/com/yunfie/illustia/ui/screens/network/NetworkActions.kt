package com.yunfie.illustia.ui.screens.network

import com.yunfie.illustia.ui.screens.library.LibraryActions
import com.yunfie.illustia.ui.screens.settings.SettingsActions

/**
 * ネットワーク設定画面が要求する操作の合成契約。
 * 実装は androidApp の ViewModel が担う。
 */
interface NetworkActions :
    SettingsActions,
    LibraryActions
