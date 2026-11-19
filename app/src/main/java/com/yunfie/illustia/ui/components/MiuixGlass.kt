package com.yunfie.illustia.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

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
