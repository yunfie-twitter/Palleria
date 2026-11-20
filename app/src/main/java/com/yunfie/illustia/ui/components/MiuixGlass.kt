package com.yunfie.illustia.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassBottomSheetDefaults
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogDefaults
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * miuix-glass（実験的グラス素材）の有効状態。
 *
 * `FeatureFlag.MiuixGlass` から解決した値をルートで提供する。個々の画面はこのローカルだけを
 * 見てグラス/通常を切り替えるので、画面側にフラグの分岐を散らさずに済む。
 */
val LocalMiuixGlassEnabled = compositionLocalOf { false }

/** グラス素材が背後をサンプリングするための backdrop。無効時は null。 */
val LocalMiuixGlassBackdrop = compositionLocalOf { null as LayerBackdrop? }

/** 実際にグラス描画が可能か（フラグ有効 かつ Android 12 以降で backdrop がある）。 */
@Composable
fun useMiuixGlass(): Boolean {
    val enabled = LocalMiuixGlassEnabled.current
    val backdrop = LocalMiuixGlassBackdrop.current
    return enabled && backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * グラス有効時に背後を記録するための backdrop を作る。
 *
 * miuix-glass は「描画済みの背後レイヤー」をサンプリングしてガラス感を出すため、
 * 画面全体を包む layer backdrop が必要になる。無効時は生成しない。
 */
@Composable
fun rememberMiuixGlassBackdrop(enabled: Boolean): LayerBackdrop? =
    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        rememberLayerBackdrop()
    } else {
        null
    }

/** backdrop を持つ要素を root で記録するためのモディファイア。無効時は素通し。 */
fun Modifier.recordMiuixGlassLayer(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) {
        this.layerBackdrop(backdrop)
    } else {
        this
    }

/**
 * アプリ共通のダイアログ。
 *
 * miuix-glass の実験的機能が有効なら [GlassDialog]（背後の面をぼかしたガラス調）で、
 * 無効なら従来の [OverlayDialog] で表示する。呼び出し側はフラグを意識しない。
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
    val glassBackdrop = LocalMiuixGlassBackdrop.current.takeIf { useMiuixGlass() }
    if (glassBackdrop != null) {
        GlassDialog(
            onDismissRequest = onDismissRequest ?: {},
            show = show,
            backdrop = glassBackdrop,
            modifier = modifier,
            title = title,
            summary = summary,
            containerColor = backgroundColor ?: GlassDialogDefaults.containerColor(),
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            content = content,
        )
    } else {
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
}

/**
 * アプリ共通のボトムシート。
 *
 * miuix-glass の実験的機能が有効なら [GlassBottomSheet] で、無効なら [OverlayBottomSheet]。
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
    val glassBackdrop = LocalMiuixGlassBackdrop.current.takeIf { useMiuixGlass() }
    if (glassBackdrop != null) {
        GlassBottomSheet(
            onDismissRequest = onDismissRequest ?: {},
            show = show,
            backdrop = glassBackdrop,
            modifier = modifier,
            title = title,
            startAction = startAction,
            endAction = endAction,
            containerColor = backgroundColor ?: GlassBottomSheetDefaults.containerColor(),
            insideMargin = insideMargin ?: BottomSheetDefaults.insideMargin,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            content = content,
        )
    } else {
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
}
