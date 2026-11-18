package com.yunfie.illustia.ui.components

import android.app.Activity
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

/**
 * 画面ごとにばら撒かれていたステータスバー/ナビゲーションバーの明暗切り替え
 * （`WindowCompat.getInsetsController(...).isAppearanceLightStatusBars`）を 1 箇所に集約する。
 *
 * @param lightStatusBars ステータスバーのアイコンを暗色（明るい背景向け）にするか。
 * @param lightNavigationBars ナビゲーションバーのアイコンを暗色にするか。
 * @param restoreLightStatusBars 画面を離れたときに戻すステータスバーの明暗。既定は
 *   [lightStatusBars] の反転ではなく、テーマが暗色かどうかで決まる値なので呼び出し側が指定する。
 * @param restoreLightNavigationBars 画面を離れたときに戻すナビゲーションバーの明暗。
 * @param statusBarColor ステータスバーに塗る色。Transparent を渡すと透過。
 * @param navigationBarColor ナビゲーションバーに塗る色。Transparent を渡すと透過。
 *   全画面（リーダー/ビューア）から戻った直後に OS が不透明色で塗り直すことがあるため、
 *   画面側でも明示的に透明を再適用できるようにしている。
 */
@Composable
internal fun SystemBarAppearanceEffect(
    lightStatusBars: Boolean,
    lightNavigationBars: Boolean,
    restoreLightStatusBars: Boolean,
    restoreLightNavigationBars: Boolean = restoreLightStatusBars,
    statusBarColor: Int? = null,
    navigationBarColor: Int? = null,
) {
    val window = (LocalContext.current as? Activity)?.window ?: return
    DisposableEffect(
        window,
        lightStatusBars,
        lightNavigationBars,
        restoreLightStatusBars,
        restoreLightNavigationBars,
        statusBarColor,
        navigationBarColor,
    ) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (statusBarColor != null) window.statusBarColor = statusBarColor
        @Suppress("DEPRECATION")
        if (navigationBarColor != null) window.navigationBarColor = navigationBarColor
        controller.isAppearanceLightStatusBars = lightStatusBars
        controller.isAppearanceLightNavigationBars = lightNavigationBars
        onDispose {
            val restored = WindowCompat.getInsetsController(window, window.decorView)
            restored.isAppearanceLightStatusBars = restoreLightStatusBars
            restored.isAppearanceLightNavigationBars = restoreLightNavigationBars
        }
    }
}

/** 透過ステータスバー色。`Color.TRANSPARENT` を各画面で参照しないための定数。 */
internal val TransparentStatusBarColor: Int = Color.TRANSPARENT

/** 透過ナビゲーションバー色。`Color.TRANSPARENT` を各画面で参照しないための定数。 */
internal val TransparentNavigationBarColor: Int = Color.TRANSPARENT
