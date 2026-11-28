package com.yunfie.illustia.ui.components

import androidx.compose.runtime.Composable

/** 画面全面へのリサイズはプラットフォーム固有機能が無いため何もしない。 */
@Composable
actual fun ExpandDialogToContainer(
    widthPx: Int,
    heightPx: Int,
) {
}
