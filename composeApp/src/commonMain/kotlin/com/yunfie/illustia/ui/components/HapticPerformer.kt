package com.yunfie.illustia.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

/**
 * 共有 UI から触覚フィードバックを発火するための契約。
 * プラットフォーム固有の Vibrator 実装は androidApp 側が提供する。
 */
fun interface AppHapticPerformer {
    fun perform(effect: AppHapticEffect)
}

val LocalAppHapticPerformer = compositionLocalOf<AppHapticPerformer?> { null }

/**
 * 触覚フィードバックを発火する関数を返す。
 * 未注入でも安全に呼べるよう NoOp にフォールバックせず、呼び出し側で null 判定する。
 */
@Composable
fun rememberAppHapticPerformer(): (AppHapticEffect) -> Unit {
    val performer = LocalAppHapticPerformer.current
    return remember(performer) {
        { effect -> performer?.perform(effect) }
    }
}
