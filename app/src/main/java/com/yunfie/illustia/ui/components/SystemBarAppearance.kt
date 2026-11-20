package com.yunfie.illustia.ui.components

import android.app.Activity
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
 * @param restoreStatusBarColor 画面を離れたときに戻すステータスバーの色。[statusBarColor] を
 *   一時的に上書きした画面（作品ページの透過など）が、離脱後に別画面へ色を持ち越さないため、
 *   呼び出し側がテーマに沿った色を指定する。null なら色は触らない。
 * @param restoreNavigationBarColor 画面を離れたときに戻すナビゲーションバーの色。
 */
@Composable
internal fun SystemBarAppearanceEffect(
    lightStatusBars: Boolean,
    lightNavigationBars: Boolean,
    restoreLightStatusBars: Boolean,
    restoreLightNavigationBars: Boolean = restoreLightStatusBars,
    statusBarColor: Int? = null,
    navigationBarColor: Int? = null,
    restoreStatusBarColor: Int? = null,
    restoreNavigationBarColor: Int? = null,
) {
    val window = (LocalContext.current as? Activity)?.window ?: return
    DisposableEffect(window) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        onDispose {
            val restored = WindowCompat.getInsetsController(window, window.decorView)
            restored.isAppearanceLightStatusBars = restoreLightStatusBars
            restored.isAppearanceLightNavigationBars = restoreLightNavigationBars
            // 透過を適用した画面（作品ページ等）から離れたら、ナビゲーションバーの色を
            // テーマ色へ戻す。戻さないと全画面から戻った直後と同じ「透明のまま」状態が
            // 他画面に残り、バー領域だけ背景色が欠けて見える。
            if (restoreStatusBarColor != null) window.statusBarColor = restoreStatusBarColor
            @Suppress("DEPRECATION")
            if (restoreNavigationBarColor != null) window.navigationBarColor = restoreNavigationBarColor
        }
    }
    // 色と明暗は DisposableEffect の key にしない。作品ページは lightStatusBars が
    // スクロール位置で切り替わるため、key に含めるとスクロールのたびに onDispose が
    // 走って window のバー色を書き戻し続け、バー領域が点滅する（#647）。
    // 値の変化は DisposableEffect を作り直さずに反映する。
    val currentLightStatusBars by rememberUpdatedState(lightStatusBars)
    val currentLightNavigationBars by rememberUpdatedState(lightNavigationBars)
    SideEffect {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (statusBarColor != null) window.statusBarColor = statusBarColor
        @Suppress("DEPRECATION")
        if (navigationBarColor != null) window.navigationBarColor = navigationBarColor
        controller.isAppearanceLightStatusBars = currentLightStatusBars
        controller.isAppearanceLightNavigationBars = currentLightNavigationBars
    }
}

/** 透過ステータスバー色。`Color.TRANSPARENT` を各画面で参照しないための定数。 */
internal val TransparentStatusBarColor: Int = Color.TRANSPARENT

/** 透過ナビゲーションバー色。`Color.TRANSPARENT` を各画面で参照しないための定数。 */
internal val TransparentNavigationBarColor: Int = Color.TRANSPARENT
