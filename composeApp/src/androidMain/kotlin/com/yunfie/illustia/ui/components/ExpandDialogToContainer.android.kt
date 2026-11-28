package com.yunfie.illustia.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * ダイアログのウィンドウを画面全面に広げる。
 *
 * `LocalView` / `DialogWindowProvider` は Android 専用なので、その他の
 * プラットフォームでは何もしない（`Dialog` の既定サイズで十分中央寄せできる）。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun ExpandDialogToContainer(
    widthPx: Int,
    heightPx: Int,
) {
    val view = LocalView.current
    (view.parent as? DialogWindowProvider)?.window?.setLayout(widthPx, heightPx)
}
