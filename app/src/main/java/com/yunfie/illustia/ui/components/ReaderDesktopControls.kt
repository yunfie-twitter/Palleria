package com.yunfie.illustia.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Build
import android.view.Window
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * リーダー/ビューアの全画面表示を切り替える。
 *
 * 解除時は「入る前の状態に戻す」のではなく、必ず両方のバーを表示する。
 * 入る前の状態を復元すると、全画面に入った時点で既にバーが隠れていた場合
 * （復帰後すぐ再入場した、ホームからの遷移中だった など）に抜けた後もバーが
 * 隠れたままになり、ナビゲーションバーの領域だけ背景色が残る。
 * バーを隠したままにすると insets も 0 になるため、透明ナビゲーションの
 * 余白確保もうまくいかない。
 *
 * また、バーを出し直したときに OS がナビゲーションバーを不透明色で塗り直す
 * ことがあるため、復帰時は透明色・コントラスト強制無効を再適用する。
 *
 * 復帰時に書き戻すバーの明暗は、デバイスのナイトモードではなく [isDarkTheme]
 * （アプリ設定から解決したテーマ）を使う。デバイス設定とアプリテーマが食い違う
 * 場合、デバイス値で上書きすると復帰後のアイコンが誤ったコントラストになる。
 */
@Composable
internal fun ReaderFullscreen(
    enabled: Boolean,
    isDarkTheme: Boolean,
    navigationBarColor: Int = Color.TRANSPARENT,
) {
    val view = LocalView.current
    // 復帰書き込みで常に最新のアプリテーマを使う。
    val currentIsDarkTheme by rememberUpdatedState(isDarkTheme)
    val currentNavigationBarColor by rememberUpdatedState(navigationBarColor)
    DisposableEffect(view, enabled) {
        val activity = view.context.readerActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val bars = WindowInsetsCompat.Type.systemBars()
        if (enabled) {
            controller?.hide(bars)
        } else {
            controller?.show(bars)
            // バーを出し直すと OS がナビゲーションバーを不透明色で塗り直すため、
            // 解除したこの時点でアプリのナビゲーションバー色を再適用する。onDispose だけだと
            // 画面に留まったまま全画面を抜けたときにバーが不透明のまま残る。
            if (window != null) {
                TransparentSystemBars.applySystemBars(window, currentIsDarkTheme, currentNavigationBarColor)
            }
        }
        onDispose {
            controller?.show(bars)
            if (window != null) {
                // 全画面を抜けた後は、ビューアの黒背景ではなくアプリのナビゲーションバー色に
                // 戻す。透明のままにすると、window 背景が無い（setBackgroundDrawable(null)）
                // ため全画面時の黒サーフェスがバー領域に透けて見える。
                TransparentSystemBars.applySystemBars(window, currentIsDarkTheme, currentNavigationBarColor)
            }
        }
    }
}

/** 全画面表示から戻ったあと、システムバーを整えるための共通処理。 */
internal object TransparentSystemBars {
    @Suppress("DEPRECATION")
    fun applySystemBars(
        window: Window,
        isDark: Boolean,
        navigationBarColor: Int,
    ) {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = navigationBarColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // ナビゲーションバーに色を戻すときはコントラスト強制を無効にしておく。
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }
}

internal fun paragraphTarget(
    indices: List<Int>,
    current: Int,
    direction: Int,
): Int? = if (direction > 0) indices.firstOrNull { it > current } else indices.lastOrNull { it < current }

@Composable
internal fun ParagraphNavigation(
    state: LazyListState,
    request: Int,
    active: () -> Boolean,
    paragraphIndices: List<Int>,
) {
    var previousRequest by remember { mutableIntStateOf(request) }
    // active を値ではなく provider で受け取り、LaunchedEffect のキーに含めない。
    // スワイプ中に settledPage が変わるたびに effect が張り直されるのを防ぐ。
    val isActive by rememberUpdatedState(active)
    LaunchedEffect(request) {
        val direction = request.compareTo(previousRequest)
        previousRequest = request
        if (!isActive() || direction == 0) return@LaunchedEffect
        val target = paragraphTarget(paragraphIndices, state.firstVisibleItemIndex, direction) ?: return@LaunchedEffect
        state.animateScrollToItem(target)
    }
}

private tailrec fun Context.readerActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.readerActivity()
        else -> null
    }
