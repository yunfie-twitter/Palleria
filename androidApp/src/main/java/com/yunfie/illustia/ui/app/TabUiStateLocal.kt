package com.yunfie.illustia.ui.app

import androidx.compose.runtime.compositionLocalOf

/**
 * タブ UI の状態を Compose ツリーへ配る。
 *
 * [com.yunfie.illustia.IllustiaViewModel] から切り離した UI 専用の調整役。
 */
internal val LocalTabUiState = compositionLocalOf<TabUiState> { error("TabUiState not provided") }
