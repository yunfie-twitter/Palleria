package com.yunfie.illustia.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
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
 */
@Composable
internal fun ReaderFullscreen(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val activity = view.context.readerActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val bars = WindowInsetsCompat.Type.systemBars()
        if (enabled) controller?.hide(bars) else controller?.show(bars)
        onDispose {
            controller?.show(bars)
            if (window != null) {
                val isDark =
                    (view.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
                TransparentSystemBars.reapplyTransparent(window, isDark)
            }
        }
    }
}

/** 全画面表示から戻ったあと、システムバーを透明のまま保つための共通処理。 */
internal object TransparentSystemBars {
    @Suppress("DEPRECATION")
    fun reapplyTransparent(
        window: Window,
        isDark: Boolean,
    ) {
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
