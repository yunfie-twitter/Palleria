package com.yunfie.illustia.ui.screens.discord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * ログイン画面の WebView 領域。
 *
 * バーやレイアウトは共有側が持ち、ブラウザ本体と URL 解釈だけを Android 側が供給する。
 * [reloadRequest] が増えるたびに再読み込みする。
 */
val LocalWebLoginContent = compositionLocalOf<(PaddingValues, Int) -> Unit> { { _, _ -> } }

/** WebView 本体のホスト。Android 側で実装する。 */
interface WebLoginHost {
    fun canGoBack(): Boolean

    fun goBack()
}
