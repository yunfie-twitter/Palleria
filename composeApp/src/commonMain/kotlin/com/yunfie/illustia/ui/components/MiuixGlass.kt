package com.yunfie.illustia.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * アプリ共通のダイアログ。
 *
 * 以前は miuix-glass の GlassDialog を切り替えていたが、miuix-glass が miuix-ui を
 * 0.9.5-beta01 へ引き上げ、miuix-preference 0.9.3 との間で `OverlayDialog` の
 * シグネチャ不整合（NoSuchMethodError）を起こすため撤去した。依存を全モジュール
 * 0.9.3 に揃えるため、ここでは常に従来の [OverlayDialog] で表示する。
 */
@Composable
fun AppDialog(
    show: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    summary: String? = null,
    backgroundColor: Color? = null,
    onDismissRequest: (() -> Unit)? = null,
    defaultWindowInsetsPadding: Boolean = true,
    content: @Composable () -> Unit,
) {
    OverlayDialog(
        show = show,
        modifier = modifier,
        title = title,
        summary = summary,
        backgroundColor = backgroundColor ?: DialogDefaults.backgroundColor(),
        onDismissRequest = onDismissRequest,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        content = content,
    )
}

/**
 * アプリ共通のボトムシート。
 *
 * miuix-glass 撤去に伴い、常に従来の [OverlayBottomSheet] で表示する。
 */
@Composable
fun AppBottomSheet(
    show: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    backgroundColor: Color? = null,
    onDismissRequest: (() -> Unit)? = null,
    insideMargin: DpSize? = null,
    defaultWindowInsetsPadding: Boolean = true,
    content: @Composable () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        modifier = modifier,
        title = title,
        startAction = startAction,
        endAction = endAction,
        backgroundColor = backgroundColor ?: BottomSheetDefaults.backgroundColor(),
        onDismissRequest = onDismissRequest,
        insideMargin = insideMargin ?: BottomSheetDefaults.insideMargin,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        content = content,
    )
}
