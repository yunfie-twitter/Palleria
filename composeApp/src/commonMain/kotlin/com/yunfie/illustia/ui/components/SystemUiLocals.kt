package com.yunfie.illustia.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * アプリウィンドウが SystemUI / キーボードに覆われているか。
 * 端末側の insets / フォーカス監視が値を供給する。
 */
val LocalSystemUiCovered = compositionLocalOf<Boolean> { false }

/**
 * 表示中のタブページが「スワイプの落ち着いた先」かどうか。
 * まだ表に出ていない隣ページではブラー再録画/合成を止めるために使う。
 */
val LocalTabPageActive = compositionLocalOf<() -> Boolean> { { true } }

/** 端末がハードウェアぼかしをサポートするか。 */
val LocalHardwareBlurSupported = compositionLocalOf<Boolean> { false }
