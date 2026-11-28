package com.yunfie.illustia.ui.components

import androidx.compose.runtime.Composable

/**
 * ダイアログのウィンドウを画面全面に広げるためのプラットフォームフック。
 * Android だけ `LocalView` 経由で本当にリサイズし、他は何もしない。
 */
@Composable
expect fun ExpandDialogToContainer(
    widthPx: Int,
    heightPx: Int,
)
