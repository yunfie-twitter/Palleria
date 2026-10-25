package com.yunfie.illustia.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** クイック設定（通知シェード）がここまでアプリを覆ったら、背面の描画を止めるしきい値。 */
private const val SHADE_COVER_THRESHOLD_DP = 72f

/**
 * アプリウィンドウがクイック設定 / キーボードに覆われているか。
 *
 * クイック設定は SystemUI の別ウィンドウなので、この listener はシェードがアプリを
 * 覆い始めた時だけ呼ばれる（パネル操作中に毎フレーム呼ばれない）。覆われている間は
 * アプリはバックグラウンド扱いになるため、値は再生アニメーション
 * （[androidx.compose.runtime.getValue]）から直接読み、state 書き込み→再コンポーズ→
 * 再描画の連鎖を発生させないでフレーム取得だけ止める。
 */
@Composable
internal fun rememberSystemUiCoveredState(): State<Boolean> {
    val view = LocalView.current
    val covered = remember { mutableStateOf(false) }
    DisposableEffect(view) {
        val thresholdPx = SHADE_COVER_THRESHOLD_DP * (view.resources.displayMetrics.density)
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
            if (covered.value != next) covered.value = next
            insets
        }
        ViewCompat.requestApplyInsets(view)
        onDispose { ViewCompat.setOnApplyWindowInsetsListener(view, null) }
    }
    return covered
}
