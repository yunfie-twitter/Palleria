package com.yunfie.illustia.ui.components

import android.os.Build
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** クイック設定（通知シェード）がここまでアプリを覆ったら、背面の描画を止めるしきい値。 */
private const val SHADE_COVER_THRESHOLD_DP = 72f

/**
 * アプリウィンドウが SystemUI / キーボードに覆われているか。
 *
 * 以前は各コンポーザブルが同じ View に insets listener を登録していたため、後から
 * 登録したものが前のものを上書きし、ブラーやうごイラの停止判定が画面によって効かなかった。
 * 更新は [ProvideSystemUiCoverage] の 1 箇所に集約し、各画面はこの値だけを読む。
 */
internal val LocalSystemUiCovered = staticCompositionLocalOf<Boolean> { false }

/**
 * coverage state を画面全体で 1 つだけ作り、[LocalSystemUiCovered] として配る。
 *
 * クイック設定・通知シェード・電源メニューはアクティビティを pause させないため、
 * insets の変化だけでなくウィンドウフォーカスの喪失も「覆われている」とみなす。
 * 覆われている間はブラーの再録画/合成やうごイラのデコードを止める。
 */
@Composable
internal fun ProvideSystemUiCoverage(content: @Composable () -> Unit) {
    val covered by rememberSystemUiCoveredState()
    CompositionLocalProvider(LocalSystemUiCovered provides covered) {
        content()
    }
}

@Composable
private fun rememberSystemUiCoveredState(): State<Boolean> {
    val view = LocalView.current
    var hasWindowFocus by remember(view) { mutableStateOf(view.hasWindowFocus()) }
    var insetsCovered by remember(view) { mutableStateOf(false) }

    DisposableEffect(view) {
        val thresholdPx = SHADE_COVER_THRESHOLD_DP * view.resources.displayMetrics.density
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val shadeTop =
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        insets
                            .getInsets(
                                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                            ).top
                    }

                    else -> {
                        insets.systemWindowInsetTop
                    }
                }
            val next = imeVisible || shadeTop >= thresholdPx
            if (insetsCovered != next) insetsCovered = next
            insets
        }
        val focusObserver =
            ViewTreeObserver.OnWindowFocusChangeListener { focused ->
                if (hasWindowFocus != focused) hasWindowFocus = focused
            }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusObserver)
        ViewCompat.requestApplyInsets(view)
        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
            view.viewTreeObserver.removeOnWindowFocusChangeListener(focusObserver)
        }
    }

    return remember(view) {
        derivedStateOf { !hasWindowFocus || insetsCovered }
    }
}
